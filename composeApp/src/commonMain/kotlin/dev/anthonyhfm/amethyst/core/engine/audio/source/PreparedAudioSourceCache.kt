package dev.anthonyhfm.amethyst.core.engine.audio.source

import kotlinx.atomicfu.atomic

/**
 * Small process-local cache of PCM sources prepared at the active hardware rate.
 *
 * Preparation is a control-thread operation. Returned sources are immutable and
 * can be read directly by the realtime renderer without sample-rate conversion.
 */
object PreparedAudioSourceCache {
    private data class Key(
        val id: String,
        val sourceRate: Int,
        val outputRate: Int,
        val channels: Int,
        val frameCount: Long,
    )

    private val entries = atomic<Map<Key, AudioSource>>(emptyMap())

    fun getOrPrepare(source: AudioSource, outputRate: Int): AudioSource {
        require(outputRate > 0)
        if (source.sampleRate == outputRate) return source
        val key = Key(
            id = source.id,
            sourceRate = source.sampleRate,
            outputRate = outputRate,
            channels = source.channels,
            frameCount = source.frameCount,
        )
        entries.value[key]?.let { return it }

        val prepared = resampleToPcm24(source, outputRate)
        while (true) {
            val current = entries.value
            current[key]?.let { return it }
            val trimmed = if (current.size >= MAXIMUM_ENTRIES) {
                current.entries.drop(current.size - MAXIMUM_ENTRIES + 1)
                    .associate { it.toPair() }
            } else {
                current
            }
            if (
                entries.compareAndSet(
                    current,
                    trimmed + (key to prepared),
                )
            ) {
                return prepared
            }
        }
    }

    fun clear() {
        entries.value = emptyMap()
    }

    private fun resampleToPcm24(source: AudioSource, outputRate: Int): AudioSource {
        val outputFrames = (
            source.frameCount.toDouble() * outputRate / source.sampleRate
            ).toLong().coerceAtLeast(1L)
        require(outputFrames <= Int.MAX_VALUE / (source.channels * BYTES_PER_PCM24_SAMPLE)) {
            "Prepared audio source is too large"
        }
        val output = ByteArray(
            outputFrames.toInt() * source.channels * BYTES_PER_PCM24_SAMPLE,
        )
        val frame = FloatArray(source.channels)
        val resampler = PolyphaseSincResampler(
            sourceRate = source.sampleRate,
            outputRate = outputRate,
            channels = source.channels,
        )
        var outputFrame = 0
        while (outputFrame < outputFrames.toInt()) {
            resampler.readFrame(source, frame)
            var channel = 0
            while (channel < source.channels) {
                writePcm24(
                    destination = output,
                    sampleIndex = outputFrame * source.channels + channel,
                    sample = frame[channel],
                )
                channel++
            }
            resampler.advance()
            outputFrame++
        }
        return ByteArrayPcmAudioSource(
            id = source.id,
            sampleRate = outputRate,
            channels = source.channels,
            bitDepth = 24,
            rawData = output,
        )
    }

    private fun writePcm24(
        destination: ByteArray,
        sampleIndex: Int,
        sample: Float,
    ) {
        val normalized = sample.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
        val value = if (normalized <= -1f) {
            -8_388_608
        } else {
            (normalized * 8_388_607f).toInt()
        }
        val offset = sampleIndex * BYTES_PER_PCM24_SAMPLE
        destination[offset] = (value and 0xff).toByte()
        destination[offset + 1] = ((value ushr 8) and 0xff).toByte()
        destination[offset + 2] = ((value ushr 16) and 0xff).toByte()
    }

    private const val MAXIMUM_ENTRIES = 64
    private const val BYTES_PER_PCM24_SAMPLE = 3
}
