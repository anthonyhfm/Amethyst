package dev.anthonyhfm.amethyst.core.engine.audio.graph

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import kotlinx.atomicfu.atomic

/** Immutable, allocation-free audio graph compiled from an editable [Chain]. */
class AudioExecutionPlan private constructor(
    private val root: SerialAudioNode,
    val devices: Array<AudioChainDevice<*>>,
    val latencyFrames: Int,
    val tailFrames: Long,
    val diagnostics: List<AudioGraphDiagnostic>,
    val metrics: AudioRenderMetrics,
) {
    fun process(block: AudioProcessingBlock, context: AudioRenderContext) {
        root.process(block, context)
    }

    fun reset() {
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
                devices = compiler.devices.toTypedArray(),
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
    fun process(block: AudioProcessingBlock, context: AudioRenderContext)
}

private class SerialAudioNode(
    private val nodes: Array<AudioPlanNode>,
) : AudioPlanNode {
    override val latencyFrames: Int = nodes.sumOf(AudioPlanNode::latencyFrames)
    override val tailFrames: Long = nodes.sumOf(AudioPlanNode::tailFrames)

    override fun process(block: AudioProcessingBlock, context: AudioRenderContext) {
        var index = 0
        while (index < nodes.size) {
            nodes[index].process(block, context)
            index++
        }
    }
}

private class DeviceAudioNode(
    private val device: AudioChainDevice<*>,
    private val enabled: Boolean,
    private val metrics: AudioRenderMetrics,
) : AudioPlanNode {
    override val latencyFrames: Int = device.latencyFrames
    override val tailFrames: Long = device.tailFrames

    override fun process(block: AudioProcessingBlock, context: AudioRenderContext) {
        if (enabled) {
            device.processAudio(block, context)
            sanitizeFinite(block, metrics)
        }
    }
}

private class ParallelAudioNode(
    private val branches: Array<AudioBranch>,
) : AudioPlanNode {
    override val latencyFrames: Int = branches.maxOfOrNull { it.node.latencyFrames } ?: 0
    override val tailFrames: Long = branches.maxOfOrNull { it.node.tailFrames } ?: 0L

    override fun process(block: AudioProcessingBlock, context: AudioRenderContext) {
        var branchIndex = 0
        while (branchIndex < branches.size) {
            val branch = branches[branchIndex]
            branch.block.configure(block.frameCount, block.frameOffset)
            branch.block.clear()
            branch.node.process(branch.block, context)
            sumInto(branch.block, block)
            branchIndex++
        }
    }
}

private data class AudioBranch(
    val node: SerialAudioNode,
    val block: AudioProcessingBlock,
)

private class AudioGraphCompiler(
    private val configuration: AudioConfiguration,
    private val metrics: AudioRenderMetrics,
) {
    val devices = mutableListOf<AudioChainDevice<*>>()
    val diagnostics = mutableListOf<AudioGraphDiagnostic>()

    fun compileSerial(editableDevices: List<GenericChainDevice<*>>): SerialAudioNode {
        val nodes = mutableListOf<AudioPlanNode>()
        editableDevices.forEach { device ->
            when (device) {
                is AudioChainDevice<*> -> {
                    if (devices.none { it === device }) {
                        devices += device
                    }
                    nodes += DeviceAudioNode(device, enabled = !device.isMuted, metrics = metrics)
                }

                is NestedChainDevice -> {
                    val branches = device.audioNestedChains().map { child ->
                        AudioBranch(
                            node = compileSerial(child.devices.value),
                            block = AudioProcessingBlock(
                                samples = FloatArray(
                                    configuration.maximumBlockFrames * configuration.channels,
                                ),
                                channels = configuration.channels,
                                maximumFrames = configuration.maximumBlockFrames,
                            ),
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
