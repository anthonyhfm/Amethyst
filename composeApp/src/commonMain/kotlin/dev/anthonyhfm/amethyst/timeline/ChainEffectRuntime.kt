package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.mainDispatcherOrDefault
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.devices.TimelineTriggerable
import dev.anthonyhfm.amethyst.devices.serialDuration
import dev.anthonyhfm.amethyst.devices.timelineDuration
import dev.anthonyhfm.amethyst.timeline.data.ChainEffectEntry
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Runtime counterpart of one persisted Chain Effect clip. */
class ChainEffectRuntime(
    entry: ChainEffectEntry,
    private val bpmProvider: () -> Double,
    private val onStateOrDurationChanged: (ChainEffectRuntime) -> Unit,
) {
    private data class OutputKey(val x: Int, val y: Int, val layer: Int)

    var entry: ChainEffectEntry = entry
        private set

    var source: GenericChainDevice<*>? = entry.source?.let(DeviceRegistry::unpack)
        private set

    val processors: Chain = entry.processors.unpack()
    private val scope = CoroutineScope(mainDispatcherOrDefault("ChainEffectRuntime") + SupervisorJob())
    private val stateJobs = mutableListOf<Job>()
    private val activeOutputs = mutableMapOf<OutputKey, Signal.LED>()

    init {
        source?.collaborationSyncEnabled = false
        configurePrivateChain(processors)
        wirePrivateChain()
        observeRuntimeState()
    }

    val isPlayable: Boolean get() = source is TimelineTriggerable && source?.isMuted == false

    fun naturalDuration(): TimelineDuration {
        val workspaceSize = WorkspaceRepository.bounds.second
        val context = TimelineDurationContext(
            bpm = bpmProvider(),
            canvasWidth = workspaceSize.width.coerceAtLeast(1),
            canvasHeight = workspaceSize.height.coerceAtLeast(1),
        )
        val sourceDuration = source
            ?.takeUnless(GenericChainDevice<*>::isMuted)
            ?.timelineDuration(context)
            ?: TimelineDuration.None
        return listOf(
            sourceDuration,
            processors.timelineDuration(context),
        ).serialDuration()
    }

    fun snapshot(
        durationMs: Long = entry.durationMs,
        maxDurationMs: Long? = entry.maxDurationMs,
        startTimeMs: Long = entry.startTimeMs,
    ): ChainEffectEntry = entry.copy(
        startTimeMs = startTimeMs,
        durationMs = durationMs,
        source = source?.let(DeviceRegistry::pack),
        processors = StateChain.pack(processors),
        maxDurationMs = maxDurationMs,
    )

    fun replaceSource(device: GenericChainDevice<*>?) {
        require(device == null || device is TimelineTriggerable) {
            "The first Chain Effect device must implement TimelineTriggerable"
        }
        stop()
        source?.onRemovedFromChain()
        source = device
        source?.collaborationSyncEnabled = false
        wirePrivateChain()
        observeRuntimeState()
        notifyChanged()
    }

    fun addProcessor(device: GenericChainDevice<*>, atIndex: Int? = null) {
        require(device !is dev.anthonyhfm.amethyst.devices.AudioChainDevice<*>) {
            "Audio chain devices are not supported in timeline Chain Effects"
        }
        device.collaborationSyncEnabled = false
        processors.add(device, atIndex = atIndex, fromUser = false)
        configurePrivateChain(processors)
        observeRuntimeState()
        notifyChanged()
    }

    fun removeProcessor(index: Int) {
        processors.remove(index, fromUser = false)
        observeRuntimeState()
        notifyChanged()
    }

    fun moveProcessor(fromIndex: Int, toIndex: Int) {
        val current = processors.devices.value.toMutableList()
        val device = current.getOrNull(fromIndex) ?: return
        processors.remove(fromIndex, fromUser = false)
        processors.add(device, atIndex = toIndex.coerceIn(0, processors.devices.value.size), fromUser = false)
        observeRuntimeState()
        notifyChanged()
    }

    fun start() {
        stop()
        (source as? TimelineTriggerable)?.startTimelineTrigger()
    }

    fun stop() {
        (source as? TimelineTriggerable)?.stopTimelineTrigger()
        source?.let(::cancelDeviceRecursively)
        processors.devices.value.forEach(::cancelDeviceRecursively)
        clearActiveOutputs()
    }

    fun dispose() {
        stop()
        stateJobs.forEach(Job::cancel)
        stateJobs.clear()
        scope.cancel()
    }

    internal fun updateEntryMetadata(updated: ChainEffectEntry) {
        entry = updated
    }

    private fun wirePrivateChain() {
        source?.signalExit = processors::signalEnter
        processors.signalExit = ::emitToHeaven
        processors.reroute()
        configurePrivateChain(processors)
    }

    private fun observeRuntimeState() {
        stateJobs.forEach(Job::cancel)
        stateJobs.clear()
        (listOfNotNull(source) + allDevices(processors)).forEach { device ->
            stateJobs += scope.launch {
                device.state.collect {
                    notifyChanged()
                }
            }
        }
    }

    private fun notifyChanged() = onStateOrDurationChanged(this)

    private fun emitToHeaven(signals: List<Signal>) {
        val ledSignals = signals.filterIsInstance<Signal.LED>()
        ledSignals.forEach { signal ->
            val key = OutputKey(signal.x, signal.y, signal.layer)
            if (signal.color == Color.Black) activeOutputs.remove(key) else activeOutputs[key] = signal
        }
        if (ledSignals.isNotEmpty()) Heaven.midiEnter(ledSignals)
    }

    private fun clearActiveOutputs() {
        if (activeOutputs.isEmpty()) return
        Heaven.midiEnter(activeOutputs.values.map { it.copy(color = Color.Black) })
        activeOutputs.clear()
    }

    private fun cancelDeviceRecursively(device: GenericChainDevice<*>) {
        Heaven.cancelJobsForOwner(device)
        (device as? Chokeable)?.onChoke()
        if (device is NestedChainDevice) {
            device.nestedChains().forEach { nested ->
                nested.devices.value.forEach(::cancelDeviceRecursively)
            }
        }
    }

    private fun allDevices(chain: Chain): List<GenericChainDevice<*>> = buildList {
        chain.devices.value.forEach { device ->
            add(device)
            if (device is NestedChainDevice) {
                device.nestedChains().forEach { addAll(allDevices(it)) }
            }
        }
    }

    private fun configurePrivateChain(chain: Chain) {
        chain.collaborationSyncEnabled = false
        chain.topologyChangedListener = {
            configurePrivateChain(processors)
            observeRuntimeState()
            notifyChanged()
        }
        chain.devices.value.forEach { device ->
            device.collaborationSyncEnabled = false
            if (device is NestedChainDevice) {
                device.nestedChains().forEach(::configurePrivateChain)
            }
        }
    }
}

