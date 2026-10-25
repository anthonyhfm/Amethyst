package dev.anthonyhfm.amethyst.core.engine.audio.source

import kotlin.test.Test
import kotlin.test.assertEquals

class BufferedPcmAudioSourceTest {
    @Test
    fun decodedWindowsMatchAllPcmFormatsAcrossBoundariesAndSeeks() {
        for (channels in 1..2) {
            for (bitDepth in listOf(8, 16, 24, 32)) {
                val bytes = ByteArray(size = 20_000 * channels * (bitDepth / 8)) { (it * 37).toByte() }
                verifySamples(bytes = bytes, channels = channels, bitDepth = bitDepth)
            }
            val floats = listOf(0f, -1f, 0.25f, 2f, Float.NaN, Float.POSITIVE_INFINITY)
            val bytes = ByteArray(size = 20_000 * channels * 4) { index ->
                val bits = floats[(index / 4) % floats.size].toRawBits()
                (bits ushr ((index % 4) * 8)).toByte()
            }
            verifySamples(bytes = bytes, channels = channels, bitDepth = 32, floatingPoint = true)
        }
    }

    @Test
    fun resamplingProducesIdenticalFramesAcrossWindowsAndClipBoundaries() {
        val source = ByteArrayPcmAudioSource(
            id = "resample",
            sampleRate = 44_100,
            channels = 2,
            bitDepth = 24,
            rawData = ByteArray(size = 20_000 * 6) { (it * 37).toByte() },
        )
        for (outputRate in listOf(16_000, 48_000, 96_000)) {
            val direct = PolyphaseSincResampler(
                sourceRate = source.sampleRate,
                outputRate = outputRate,
                channels = 2,
            )
            val buffered = PolyphaseSincResampler(
                sourceRate = source.sampleRate,
                outputRate = outputRate,
                channels = 2,
            )
            val input = BufferedPcmAudioSource(source = source)
            val expected = FloatArray(size = 2)
            val actual = FloatArray(size = 2)
            direct.reset(sourceFrame = 17.0)
            buffered.reset(sourceFrame = 17.0)
            while (direct.sourcePosition < source.frameCount - 17) {
                val hasExpected = direct.readFrame(
                    source = source,
                    destination = expected,
                    lowerBoundFrame = 17,
                    upperBoundFrameExclusive = source.frameCount - 17,
                )
                val hasActual = buffered.readFrame(
                    source = input,
                    destination = actual,
                    lowerBoundFrame = 17,
                    upperBoundFrameExclusive = source.frameCount - 17,
                )
                assertEquals(expected = hasExpected, actual = hasActual)
                for (channel in 0..1) {
                    assertEquals(expected = expected[channel], actual = actual[channel])
                }
                direct.advance()
                buffered.advance()
            }
        }
    }

    @Test
    fun bulkPreparationMatchesTheOriginalFilterForAllIntegerFormats() {
        for (channels in 1..2) {
            for (bitDepth in listOf(8, 16, 24, 32)) {
                val source = ByteArrayPcmAudioSource(
                    id = "bulk-$channels-$bitDepth",
                    sampleRate = 44100,
                    channels = channels,
                    bitDepth = bitDepth,
                    rawData = ByteArray(size = 20_000 * channels * (bitDepth / 8)) { (it * 37).toByte() },
                )
                for (outputRate in listOf(16000, 44100, 48000, 96000)) {
                    val direct = PolyphaseSincResampler(
                        sourceRate = 44100,
                        outputRate = outputRate,
                        channels = channels,
                    )
                    val bulk = PolyphaseSincResampler(
                        sourceRate = 44100,
                        outputRate = outputRate,
                        channels = channels,
                    )
                    val buffered = BufferedPcmAudioSource(source = source)
                    val expected = FloatArray(size = channels)
                    val actual = FloatArray(size = channels)
                    while (direct.sourcePosition < source.frameCount) {
                        assertEquals(
                            expected = direct.readFrame(source = source, destination = expected),
                            actual = bulk.readBufferedFrame(source = buffered, destination = actual),
                        )
                        for (channel in 0 until channels) {
                            assertEquals(expected = expected[channel], actual = actual[channel])
                        }
                        direct.advance()
                        bulk.advance()
                    }
                    for (position in listOf(0.25, 8191.75, 16383.5, 19999.75)) {
                        direct.reset(sourceFrame = position)
                        bulk.reset(sourceFrame = position)
                        direct.readFrame(source = source, destination = expected)
                        bulk.readBufferedFrame(source = buffered, destination = actual)
                        for (channel in 0 until channels) {
                            assertEquals(expected = expected[channel], actual = actual[channel])
                        }
                    }
                }
            }
        }
    }

    private fun verifySamples(
        bytes: ByteArray,
        channels: Int,
        bitDepth: Int,
        floatingPoint: Boolean = false,
    ) {
        val source = ByteArrayPcmAudioSource(
            id = "pcm-$bitDepth-$channels",
            sampleRate = 44_100,
            channels = channels,
            bitDepth = bitDepth,
            rawData = bytes,
            floatingPoint = floatingPoint,
        )
        val buffered = BufferedPcmAudioSource(source = source)
        val frameIndices = (-1L..source.frameCount) + listOf(8191L, 8192L, 16383L, 16384L, 1L, 19_999L, 100L)
        for (frame in frameIndices) {
            for (channel in -1..channels) {
                assertEquals(
                    expected = source.sample(frameIndex = frame, channel = channel),
                    actual = buffered.sample(frameIndex = frame, channel = channel),
                )
            }
        }
    }
}
