package dev.anthonyhfm.amethyst.core.engine.audio.graph

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioChainDeviceRole
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.AudioOutputActivity
import dev.anthonyhfm.amethyst.devices.AudioSourceRouting
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.devices.SidechainAudioProvider
import dev.anthonyhfm.amethyst.devices.EMPTY_AUDIO_PROCESSING_BLOCKS
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.ChokeVoiceSource
import kotlinx.atomicfu.atomic

/** Immutable, allocation-free audio graph compiled from an editable [Chain]. */
class AudioExecutionPlan private constructor(
    private val root: SerialAudioNode,
    private val sources: Array<RenderedAudioSource>,
    private val sidechainAudioProvider: SidechainAudioProvider,
    val devices: Array<AudioChainDevice<*>>,
    val enabledChokeSources: Array<ChokeVoiceSource>,
    val latencyFrames: Int,
    val tailFrames: Long,
    val diagnostics: List<AudioGraphDiagnostic>,
    val metrics: AudioRenderMetrics,
) {
    fun process(block: AudioProcessingBlock, context: AudioRenderContext) {
        var sourceIndex = 0
        while (sourceIndex < sources.size) {
            sources[sourceIndex].render(block.frameCount, block.frameOffset, context)
            sourceIndex++
        }
        context.sidechainAudioProvider = sidechainAudioProvider
        root.process(block, context)
    }

    fun reset() {
        root.resetRoutingState()
        devices.forEach(AudioChainDevice<*>::resetAudio)
    }

    fun release() {
        devices.forEach(AudioChainDevice<*>::releaseAudio)
    }

    companion object {
        fun compile(
            chain: Chain,
            configuration: AudioConfiguration,
            metrics: AudioRenderMetrics = AudioRenderMetrics(),
        ): AudioExecutionPlan {
            val compiler = AudioGraphCompiler(configuration, metrics)
            val root = compiler.compileSerial(chain.devices.value)
            return AudioExecutionPlan(
                root = root,
                sources = compiler.sources.toTypedArray(),
                sidechainAudioProvider = compiler.sidechainAudioProvider(),
                devices = compiler.devices.toTypedArray(),
                enabledChokeSources = compiler.sources.mapNotNull { source ->
                    if (source.enabled) source.device as? ChokeVoiceSource else null
                }.toTypedArray(),
                latencyFrames = root.latencyFrames,
                tailFrames = root.tailFrames,
                diagnostics = compiler.diagnostics.toList(),
                metrics = metrics,
            )
        }
    }
}

data class AudioGraphDiagnostic(
    val deviceId: String,
    val message: String,
)

private interface AudioPlanNode {
    val latencyFrames: Int
    val tailFrames: Long
    val hasPotentialOutput: Boolean
    fun process(block: AudioProcessingBlock, context: AudioRenderContext)
    fun resetRoutingState() = Unit
}

private class SerialAudioNode(
    private val nodes: Array<AudioPlanNode>,
) : AudioPlanNode {
    override val latencyFrames: Int = nodes.sumOf(AudioPlanNode::latencyFrames)
    override val tailFrames: Long = nodes.sumOf(AudioPlanNode::tailFrames)
    override val hasPotentialOutput: Boolean
        get() = nodes.any { it.hasPotentialOutput }

    override fun process(block: AudioProcessingBlock, context: AudioRenderContext) {
        var index = 0
        while (index < nodes.size) {
            nodes[index].process(block, context)
            index++
        }
    }

    override fun resetRoutingState() {
        nodes.forEach(AudioPlanNode::resetRoutingState)
    }
}

private class DeviceAudioNode(
    private val device: AudioChainDevice<*>,
    private val enabled: Boolean,
    private val metrics: AudioRenderMetrics,
    private val renderedSource: RenderedAudioSource? = null,
) : AudioPlanNode {
    override val latencyFrames: Int = device.latencyFrames
    override val tailFrames: Long = device.tailFrames
    override val hasPotentialOutput: Boolean
        get() = enabled && (renderedSource?.hasMainOutput ?: true)

    override fun process(block: AudioProcessingBlock, context: AudioRenderContext) {
        if (renderedSource != null) {
            if (enabled && renderedSource.contributesToMainOutput) sumInto(renderedSource.block, block)
        } else if (enabled) {
            device.processAudio(block, context)
            sanitizeFinite(block, metrics)
        }
    }
}

