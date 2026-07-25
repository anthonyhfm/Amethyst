package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import io.github.vinceglb.filekit.utils.toNSData
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import swiftPMImport.dev.anthonyhfm.amethyst.composeApp.OggVorbis_File
import swiftPMImport.dev.anthonyhfm.amethyst.composeApp.ov_clear
import swiftPMImport.dev.anthonyhfm.amethyst.composeApp.ov_fopen
import swiftPMImport.dev.anthonyhfm.amethyst.composeApp.ov_info
import swiftPMImport.dev.anthonyhfm.amethyst.composeApp.ov_pcm_seek
import swiftPMImport.dev.anthonyhfm.amethyst.composeApp.ov_pcm_total
import swiftPMImport.dev.anthonyhfm.amethyst.composeApp.ov_read_float
import kotlin.math.roundToInt

/**
 * iOS decoder that normalizes every supported format to the same signed
 * little-endian 24-bit PCM representation used by the desktop backend.
 *
 * AVFoundation handles the system formats. Ogg/Vorbis uses libvorbis through
 * Kotlin's SwiftPM Clang-module import, without a Swift bridge.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosAudioDecoder {
    val formats = listOf("wav", "mp3", "flac", "ogg", "aiff", "aif", "aifc")

    fun decodeFile(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?,
    ): Signal.AudioSignal? {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        if (extension !in formats) return null
        return if (extension == "ogg") {
            decodeVorbis(filePath, sampleStart, sampleEnd)
        } else {
            decodeWithAvFoundation(filePath, sampleStart, sampleEnd)
        }
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
            decodeFile(temporaryPath, sampleStart, sampleEnd)
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

    private fun decodeVorbis(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?,
    ): Signal.AudioSignal? = memScoped {
        val vorbisFile = alloc<OggVorbis_File>()
        if (ov_fopen(filePath, vorbisFile.ptr) != 0) return null

        try {
            val initialInfo = ov_info(vorbisFile.ptr, -1)?.pointed ?: return null
            val sampleRate = initialInfo.rate.toInt()
            val channels = initialInfo.channels
            if (sampleRate <= 0 || channels !in 1..2) return null

            val range = normalizedRange(
                totalFrames = ov_pcm_total(vorbisFile.ptr, -1),
                sampleStart = sampleStart,
                sampleEnd = sampleEnd,
            ) ?: return null
            val requestedFrames = range.last - range.first
            val output = allocatePcm24(requestedFrames, channels) ?: return null
            if (requestedFrames == 0L) {
                return signal(output, sampleRate, channels, 0L)
            }
            if (range.first > 0L && ov_pcm_seek(vorbisFile.ptr, range.first) != 0) {
                return null
            }

            val pcmChannels = alloc<CPointerVar<CPointerVar<FloatVar>>>()
            val bitstream = alloc<IntVar>()
            var framesRemaining = requestedFrames
            var outputIndex = 0
            var decodedFrames = 0L
            var recoverableHoles = 0
            while (framesRemaining > 0L) {
                val framesToRead = minOf(DECODE_BLOCK_FRAMES.toLong(), framesRemaining).toInt()
                val read = ov_read_float(
                    vorbisFile.ptr,
                    pcmChannels.ptr,
                    framesToRead,
                    bitstream.ptr,
                )
                if (read == 0L) break
                if (read < 0L) {
                    if (++recoverableHoles > MAX_RECOVERABLE_VORBIS_HOLES) return null
                    continue
                }
                recoverableHoles = 0

                val streamInfo = ov_info(vorbisFile.ptr, bitstream.value)?.pointed ?: return null
                if (streamInfo.rate.toInt() != sampleRate || streamInfo.channels != channels) {
                    return null
                }
                val planar = pcmChannels.value ?: return null
                var frame = 0
                while (frame < read.toInt()) {
                    var channel = 0
                    while (channel < channels) {
                        val samples = planar[channel] ?: return null
                        outputIndex = writePcm24(output, outputIndex, samples[frame])
                        channel++
                    }
                    frame++
                }
                decodedFrames += read
                framesRemaining -= read
            }

            val exactOutput = if (outputIndex == output.size) output else output.copyOf(outputIndex)
            signal(exactOutput, sampleRate, channels, decodedFrames)
        } finally {
            ov_clear(vorbisFile.ptr)
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

    private const val PCM24_BYTES = 3
    private const val DECODE_BLOCK_FRAMES = 4_096
    private const val MAX_RECOVERABLE_VORBIS_HOLES = 8
}
