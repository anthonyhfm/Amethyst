package dev.anthonyhfm.amethyst.devices.audio.effects

import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreAudioEffectsTest {
    private val configuration = AudioConfiguration(48_000, 2, 4_096)

    @Test
    fun eqFlatResponseIsTransparentAndBandKillsRamp() {
        val input = stereoSine(4_096, 120.0)
        val flat = EqThreeChainDevice().apply { prepareAudio(configuration) }
        val flatOutput = process(flat, input)
        assertTrue(flatOutput.indices.all { abs(flatOutput[it] - input[it]) < 0.0002f })

        val killed = EqThreeChainDevice().apply {
            state.value = EqThreeChainDeviceState(lowKilled = true)
            prepareAudio(configuration)
        }
        val killedOutput = process(killed, input)
        assertTrue(rms(killedOutput.copyOfRange(4_096, killedOutput.size)) < rms(flatOutput.copyOfRange(4_096, flatOutput.size)) * 0.55f)
        assertTrue(killedOutput.all(Float::isFinite))
    }

    @Test
    fun everyFilterModeAndExtremeRemainsFinite() {
        FilterType.entries.forEach { type ->
            listOf(20f, 20_000f).forEach { cutoff ->
                val device = FilterChainDevice().apply {
                    state.value = FilterChainDeviceState(
                        type = type,
                        cutoffHz = cutoff,
                        resonance = 12f,
                        slope = FilterSlope.Db24,
                        driveDb = 24f,
                    )
                    prepareAudio(configuration)
                }
                val impulse = FloatArray(8_192).also { it[0] = 1f; it[1] = 1f }
                assertTrue(process(device, impulse).all(Float::isFinite), "$type at $cutoff Hz")
            }
        }
    }

    @Test
    fun lowPassAttenuatesHighFrequencyAndTwentyFourDbIsSteeper() {
        val high = stereoSine(4_096, 8_000.0)
        fun filtered(slope: FilterSlope): FloatArray = FilterChainDevice().run {
            state.value = FilterChainDeviceState(cutoffHz = 800f, resonance = 0.7071f, slope = slope)
            prepareAudio(configuration)
            process(this, high)
        }
        val twelve = rms(filtered(FilterSlope.Db12).copyOfRange(2_048, 8_192))
        val twentyFour = rms(filtered(FilterSlope.Db24).copyOfRange(2_048, 8_192))
        assertTrue(twelve < rms(high) * 0.1f)
        assertTrue(twentyFour < twelve * 0.3f)
    }

    @Test
    fun dryFilterBypassAndInstancesHaveIndependentState() {
        val dryDevice = FilterChainDevice().apply {
            state.value = FilterChainDeviceState(dryWet = 0f, cutoffHz = 40f)
            prepareAudio(configuration)
        }
        val signal = stereoSine(512, 1_000.0)
        assertEquals(signal.toList(), process(dryDevice, signal).toList())

        val first = FilterChainDevice().apply {
            state.value = FilterChainDeviceState(cutoffHz = 40f)
            prepareAudio(configuration)
        }
        val second = FilterChainDevice().apply {
            state.value = FilterChainDeviceState(cutoffHz = 40f)
            prepareAudio(configuration)
        }
        process(first, FloatArray(256).also { it[0] = 1f; it[1] = 1f })
        val silence = FloatArray(256)
        val firstTail = process(first, silence)
        val secondTail = process(second, silence)
        assertTrue(firstTail.any { abs(it) > 0.000001f })
        assertTrue(secondTail.all { it == 0f })
    }

    private fun process(device: dev.anthonyhfm.amethyst.devices.AudioChainDevice<*>, input: FloatArray): FloatArray {
        val output = input.copyOf()
        val frames = output.size / 2
        val block = AudioProcessingBlock(output, 2, frames).apply { configure(frames, 0) }
        device.processAudio(block, AudioRenderContext(configuration.sampleRate, 0))
        return output
    }

    private fun stereoSine(frames: Int, frequency: Double): FloatArray = FloatArray(frames * 2) { index ->
        sin(2.0 * PI * frequency * (index / 2) / configuration.sampleRate).toFloat() * 0.5f
    }

    private fun rms(samples: FloatArray): Float =
        sqrt(samples.fold(0.0) { sum, value -> sum + value * value } / samples.size).toFloat()
}