/** A generator is advanced once per callback, then reused by audio and detector paths. */
private class RenderedAudioSource(
    val device: AudioChainDevice<*>,
    val enabled: Boolean,
    configuration: AudioConfiguration,
    private val metrics: AudioRenderMetrics,
) {
    val block = AudioProcessingBlock(
        samples = FloatArray(configuration.maximumBlockFrames * configuration.channels),
        channels = configuration.channels,
        maximumFrames = configuration.maximumBlockFrames,
    )
    var hasOutput: Boolean = false
        private set
    val contributesToMainOutput: Boolean
        get() = (device as? AudioSourceRouting)?.contributesToMainOutput ?: true
    val sidechainBusId: String?
        get() = (device as? AudioSourceRouting)?.sidechainBusId
    val hasMainOutput: Boolean
        get() = hasOutput && contributesToMainOutput

    fun render(frameCount: Int, frameOffset: Long, context: AudioRenderContext) {
        block.configure(frameCount, frameOffset)
        if (!enabled || (device as? AudioOutputActivity)?.mayProduceAudio == false) {
            // Sidechain consumers retain this block reference. Clear the final
            // active contents once so an idle source cannot expose stale PCM.
            if (hasOutput) block.clear()
            hasOutput = false
            return
        }
        block.clear()
        device.processAudio(block, context)
        sanitizeFinite(block, metrics)
        hasOutput = true
    }
}

private class ParallelAudioNode(
    private val branches: Array<AudioBranch>,
) : AudioPlanNode {
    override val latencyFrames: Int = branches.maxOfOrNull { it.node.latencyFrames } ?: 0
    override val tailFrames: Long = branches.maxOfOrNull { it.node.tailFrames } ?: 0L
    override val hasPotentialOutput: Boolean
        get() = branches.any(AudioBranch::hasPotentialOutput)

    override fun process(block: AudioProcessingBlock, context: AudioRenderContext) {
        var branchIndex = 0
        while (branchIndex < branches.size) {
            val branch = branches[branchIndex]
            if (branch.hasPotentialOutput) {
                branch.block.configure(block.frameCount, block.frameOffset)
                branch.block.clear()
                if (branch.node.hasPotentialOutput) {
                    branch.node.process(branch.block, context)
                }
                branch.applyLatencyCompensation()
                sumInto(branch.block, block)
            }
            branchIndex++
        }
    }


    override fun resetRoutingState() {
        branches.forEach(AudioBranch::reset)
    }
}

private class AudioBranch(
    val node: SerialAudioNode,
    val block: AudioProcessingBlock,
    private val compensationFrames: Int,
) {
    private val delay = FloatArray(compensationFrames * block.channels)
    private var delayFrameIndex = 0

    val hasPotentialOutput: Boolean
        get() = node.hasPotentialOutput || compensationFrames > 0

    fun applyLatencyCompensation() {
        if (compensationFrames <= 0) return
        var frame = 0
        while (frame < block.frameCount) {
            val blockOffset = frame * block.channels
            val delayOffset = delayFrameIndex * block.channels
            var channel = 0
            while (channel < block.channels) {
                val delayed = delay[delayOffset + channel]
                delay[delayOffset + channel] = block.samples[blockOffset + channel]
                block.samples[blockOffset + channel] = delayed
                channel++
            }
            delayFrameIndex++
            if (delayFrameIndex == compensationFrames) delayFrameIndex = 0
            frame++
        }
    }

    fun reset() {
        node.resetRoutingState()
        delay.fill(0f)
        delayFrameIndex = 0
    }
}

