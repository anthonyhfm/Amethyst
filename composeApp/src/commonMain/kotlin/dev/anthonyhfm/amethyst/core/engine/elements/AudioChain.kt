package dev.anthonyhfm.amethyst.core.engine.elements

import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioChainDeviceRole
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import kotlinx.atomicfu.atomic

/**
 * The editable sampling chain and its real-time audio execution surface.
 *
 * [devices] remains the only authoritative device graph. The real-time snapshot
 * contains references to those same device instances and is rebuilt only after
 * topology or mute changes.
 */
class AudioChain : Chain() {
    private val realtimeDevices = atomic(emptyArray<AudioChainDevice<*>>())
    private val preparedConfiguration = atomic<AudioConfiguration?>(null)
    private var observedNestedChains: List<Chain> = emptyList()
    private val retiredDevices = mutableListOf<AudioChainDevice<*>>()

    init {
        rebuildRealtimeSnapshot(devices.value)
    }

    override fun onDevicesChanged(
        previous: List<GenericChainDevice<*>>,
        current: List<GenericChainDevice<*>>,
    ) {
        rebuildRealtimeSnapshot(current)
    }

    fun prepareAudio(configuration: AudioConfiguration) {
        preparedConfiguration.value = configuration
        realtimeDevices.value.forEach { it.prepareAudio(configuration) }
    }

    fun processAudio(
        block: AudioProcessingBlock,
        context: AudioRenderContext,
    ) {
        realtimeDevices.value.forEach { device ->
            if (!device.isMuted) {
                device.processAudio(block, context)
            }
        }
    }

    fun resetAudio() {
        realtimeDevices.value.forEach(AudioChainDevice<*>::resetAudio)
    }

    fun releaseAudio() {
        realtimeDevices.value.forEach(AudioChainDevice<*>::releaseAudio)
        retiredDevices.forEach(AudioChainDevice<*>::releaseAudio)
        retiredDevices.clear()
        preparedConfiguration.value = null
    }

    private fun rebuildRealtimeSnapshot(rootDevices: List<GenericChainDevice<*>>) {
        observedNestedChains.forEach { it.topologyChangedListener = null }
        val observed = mutableListOf<Chain>()
        val snapshot = mutableListOf<AudioChainDevice<*>>()
        rootDevices.forEach { device ->
            when {
                device is AudioChainDevice<*> -> snapshot += device
                device is NestedChainDevice -> {
                    observeNestedTopology(device, observed)
                    collectNestedGenerators(device, snapshot)
                }
            }
        }
        observedNestedChains = observed
        val previous = realtimeDevices.value
        val configuration = preparedConfiguration.value
        if (configuration != null) {
            retiredDevices.removeAll { retired -> snapshot.any { it === retired } }
            snapshot.forEach { next ->
                if (previous.none { it === next }) next.prepareAudio(configuration)
            }
            previous.forEach { old ->
                if (snapshot.none { it === old } && retiredDevices.none { it === old }) {
                    // A render call may still hold the old immutable snapshot.
                    // Release retired devices only after the render thread stops.
                    retiredDevices += old
                }
            }
        }
        realtimeDevices.value = snapshot.toTypedArray()
    }

    private fun observeNestedTopology(
        container: NestedChainDevice,
        observed: MutableList<Chain>,
    ) {
        container.nestedChains().forEach { nested ->
            observed += nested
            nested.topologyChangedListener = {
                rebuildRealtimeSnapshot(devices.value)
            }
            nested.devices.value.filterIsInstance<NestedChainDevice>().forEach {
                observeNestedTopology(it, observed)
            }
        }
    }

    private fun collectNestedGenerators(
        container: NestedChainDevice,
        destination: MutableList<AudioChainDevice<*>>,
    ) {
        container.nestedChains().forEach { nested ->
            nested.devices.value.forEach { device ->
                when {
                    device is AudioChainDevice<*> &&
                        device.audioRole == AudioChainDeviceRole.Generator -> destination += device
                    device is NestedChainDevice -> collectNestedGenerators(device, destination)
                }
            }
        }
    }
}
