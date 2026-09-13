package dev.anthonyhfm.amethyst.core.engine.elements

import dev.anthonyhfm.amethyst.core.engine.audio.graph.AudioExecutionPlan
import dev.anthonyhfm.amethyst.core.engine.audio.graph.AudioRenderMetrics
import dev.anthonyhfm.amethyst.core.engine.audio.graph.AudioRenderMetricSnapshot
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerBatch
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntime
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntimeAware
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.ChokeSourceRegistration
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.ChokeVoiceSource
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.LiveAutomationSource
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioChainDeviceRole
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.devices.SidechainAudioConsumer
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.devicesDepthFirst
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationTarget
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
    val currentAudioFrame: Long get() = triggerRuntime.currentFrame
    val audioSampleRate: Int get() = triggerRuntime.sampleRate

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

    override fun signalEnter(n: List<Signal>) {
        dispatchSignals(n, requestedTargetFrame = null)
    }

    fun signalEnterAtFrame(n: List<Signal>, targetFrame: Long) {
        require(targetFrame >= 0L)
        dispatchSignals(n, requestedTargetFrame = targetFrame)
    }

    private fun dispatchSignals(n: List<Signal>, requestedTargetFrame: Long?) {
        if (preparedConfiguration.value == null || n.none { it is Signal.Midi }) {
            super.signalEnter(n)
            return
        }
        val batch = AudioTriggerBatch(requestedTargetFrame)
        try {
            super.signalEnter(n.map { signal ->
                if (signal is Signal.Midi) signal.copy(audioTriggerBatch = batch) else signal
            })
        } finally {
            triggerRuntime.commitBatch(batch)
        }
    }

    override fun onDevicesChanged(
        previous: List<GenericChainDevice<*>>,
        current: List<GenericChainDevice<*>>,
    ) {
        observeNestedTopology(current)
        rebuildExecutionPlan()
    }

    fun prepareAudio(configuration: AudioConfiguration) {
        triggerRuntime.clearBatches()
        preparedConfiguration.value = configuration
        triggerRuntime.publishSampleRate(configuration.sampleRate)
        observeNestedTopology(devices.value)
        rebuildExecutionPlan()
    }

    fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        triggerRuntime.beginRenderBlock(context.absoluteFrame)
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
        triggerRuntime.clearBatches()
        executionPlan.value?.reset()
        triggerRuntime.clearAutomationOverrides()
    }

    fun automationValue(
        target: LiveAutomationTarget,
        frame: Long = triggerRuntime.currentFrame,
    ): Float? = triggerRuntime.automationValue(target, frame)

    fun clearAutomation(target: LiveAutomationTarget) {
        triggerRuntime.clearAutomationTarget(target)
        // The UI can edit a macro before audio preparation has registered the
        // current topology with the runtime. Clear those sources directly too,
        // otherwise a stale latched value can keep masking the edited macro.
        devicesDepthFirst()
            .filterIsInstance<LiveAutomationSource>()
            .filter { it.target == target }
            .forEach(LiveAutomationSource::clearAutomationOverride)
    }

    fun releaseAudio() {
        triggerRuntime.clearBatches()
        triggerRuntime.clearAutomationOverrides()
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
        val runtimeDevices = devicesDepthFirst()
        runtimeDevices.filterIsInstance<AudioTriggerRuntimeAware>().forEach {
            it.audioTriggerRuntime = triggerRuntime
        }
        runtimeDevices.filterIsInstance<AudioChainDevice<*>>().forEach { device ->
            if (previous?.devices?.none { it === device } != false) {
                device.prepareAudio(configuration)
            }
        }
        val next = AudioExecutionPlan.compile(this, configuration, renderMetrics)
        triggerRuntime.replaceSources(
            next.enabledChokeSources.map(::ChokeSourceRegistration).toTypedArray(),
        )
        triggerRuntime.replaceAutomationSources(
            runtimeDevices.filterIsInstance<LiveAutomationSource>()
                .filter(LiveAutomationSource::participatesInAudioAutomation)
                .toTypedArray(),
        )
        val allSourceIds = next.devices.asSequence()
            .filter { it.audioRole == AudioChainDeviceRole.Generator }
            .map { it.selectionUUID }
            .toSet()
        next.devices.filterIsInstance<SidechainAudioConsumer>().forEach { consumer ->
            consumer.replaceEligibleSidechainSources(
                allSourceIds - programSourceIdsBefore(consumer as GenericChainDevice<*>),
            )
        }
        executionPlan.getAndSet(next)?.let(retiredPlans::add)
        reclaimRetiredPlans(next)
    }

    /** Sources already mixed into [target]'s input cannot be external sidechains. */
    private fun programSourceIdsBefore(target: GenericChainDevice<*>): Set<String> {
        data class SearchResult(val found: Boolean, val outputSources: Set<String>)

        fun search(chain: Chain): SearchResult {
            val upstream = linkedSetOf<String>()
            chain.devices.value.forEach { device ->
                if (device === target) return SearchResult(true, upstream)
                if (device is AudioChainDevice<*> && device.audioRole == AudioChainDeviceRole.Generator) {
                    upstream += device.selectionUUID
                }
                if (device is NestedChainDevice) {
                    val nestedOutputs = linkedSetOf<String>()
                    device.audioNestedChains().forEach { nested ->
                        val result = search(nested)
                        if (result.found) return result
                        nestedOutputs += result.outputSources
                    }
                    upstream += nestedOutputs
                }
            }
            return SearchResult(false, upstream)
        }

        return search(this).takeIf(SearchResult::found)?.outputSources.orEmpty()
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