data class ResolvedChainEffectLength(
    val durationMs: Long,
    val maxDurationMs: Long?,
    val naturalDuration: TimelineDuration,
)

fun resolveChainEffectLength(
    naturalDuration: TimelineDuration,
    existingCapMs: Long?,
    nextClipStartMs: Long?,
    clipStartMs: Long,
    bpm: Double,
    hasSource: Boolean,
): ResolvedChainEffectLength {
    val beatMs = (60_000.0 / bpm.coerceAtLeast(1.0)).toLong().coerceAtLeast(1L)
    val barMs = beatMs * 4L
    val availableMs = nextClipStartMs?.minus(clipStartMs)?.coerceAtLeast(1L)

    return when (naturalDuration) {
        TimelineDuration.None -> {
            val duration = minOf(existingCapMs ?: barMs, availableMs ?: Long.MAX_VALUE)
            ResolvedChainEffectLength(duration, existingCapMs, naturalDuration)
        }

        is TimelineDuration.Finite -> {
            val collisionCap = availableMs?.takeIf { it < naturalDuration.milliseconds }
            val cap = listOfNotNull(existingCapMs, collisionCap).minOrNull()
            val duration = minOf(naturalDuration.milliseconds.coerceAtLeast(1L), cap ?: Long.MAX_VALUE)
            ResolvedChainEffectLength(duration, cap, naturalDuration)
        }

        TimelineDuration.Unbounded -> {
            val cap = listOfNotNull(existingCapMs, availableMs).minOrNull() ?: barMs
            ResolvedChainEffectLength(cap.coerceAtLeast(1L), cap.coerceAtLeast(1L), naturalDuration)
        }
    }
}
