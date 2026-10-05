package dev.anthonyhfm.amethyst.core.engine.audio.source

/**
 * Immutable random-access PCM used by real-time voices.
 *
 * Implementations must not allocate or synchronize from [sample]. Samples are
 * normalized Float32 values; out-of-range reads return silence so a resampler
 * can safely evaluate its filter at source boundaries.
 */
interface AudioSource {
    val id: String
    val sampleRate: Int
    val channels: Int
    val frameCount: Long

    fun sample(frameIndex: Long, channel: Int): Float
}

/**
 * Compact in-memory source for decoded interleaved Float32 PCM.
 */
class Float32AudioSource(
    override val id: String,
    override val sampleRate: Int,
    override val channels: Int,
    val samples: FloatArray,
) : AudioSource {
    init {
        require(id.isNotBlank()) { "Audio source id must not be blank" }
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(channels in 1..2) { "Only mono and stereo sources are supported" }
        require(samples.size % channels == 0) {
            "Interleaved sample count must be divisible by channel count"
        }
    }

    override val frameCount: Long = samples.size.toLong() / channels

    override fun sample(frameIndex: Long, channel: Int): Float {
        if (frameIndex !in 0 until frameCount || channel !in 0 until channels) {
            return 0f
        }
        return samples[(frameIndex * channels + channel).toInt()]
    }
}

/**
 * Canonical compact PCM source backed by the decoded bytes themselves.
 *
 * Integer PCM is little-endian. 8-bit PCM is unsigned, matching WAV. Float32
 * preserves finite values without clamping so headroom remains available to
 * the processing chain.
 */
open class PcmAudioSource internal constructor(
    override val id: String,
    override val sampleRate: Int,
    override val channels: Int,
    val bitDepth: Int,
    internal val pcmBytes: PcmByteStorage,
    val floatingPoint: Boolean = false,
) : AudioSource {
    val pcmByteCount: Int get() = pcmBytes.size
    internal val pcmCacheIdentity: Any get() = (pcmBytes as? InMemoryPcmBytes)?.bytes ?: pcmBytes
    internal val residentPcmBytes: Int get() = if (pcmBytes is InMemoryPcmBytes) {
        pcmBytes.size
    } else {
        0
    }
    val bytesPerSample: Int = bitDepth / 8
    val bytesPerFrame: Int = bytesPerSample * channels

    init {
        require(id.isNotBlank()) { "Audio source id must not be blank" }
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(channels in 1..2) { "Only mono and stereo sources are supported" }
        require(bitDepth in SUPPORTED_BIT_DEPTHS) {
            "Supported PCM bit depths are 8, 16, 24, and 32"
        }
        require(!floatingPoint || bitDepth == 32) { "Only Float32 PCM is supported" }
        require(pcmBytes.size % bytesPerFrame == 0) {
            "PCM byte count must contain complete interleaved frames"
        }
    }

    override val frameCount: Long = pcmBytes.size.toLong() / bytesPerFrame

    override fun sample(frameIndex: Long, channel: Int): Float {
        if (frameIndex !in 0 until frameCount || channel !in 0 until channels) {
            return 0f
        }
        val offset = (frameIndex * bytesPerFrame + channel * bytesPerSample).toInt()
        return when {
            floatingPoint -> {
                val value = Float.fromBits(readInt32LittleEndian(offset))
                if (value.isFinite()) value else 0f
            }

            bitDepth == 8 -> ((pcmBytes[offset].toInt() and 0xff) - 128) / 128f
            bitDepth == 16 -> {
                val value = (pcmBytes[offset].toInt() and 0xff) or
                    (pcmBytes[offset + 1].toInt() shl 8)
                value.toShort() / 32768f
            }

            bitDepth == 24 -> {
                var value = (pcmBytes[offset].toInt() and 0xff) or
                    ((pcmBytes[offset + 1].toInt() and 0xff) shl 8) or
                    ((pcmBytes[offset + 2].toInt() and 0xff) shl 16)
                if (value and 0x800000 != 0) {
                    value = value or -0x1000000
                }
                value / 8388608f
            }

            else -> readInt32LittleEndian(offset) / 2147483648.0f
        }
    }

    private fun readInt32LittleEndian(offset: Int): Int =
        (pcmBytes[offset].toInt() and 0xff) or
            ((pcmBytes[offset + 1].toInt() and 0xff) shl 8) or
            ((pcmBytes[offset + 2].toInt() and 0xff) shl 16) or
            (pcmBytes[offset + 3].toInt() shl 24)

    companion object {
        val SUPPORTED_BIT_DEPTHS = setOf(8, 16, 24, 32)
    }
}

class ByteArrayPcmAudioSource(
    id: String,
    sampleRate: Int,
    channels: Int,
    bitDepth: Int,
    val rawData: ByteArray,
    floatingPoint: Boolean = false,
) : PcmAudioSource(
    id = id,
    sampleRate = sampleRate,
    channels = channels,
    bitDepth = bitDepth,
    pcmBytes = InMemoryPcmBytes(bytes = rawData),
    floatingPoint = floatingPoint,
) {
    companion object {
        val SUPPORTED_BIT_DEPTHS = PcmAudioSource.SUPPORTED_BIT_DEPTHS
    }
}

internal fun pcmSourceFromStorage(
    id: String,
    sampleRate: Int,
    channels: Int,
    bitDepth: Int,
    storage: PcmByteStorage,
): PcmAudioSource = if (storage is InMemoryPcmBytes) {
    ByteArrayPcmAudioSource(
        id = id,
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth,
        rawData = storage.bytes,
    )
} else {
    PcmAudioSource(
        id = id,
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth,
        pcmBytes = storage,
    )
}