private class AudioGraphCompiler(
    private val configuration: AudioConfiguration,
    private val metrics: AudioRenderMetrics,
) {
    val devices = mutableListOf<AudioChainDevice<*>>()
    val sources = mutableListOf<RenderedAudioSource>()
    val diagnostics = mutableListOf<AudioGraphDiagnostic>()

    fun sidechainAudioProvider(): SidechainAudioProvider {
        val sourceBlocks = sources.associate { it.device.selectionUUID to it.block }
        val busBlocks = sources
            .mapNotNull { source -> source.sidechainBusId?.takeIf(String::isNotBlank)?.let { it to source.block } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, blocks) -> blocks.toTypedArray() }
        return object : SidechainAudioProvider {
            override fun sourceBlock(sourceId: String): AudioProcessingBlock? = sourceBlocks[sourceId]
            override fun busBlocks(busId: String): Array<AudioProcessingBlock> =
                busBlocks[busId] ?: EMPTY_AUDIO_PROCESSING_BLOCKS
        }
    }

    fun compileSerial(
        editableDevices: List<GenericChainDevice<*>>,
        ancestorEnabled: Boolean = true,
    ): SerialAudioNode {
        val nodes = mutableListOf<AudioPlanNode>()
        editableDevices.forEach { device ->
            when (device) {
                is AudioChainDevice<*> -> {
                    val enabled = ancestorEnabled && !device.isMuted
                    if (devices.none { it === device }) {
                        devices += device
                    }
                    val renderedSource = if (device.audioRole == AudioChainDeviceRole.Generator) {
                        sources.firstOrNull { it.device === device } ?: RenderedAudioSource(
                            device = device,
                            enabled = enabled,
                            configuration = configuration,
                            metrics = metrics,
                        ).also(sources::add)
                    } else null
                    nodes += DeviceAudioNode(
                        device,
                        enabled = enabled,
                        metrics = metrics,
                        renderedSource = renderedSource,
                    )
                }

                is NestedChainDevice -> {
                    val enabled = ancestorEnabled && !device.isMuted
                    val childNodes = device.audioNestedChains().map { child ->
                        compileSerial(child.devices.value, ancestorEnabled = enabled)
                    }
                    val maximumLatency = childNodes.maxOfOrNull(SerialAudioNode::latencyFrames) ?: 0
                    val branches = childNodes.map { childNode ->
                        AudioBranch(
                            node = childNode,
                            block = AudioProcessingBlock(
                                samples = FloatArray(
                                    configuration.maximumBlockFrames * configuration.channels,
                                ),
                                channels = configuration.channels,
                                maximumFrames = configuration.maximumBlockFrames,
                            ),
                            compensationFrames = maximumLatency - childNode.latencyFrames,
                        )
                    }
                    if (branches.isNotEmpty()) nodes += ParallelAudioNode(branches.toTypedArray())
                }

                else -> Unit
            }
        }
        return SerialAudioNode(nodes.toTypedArray())
    }
}

private fun sanitizeFinite(block: AudioProcessingBlock, metrics: AudioRenderMetrics) {
    val sampleCount = block.frameCount * block.channels
    var sanitized = 0L
    var index = 0
    while (index < sampleCount) {
        if (!block.samples[index].isFinite()) {
            block.samples[index] = 0f
            sanitized++
        }
        index++
    }
    if (sanitized > 0L) metrics.addSanitizedSamples(sanitized)
}

private fun sumInto(source: AudioProcessingBlock, destination: AudioProcessingBlock) {
    val sampleCount = source.frameCount * source.channels
    var index = 0
    while (index < sampleCount) {
        destination.samples[index] += source.samples[index]
        index++
    }
}

class AudioRenderMetrics {
    private val renderedBlocks = atomic(0L)
    private val renderedFrames = atomic(0L)
    private val renderOverruns = atomic(0L)
    private val sanitizedSamples = atomic(0L)
    private val lastDspLoadBasisPoints = atomic(0)
    private val peakDspLoadBasisPoints = atomic(0)

    fun recordRender(frameCount: Int, elapsedNanos: Long, sampleRate: Int) {
        renderedBlocks.incrementAndGet()
        renderedFrames.addAndGet(frameCount.toLong())
        val budgetNanos = frameCount.toDouble() * 1_000_000_000.0 / sampleRate.coerceAtLeast(1)
        val load = ((elapsedNanos / budgetNanos) * 10_000.0)
            .coerceIn(0.0, Int.MAX_VALUE.toDouble())
            .toInt()
        lastDspLoadBasisPoints.value = load
        while (true) {
            val peak = peakDspLoadBasisPoints.value
            if (load <= peak || peakDspLoadBasisPoints.compareAndSet(peak, load)) break
        }
        if (elapsedNanos > budgetNanos) renderOverruns.incrementAndGet()
    }

    fun addSanitizedSamples(count: Long) {
        sanitizedSamples.addAndGet(count.coerceAtLeast(0L))
    }

    fun snapshot(): AudioRenderMetricSnapshot = AudioRenderMetricSnapshot(
        renderedBlocks = renderedBlocks.value,
        renderedFrames = renderedFrames.value,
        renderOverruns = renderOverruns.value,
        sanitizedSamples = sanitizedSamples.value,
        lastDspLoadPercent = lastDspLoadBasisPoints.value / 100f,
        peakDspLoadPercent = peakDspLoadBasisPoints.value / 100f,
    )
}

data class AudioRenderMetricSnapshot(
    val renderedBlocks: Long,
    val renderedFrames: Long,
    val renderOverruns: Long,
    val sanitizedSamples: Long,
    val lastDspLoadPercent: Float,
    val peakDspLoadPercent: Float,
)
