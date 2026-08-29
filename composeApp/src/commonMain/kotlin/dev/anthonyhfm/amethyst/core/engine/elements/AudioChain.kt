package dev.anthonyhfm.amethyst.core.engine.elements

import dev.anthonyhfm.amethyst.core.engine.audio.graph.AudioExecutionPlan
import dev.anthonyhfm.amethyst.core.engine.audio.graph.AudioRenderMetrics
import dev.anthonyhfm.amethyst.core.engine.audio.graph.AudioRenderMetricSnapshot
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntime
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntimeAware
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.ChokeSourceRegistration
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.ChokeVoiceSource
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.LiveAutomationSource
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.SidechainTriggerRegistration
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.SidechainTriggerSink
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.devices.audio.effects.DuckerChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import kotlinx.atomicfu.atomic
import kotlin.time.TimeSource

/** Editable sampling graph backed by an atomically swapped immutable execution plan. */
class AudioChain : Chain() {
    private val executionPlan = atomic<AudioExecutionPlan?>(null)
    private val preparedConfiguration = atomic<AudioConfiguration?>(null)
    private val activeRenderReaders = atomic(0)
    private val triggerRuntime = AudioTriggerRuntime()
    private val renderMetrics = AudioRenderMetrics()
    private var observedNestedChains: List<Chain> = emptyList()
    private val retiredPlans = mutableListOf<AudioExecutionPlan>()

    val latencyFrames: Int get() = executionPlan.value?.latencyFrames ?: 0
    val tailFrames: Long get() = executionPlan.value?.tailFrames ?: 0L
    val diagnostics get() = executionPlan.value?.diagnostics.orEmpty()

    fun diagnosticsSnapshot(): AudioDiagnosticsSnapshot {
        val planDevices = executionPlan.value?.devices.orEmpty()
        var activeVoices = 0
        var voiceDrops = 0L
        var commandDrops = 0L
        planDevices.forEach { device ->
            when (device) {
                is SampleChainDevice -> {
                    activeVoices += device.activeVoiceCount
                    voiceDrops += device.voiceStealCount
                    commandDrops += device.commandQueueDropCount
                }
                is DuckerChainDevice -> commandDrops += device.droppedTriggerCount
            }
        }
        return AudioDiagnosticsSnapshot(
            activeVoices = activeVoices,
            voiceDrops = voiceDrops,
            commandQueueDrops = commandDrops,
            graphLatencyFrames = latencyFrames,
            graphTailFrames = tailFrames,
            graphDiagnostics = diagnostics.map { it.message },
            render = renderMetrics.snapshot(),
        )
    }

    override fun onDevicesChanged(
        previous: List<GenericChainDevice<*>>,
        current: List<GenericChainDevice<*>>,
    ) {
        observeNestedTopology(current)
        rebuildExecutionPlan()
    }

    fun prepareAudio(configuration: AudioConfiguration) {
        preparedConfiguration.value = configuration
        triggerRuntime.publishSampleRate(configuration.sampleRate)
        observeNestedTopology(devices.value)
        rebuildExecutionPlan()
    }

    fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        triggerRuntime.publishFrame(context.absoluteFrame)
        val started = TimeSource.Monotonic.markNow()
        activeRenderReaders.incrementAndGet()
        try {
            // One atomic read pins a single immutable topology for the whole block.
            executionPlan.value?.process(block, context)
        } finally {
            activeRenderReaders.decrementAndGet()
            renderMetrics.recordRender(
                frameCount = block.frameCount,
                elapsedNanos = started.elapsedNow().inWholeNanoseconds,
                sampleRate = context.sampleRate,
            )
        }
    }

    fun resetAudio() {
        executionPlan.value?.reset()
    }

    fun releaseAudio() {
        executionPlan.value?.release()
        retiredPlans.flatMap { it.devices.asList() }
            .distinctBy { it }
            .filter { retired -> executionPlan.value?.devices?.none { it === retired } != false }
            .forEach { it.releaseAudio() }
        retiredPlans.clear()
        executionPlan.value = null
        preparedConfiguration.value = null
        observedNestedChains.forEach { it.topologyChangedListener = null }
        observedNestedChains = emptyList()
    }

    private fun rebuildExecutionPlan() {
        val configuration = preparedConfiguration.value ?: return
        val previous = executionPlan.value
        val next = AudioExecutionPlan.compile(this, configuration, renderMetrics)
        next.devices.filterIsInstance<AudioTriggerRuntimeAware>().forEach {
            it.audioTriggerRuntime = triggerRuntime
        }
        triggerRuntime.replaceSources(
            next.devices.mapNotNull { device ->
                val source = device as? ChokeVoiceSource ?: return@mapNotNull null
                ChokeSourceRegistration(source)
            }.toTypedArray(),
        )
        triggerRuntime.replaceAutomationSources(
            next.devices.filterIsInstance<LiveAutomationSource>().toTypedArray(),
        )
        triggerRuntime.replaceSidechainSinks(
            next.devices.mapIndexedNotNull { index, device ->
                val sink = device as? SidechainTriggerSink ?: return@mapIndexedNotNull null
                val allowed = next.devices.asSequence().take(index)
                    .filterIsInstance<ChokeVoiceSource>()
                    .map(ChokeVoiceSource::persistentSourceId)
                    .filter { it == sink.sidechainSourceId }
                    .toSet()
                SidechainTriggerRegistration(sink, allowed)
            }.toTypedArray(),
        )
        next.devices.forEach { device ->
            if (previous?.devices?.none { it === device } != false) {
                device.prepareAudio(configuration)
            }
        }
        executionPlan.getAndSet(next)?.let(retiredPlans::add)
        reclaimRetiredPlans(next)
    }

    private fun reclaimRetiredPlans(current: AudioExecutionPlan) {
        if (activeRenderReaders.value != 0 || retiredPlans.isEmpty()) return
        retiredPlans.flatMap { it.devices.asList() }
            .distinctBy { it }
            .filter { retired -> current.devices.none { it === retired } }
            .forEach { it.releaseAudio() }
        retiredPlans.clear()
    }

    private fun observeNestedTopology(rootDevices: List<GenericChainDevice<*>>) {
        observedNestedChains.forEach { it.topologyChangedListener = null }
        val observed = mutableListOf<Chain>()

        fun observe(device: NestedChainDevice) {
            device.nestedChains().forEach { nested ->
                observed += nested
                nested.topologyChangedListener = ::rebuildExecutionPlan
                nested.devices.value.filterIsInstance<NestedChainDevice>().forEach(::observe)
            }
        }

        rootDevices.filterIsInstance<NestedChainDevice>().forEach(::observe)
        observedNestedChains = observed
    }
}

data class AudioDiagnosticsSnapshot(
    val activeVoices: Int,
    val voiceDrops: Long,
    val commandQueueDrops: Long,
    val graphLatencyFrames: Int,
    val graphTailFrames: Long,
    val graphDiagnostics: List<String>,
    val render: AudioRenderMetricSnapshot,
)
