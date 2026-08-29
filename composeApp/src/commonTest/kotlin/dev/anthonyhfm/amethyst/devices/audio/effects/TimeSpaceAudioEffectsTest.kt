package dev.anthonyhfm.amethyst.devices.audio.effects

import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeSpaceAudioEffectsTest {
    private val configuration = AudioConfiguration(1_000, 2, 64, 2_048)

    @Test
    fun delayProducesPhaseStableEchoAtRequestedTime() {
        val device = AudioDelayChainDevice().apply {
            state.value = AudioDelayChainDeviceState(timeMs = 10f, feedback = 0f, dryWet = 1f)
            prepareAudio(configuration)
        }
        val impulse = FloatArray(80).also { it[0] = 1f; it[1] = 1f }
        val output = process(device, impulse)

        assertTrue(output.take(20).all { it == 0f })
        assertEquals(1f, output[20], 0.0001f)
        assertEquals(1f, output[21], 0.0001f)
        assertTrue(output.all(Float::isFinite))
    }

    @Test
    fun pingPongAlternatesFeedbackChannelsAndSyncMathIsStable() {
        val state = AudioDelayChainDeviceState(
            timeMode = AudioDelayTimeMode.Sync,
            noteValue = AudioDelayNoteValue.Eighth,
            timeMs = 5f,
            feedback = 0.5f,
            dryWet = 1f,
            stereoMode = AudioDelayStereoMode.PingPong,
            filterHz = 450f,
        )
        assertEquals(250f, state.resolvedTimeMs(120.0), 0.0001f)
        assertEquals(250f, state.resolvedTimeMs(120.0), 0.0001f)

        val freeState = state.copy(timeMode = AudioDelayTimeMode.Milliseconds)
        val device = AudioDelayChainDevice().apply {
            this.state.value = freeState
            prepareAudio(configuration)
        }
        val input = FloatArray(48).also { it[0] = 1f }
        val output = process(device, input)
        assertTrue(output[10] > 0.9f)
        assertTrue(output[21] > 0.35f)
    }

    @Test
    fun maximumFeedbackCannotProduceNonFiniteOutput() {
        val device = AudioDelayChainDevice().apply {
            state.value = AudioDelayChainDeviceState(timeMs = 1f, feedback = 1f, dryWet = 1f)
            prepareAudio(configuration)
        }
        val input = FloatArray(8_192) { if (it < 2) 100f else 0f }
        assertTrue(process(device, input).all(Float::isFinite))
    }

    @Test
    fun reverbRendersStereoTailAfterSourceEnds() {
        val device = ReverbChainDevice().apply {
            state.value = ReverbChainDeviceState(preDelayMs = 0f, size = 0.7f, decay = 0.8f, damping = 0.3f, dryWet = 1f)
            prepareAudio(configuration)
        }
        val impulse = FloatArray(2_048).also { it[0] = 1f; it[1] = 0.25f }
        val first = process(device, impulse)
        val tail = process(device, FloatArray(2_048), absoluteFrame = 1_024)

        assertTrue(first.any { abs(it) > 0.0001f })
        assertTrue(tail.any { abs(it) > 0.0001f })
        assertTrue(tail.all(Float::isFinite))
        assertTrue(device.isTailActive)
        assertTrue(device.tailFrames > configuration.sampleRate)
        assertTrue(tail.indices.step(2).any { abs(tail[it] - tail[it + 1]) > 0.00001f })
    }

    @Test
    fun zeroWetIsExactBypassForBothEffects() {
        val input = FloatArray(256) { ((it % 17) - 8) / 8f }
        val delay = AudioDelayChainDevice().apply {
            state.value = AudioDelayChainDeviceState(dryWet = 0f)
            prepareAudio(configuration)
        }
        val reverb = ReverbChainDevice().apply {
            state.value = ReverbChainDeviceState(dryWet = 0f)
            prepareAudio(configuration)
        }
        assertEquals(input.toList(), process(delay, input).toList())
        assertEquals(input.toList(), process(reverb, input).toList())
    }

    @Test
    fun effectStateAndParametersAreRegisteredForProjectRoundTrips() {
        val delayState = AudioDelayChainDeviceState(
            timeMode = AudioDelayTimeMode.Sync,
            noteValue = AudioDelayNoteValue.Half,
            stereoMode = AudioDelayStereoMode.PingPong,
        )
        val reverbState = ReverbChainDeviceState(preDelayMs = 42f, decay = 0.8f)
        assertEquals(delayState, DeviceRegistry.deepCopyState(delayState))
        assertEquals(reverbState, DeviceRegistry.deepCopyState(reverbState))
        assertEquals(setOf("timeMs", "feedback", "dryWet", "filter"), AudioDelayChainDevice.PARAMETERS.map { it.id }.toSet())
        assertEquals(setOf("preDelay", "size", "decay", "damping", "dryWet"), ReverbChainDevice.PARAMETERS.map { it.id }.toSet())
    }

    private fun process(device: AudioChainDevice<*>, input: FloatArray, absoluteFrame: Long = 0): FloatArray {
        val output = input.copyOf()
        val frames = output.size / 2
        val block = AudioProcessingBlock(output, 2, frames).apply { configure(frames, absoluteFrame) }
        device.processAudio(block, AudioRenderContext(configuration.sampleRate, absoluteFrame))
        return output
    }
}
