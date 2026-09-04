package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.nativeengine.EchoAudioBuffer
import dev.anthonyhfm.amethyst.nativeengine.EchoEngine as NativeEchoDecoder
import io.github.vinceglb.filekit.utils.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlin.math.roundToInt

/**
 * iOS decoder that normalizes every supported format to the same signed
 * little-endian 24-bit PCM representation used by the desktop backend.
 *
 * AVFoundation handles the system formats. The shared Rust/Symphonia decoder
 * provides parity with desktop and Android for unsupported codecs/containers.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosAudioDecoder {
    val formats = EchoSupportedAudioFormats
    private val fallbackDecoder = NativeEchoDecoder()

    fun probeFile(filePath: String): AudioFileMetadata? {
        val metadata = fallbackDecoder.probeFile(filePath).metadata ?: return null
        return AudioFileMetadata(
            durationMs = metadata.durationMs.toLong(),
            sampleRate = metadata.sampleRate.toInt(),
            channels = metadata.channels.toInt(),
            totalSamples = metadata.totalSamples.toLong(),
            bitDepth = metadata.bitDepth.toInt(),
        )
    }

    fun decodeFile(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?,
    ): Signal.AudioSignal? {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        if (extension !in formats) return null
        val platformDecoded = decodeWithAvFoundation(filePath, sampleStart, sampleEnd)
        return platformDecoded ?: fallbackDecoder.decodeFile(filePath).buffer
            ?.toSignal(sampleStart, sampleEnd)
    }

    fun decodeData(
        audioData: ByteArray,
        fileName: String,
        sampleStart: Long?,
        sampleEnd: Long?,
    ): Signal.AudioSignal? {
        if (audioData.isEmpty()) return null
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in formats) return null

        val temporaryPath = buildString {
            append(NSTemporaryDirectory())
            append("amethyst-audio-")
            append(NSUUID().UUIDString)
            append('.')
            append(extension)
        }
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.createFileAtPath(temporaryPath, audioData.toNSData(), null)) {
            return null
        }
        return try {
            val platformDecoded = decodeWithAvFoundation(temporaryPath, sampleStart, sampleEnd)
            platformDecoded ?: fallbackDecoder.decodeBytes(audioData, fileName).buffer
                ?.toSignal(sampleStart, sampleEnd)
        } finally {
            fileManager.removeItemAtPath(temporaryPath, null)
        }
    }

    private fun decodeWithAvFoundation(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?,
    ): Signal.AudioSignal? = memScoped {
        val file = runCatching {
            AVAudioFile(
                forReading = NSURL.fileURLWithPath(filePath),
                commonFormat = AVAudioPCMFormatFloat32,
                interleaved = true,
                error = null,
            )
        }.getOrNull() ?: return null

        try {
            val format = file.processingFormat
            val sampleRate = format.sampleRate.roundToInt()
            val channels = format.channelCount.toInt()
            if (sampleRate <= 0 || channels !in 1..2) return null

            val range = normalizedRange(file.length, sampleStart, sampleEnd)
                ?: return null
            val requestedFrames = range.last - range.first
            val output = allocatePcm24(requestedFrames, channels) ?: return null
            if (requestedFrames == 0L) {
                return signal(output, sampleRate, channels, 0L)
            }

            file.framePosition = range.first
            val bufferFrames = minOf(DECODE_BLOCK_FRAMES.toLong(), requestedFrames).toUInt()
            val buffer = AVAudioPCMBuffer(format, bufferFrames)
            var framesRemaining = requestedFrames
            var outputIndex = 0
            var decodedFrames = 0L
            while (framesRemaining > 0L) {
                val framesToRead = minOf(bufferFrames.toLong(), framesRemaining).toUInt()
                buffer.frameLength = 0u
                if (!file.readIntoBuffer(buffer, framesToRead, null)) return null
                val frameLength = buffer.frameLength.toInt()
                if (frameLength <= 0) break

                val channelData = buffer.floatChannelData ?: return null
                val stride = buffer.stride.toInt()
                if (stride == channels) {
                    val interleaved = channelData[0] ?: return null
                    var frame = 0
                    while (frame < frameLength) {
                        var channel = 0
                        while (channel < channels) {
                            outputIndex = writePcm24(
                                output,
                                outputIndex,
                                interleaved[frame * stride + channel],
                            )
                            channel++
                        }
                        frame++
                    }
                } else {
                    var frame = 0
                    while (frame < frameLength) {
                        var channel = 0
                        while (channel < channels) {
                            val samples = channelData[channel] ?: return null
                            outputIndex = writePcm24(output, outputIndex, samples[frame * stride])
                            channel++
                        }
                        frame++
                    }
                }
                decodedFrames += frameLength
                framesRemaining -= frameLength
            }

            val exactOutput = if (outputIndex == output.size) output else output.copyOf(outputIndex)
            signal(exactOutput, sampleRate, channels, decodedFrames)
        } finally {
            file.close()
        }
    }

    private fun normalizedRange(
        totalFrames: Long,
        sampleStart: Long?,
        sampleEnd: Long?,
    ): LongRange? {
        if (totalFrames < 0L) return null
        val start = (sampleStart ?: 0L).coerceIn(0L, totalFrames)
        val end = (sampleEnd ?: totalFrames).coerceIn(start, totalFrames)
        return start..end
    }

    private fun allocatePcm24(frameCount: Long, channels: Int): ByteArray? {
        if (frameCount < 0L) return null
        val byteCount = frameCount * channels.toLong() * PCM24_BYTES
        if (byteCount < 0L || byteCount > Int.MAX_VALUE) return null
        return ByteArray(byteCount.toInt())
    }

    private fun writePcm24(output: ByteArray, offset: Int, sample: Float): Int {
        val normalized = sample.coerceIn(-1f, 1f)
        val value = if (normalized <= -1f) {
            -8_388_608
        } else {
            (normalized * 8_388_607f).roundToInt()
        }
        output[offset] = (value and 0xFF).toByte()
        output[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        output[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        return offset + PCM24_BYTES
    }

    private fun signal(
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int,
        frameCount: Long,
    ) = Signal.AudioSignal(
        origin = "Echo.Decoder",
        rawData = pcm,
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = 24,
        durationMs = frameCount * 1_000L / sampleRate,
    )

    private fun EchoAudioBuffer.toSignal(
        sampleStart: Long?,
        sampleEnd: Long?,
    ): Signal.AudioSignal? {
        val channelCount = channels.toInt()
        val rate = sampleRate.toInt()
        if (rate <= 0 || channelCount !in 1..2) return null

        val totalFrames = samples.size / channelCount
        val start = (sampleStart ?: 0L).coerceIn(0L, totalFrames.toLong()).toInt()
        val end = (sampleEnd ?: totalFrames.toLong())
            .coerceIn(start.toLong(), totalFrames.toLong())
            .toInt()
        val output = allocatePcm24((end - start).toLong(), channelCount) ?: return null
        var outputIndex = 0
        var sampleIndex = start * channelCount
        val sampleEndIndex = end * channelCount
        while (sampleIndex < sampleEndIndex) {
            outputIndex = writePcm24(output, outputIndex, samples[sampleIndex])
            sampleIndex++
        }
        return signal(output, rate, channelCount, (end - start).toLong())
    }

    private const val PCM24_BYTES = 3
    private const val DECODE_BLOCK_FRAMES = 4_096
}
