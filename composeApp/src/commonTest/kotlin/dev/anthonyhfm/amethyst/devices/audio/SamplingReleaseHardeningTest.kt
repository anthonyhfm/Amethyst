package dev.anthonyhfm.amethyst.devices.audio

import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.audio.graph.AudioRenderMetrics
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.effects.AudioDelayChainDevice
import dev.anthonyhfm.amethyst.devices.audio.effects.AudioDelayChainDeviceState
import dev.anthonyhfm.amethyst.devices.audio.effects.AudioDelayNoteValue
import dev.anthonyhfm.amethyst.devices.audio.effects.AudioDelayTimeMode
import dev.anthonyhfm.amethyst.devices.audio.effects.FilterChainDevice
import dev.anthonyhfm.amethyst.devices.audio.effects.FilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.audio.effects.ReverbChainDevice
import dev.anthonyhfm.amethyst.devices.audio.effects.ReverbChainDeviceState
import dev.anthonyhfm.amethyst.devices.audio.effects.SaturatorChainDevice
import dev.anthonyhfm.amethyst.devices.audio.effects.SaturatorChainDeviceState
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SamplingReleaseHardeningTest {
    private val configuration = AudioConfiguration(2_000, 2, 64, 64)

    @Test
    fun offlineRenderIsDeterministicWithStableRmsAndPeak() {
        fun render(): FloatArray {
            val chain = AudioChain().apply {
                add(FilterChainDevice().apply {
                    state.value = FilterChainDeviceState(cutoffHz = 420f, resonance = 0.9f, dryWet = 0.8f)
                }, fromUser = false)
                add(SaturatorChainDevice().apply {
                    state.value = SaturatorChainDeviceState(driveDb = 12f, outputDb = -3f, dryWet = 0.7f)
                }, fromUser = false)
                prepareAudio(configuration)
            }
            val samples = FloatArray(128) { index ->
                sin(2.0 * PI * 180.0 * (index / 2) / configuration.sampleRate).toFloat() * 0.6f
            }
            val block = AudioProcessingBlock(samples, 2, 64).apply { configure(64, 0) }
            chain.processAudio(block, AudioRenderContext(configuration.sampleRate, 0))
            return samples
        }

        val first = render()
        val second = render()
        assertTrue(first.indices.all { abs(first[it] - second[it]) <= 0.000001f })
        assertTrue(rms(first) in 0.2f..0.8f)
        assertTrue(first.maxOf { abs(it) } <= 1.1f)
    }

    @Test
    fun graphSanitizesAndCountsNonFiniteDeviceOutput() {
        val chain = AudioChain().apply {
            add(NonFiniteDevice(), fromUser = false)
            prepareAudio(configuration)
        }
        val block = AudioProcessingBlock(FloatArray(128) { 1f }, 2, 64).apply { configure(64, 0) }
        chain.processAudio(block, AudioRenderContext(configuration.sampleRate, 0))

        assertTrue(block.samples.all { it == 0f })
        assertEquals(128L, chain.diagnosticsSnapshot().render.sanitizedSamples)
        assertEquals(1L, chain.diagnosticsSnapshot().render.renderedBlocks)
    }

    @Test
    fun renderBudgetOverrunsAndDspLoadAreMeasured() {
        val metrics = AudioRenderMetrics()
        metrics.recordRender(frameCount = 100, elapsedNanos = 150_000_000, sampleRate = 1_000)

        val snapshot = metrics.snapshot()
        assertEquals(1L, snapshot.renderOverruns)
        assertEquals(150f, snapshot.lastDspLoadPercent, 0.01f)
        assertEquals(150f, snapshot.peakDspLoadPercent, 0.01f)
    }

    @Test
    fun rapidRetriggerMultipleTimeEffectsAndBpmChangesStayBounded() {
        val previousBpm = WorkspaceRepository.bpm.value
        val sample = loadedSample()
        val chain = AudioChain().apply {
            add(sample, fromUser = false)
            repeat(4) {
                add(AudioDelayChainDevice().apply {
                    state.value = AudioDelayChainDeviceState(
                        timeMode = AudioDelayTimeMode.Sync,
                        noteValue = AudioDelayNoteValue.Sixteenth,
                        feedback = 0.8f,
                    )
                }, fromUser = false)
                add(ReverbChainDevice().apply {
                    state.value = ReverbChainDeviceState(decay = 0.75f, dryWet = 0.25f)
                }, fromUser = false)
            }
            prepareAudio(configuration)
        }
        try {
            repeat(100) { index ->
                sample.signalEnter(listOf(Signal.Midi("stress-${index % 3}", index % 8, index / 8, 127)))
            }
            val block = AudioProcessingBlock(FloatArray(128), 2, 64)
            repeat(80) { blockIndex ->
                if (blockIndex == 20) WorkspaceRepository.setBpm(90.0, undoable = false)
                if (blockIndex == 50) WorkspaceRepository.setBpm(150.0, undoable = false)
                block.configure(64, blockIndex * 64L)
                block.clear()
                chain.processAudio(block, AudioRenderContext(configuration.sampleRate, blockIndex * 64L))
                assertTrue(block.samples.all(Float::isFinite))
            }
            val diagnostics = chain.diagnosticsSnapshot()
            assertTrue(diagnostics.activeVoices <= 16)
            assertTrue(diagnostics.commandQueueDrops > 0)
            assertTrue(diagnostics.render.renderedBlocks >= 80)
        } finally {
            WorkspaceRepository.setBpm(previousBpm, undoable = false)
            chain.releaseAudio()
        }
    }

    private fun loadedSample(): SampleChainDevice = SampleChainDevice().apply {
        state.value = SampleChainDeviceState(
            fileName = "stress.raw",
            rawData = pcm16Mono(512),
            sampleRate = configuration.sampleRate,
            channels = 1,
            bitDepth = 16,
            totalDurationMs = 256,
            isLoaded = true,
        )
    }

    private fun rms(samples: FloatArray): Float =
        sqrt(samples.fold(0.0) { sum, value -> sum + value * value } / samples.size).toFloat()
}

@Serializable
private class NonFiniteState : DeviceState()

private class NonFiniteDevice : AudioChainDevice<NonFiniteState>() {
    override val state = MutableStateFlow(NonFiniteState())

    @Composable
    override fun Content() = Unit

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        var index = 0
        while (index < block.frameCount * block.channels) {
            block.samples[index] = Float.NaN
            index++
        }
    }
}

private fun pcm16Mono(frameCount: Int): ByteArray = ByteArray(frameCount * 2).also { bytes ->
    var frame = 0
    while (frame < frameCount) {
        val sample = (sin(2.0 * PI * 100.0 * frame / 2_000.0) * 16_000).toInt()
        bytes[frame * 2] = (sample and 0xff).toByte()
        bytes[frame * 2 + 1] = ((sample ushr 8) and 0xff).toByte()
        frame++
    }
}
