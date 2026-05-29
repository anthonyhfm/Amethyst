package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import org.jflac.FLACDecoder
import org.jflac.PCMProcessor
import org.jflac.metadata.StreamInfo
import org.jflac.util.ByteData

actual object AudioDecoder {

    private val supportedFormats = listOf("wav", "mp3", "flac", "ogg", "m4a", "aac", "aif", "aiff")

    actual suspend fun decodeAudioFile(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext null
            decodeAudioData(file.readBytes(), file.name, sampleStart, sampleEnd)
        } catch (e: Throwable) {
            println("AudioDecoder: Failed to decode audio file '$filePath': ${e.message}")
            null
        }
    }

    actual suspend fun decodeAudioData(
        audioData: ByteArray,
        fileName: String,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? = withContext(Dispatchers.IO) {
        try {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            when (ext) {
                "wav"  -> decodeWav(audioData, sampleStart, sampleEnd)
                "flac" -> decodeFlac(audioData, sampleStart, sampleEnd)
                "mp3", "ogg", "m4a", "aac", "aif", "aiff" -> decodeViaJavaSound(audioData, sampleStart, sampleEnd)
                else -> null
            }
        } catch (e: Throwable) {
            println("AudioDecoder: Failed to decode audio data for '$fileName': ${e.message}")
            null
        }
    }

    actual fun getSupportedFormats(): List<String> = supportedFormats
    actual fun isFormatSupported(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in supportedFormats

    private fun decodeWav(bytes: ByteArray, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? {
        val info = sniffWavInfo(bytes)
        if (info != null) {
            val decoded = decodeWavManual(bytes, info, sampleStart, sampleEnd)
            if (decoded != null) return decoded
        }

        return decodeViaJavaSound(bytes, sampleStart, sampleEnd)
    }

    private data class WavInfo(
        val audioFormat: Int, // 1=PCM, 3=IEEE_FLOAT, 0xFFFE=Extensible
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffset: Int,
        val dataSize: Int
    )

    private fun sniffWavInfo(bytes: ByteArray): WavInfo? {
        if (bytes.size < 44) return null
        fun rd32(o: Int) = (bytes[o].toInt() and 0xFF) or
                ((bytes[o+1].toInt() and 0xFF) shl 8) or
                ((bytes[o+2].toInt() and 0xFF) shl 16) or
                ((bytes[o+3].toInt() and 0xFF) shl 24)
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null

        var p = 12
        var fmtOff = -1
        var dataOff = -1
        var dataSize = 0
        while (p + 8 <= bytes.size) {
            val id = String(bytes, p, 4)
            val sz = rd32(p + 4)
            val next = p + 8 + sz + (sz % 2)
            if (id == "fmt ") fmtOff = p + 8
            if (id == "data") { dataOff = p + 8; dataSize = sz }
            p = next
            if (p > bytes.size) break
        }
        if (fmtOff < 0 || dataOff < 0) return null

        var audioFormat = (bytes[fmtOff].toInt() and 0xFF) or ((bytes[fmtOff+1].toInt() and 0xFF) shl 8)
        if (audioFormat == 0xFFFE && fmtOff + 26 <= bytes.size) {
            val subFormat = (bytes[fmtOff+24].toInt() and 0xFF) or ((bytes[fmtOff+25].toInt() and 0xFF) shl 8)
            if (subFormat == 1 || subFormat == 3) {
                audioFormat = subFormat
            }
        }
        val channels = (bytes[fmtOff+2].toInt() and 0xFF) or ((bytes[fmtOff+3].toInt() and 0xFF) shl 8)
        val sampleRate = rd32(fmtOff + 4)
        val bitsPerSample = (bytes[fmtOff+14].toInt() and 0xFF) or ((bytes[fmtOff+15].toInt() and 0xFF) shl 8)

        return WavInfo(audioFormat, channels, sampleRate, bitsPerSample, dataOff, dataSize)
    }

    private fun decodeWavManual(
        bytes: ByteArray,
        info: WavInfo,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? {
        val ch = if (info.channels >= 1) info.channels else 2
        val srcBps = info.bitsPerSample / 8
        if (srcBps <= 0) return null

        val frameSize = srcBps * ch
        val totalFrames = info.dataSize / frameSize
        val startF = (sampleStart ?: 0L).coerceAtLeast(0L)
        val endF = (sampleEnd ?: totalFrames.toLong()).coerceAtMost(totalFrames.toLong())
        if (startF >= endF) return Signal.AudioSignal("AudioDecoder.WAV_MANUAL", ByteArray(0), info.sampleRate, ch, 16, 0)

        val startByte = info.dataOffset + (startF * frameSize).toInt()
        val frames = (endF - startF).toInt()

        // Safety check to ensure we don't read out of bounds of the input array
        val maxRequiredBytes = startByte + frames * frameSize
        if (maxRequiredBytes > bytes.size) {
            val availableBytes = (bytes.size - startByte).coerceAtLeast(0)
            val availableFrames = availableBytes / frameSize
            if (availableFrames <= 0) {
                return Signal.AudioSignal("AudioDecoder.WAV_MANUAL", ByteArray(0), info.sampleRate, ch, 16, 0)
            }
            return decodeWavManual(bytes, info.copy(dataSize = availableFrames * frameSize), sampleStart, sampleEnd)
        }

        val out = ByteArray(frames * ch * 2)
        var inPos = startByte
        var outPos = 0

        try {
            if (info.audioFormat == 3 && info.bitsPerSample == 32) {
                // 32-bit Float
                repeat(frames * ch) {
                    val b0 = bytes[inPos].toInt() and 0xFF
                    val b1 = bytes[inPos+1].toInt() and 0xFF
                    val b2 = bytes[inPos+2].toInt() and 0xFF
                    val b3 = bytes[inPos+3].toInt() and 0xFF
                    val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                    val f = java.lang.Float.intBitsToFloat(bits).coerceIn(-1.0f, 1.0f)
                    val s = (f * 32767.0f).toInt().coerceIn(-32768, 32767)
                    out[outPos] = (s and 0xFF).toByte()
                    out[outPos + 1] = ((s ushr 8) and 0xFF).toByte()
                    inPos += 4
                    outPos += 2
                }
            } else if (info.audioFormat == 1) {
                // PCM formats
                when (info.bitsPerSample) {
                    16 -> {
                        // 16-bit PCM is already in the target format
                        System.arraycopy(bytes, startByte, out, 0, out.size)
                    }
                    24 -> {
                        // 24-bit PCM: convert to 16-bit by dropping the LSB (first byte)
                        repeat(frames * ch) {
                            out[outPos] = bytes[inPos + 1]
                            out[outPos + 1] = bytes[inPos + 2]
                            inPos += 3
                            outPos += 2
                        }
                    }
                    8 -> {
                        // 8-bit PCM is unsigned in WAV (0 to 255, where 128 is center)
                        repeat(frames * ch) {
                            val s8 = (bytes[inPos].toInt() and 0xFF) - 128
                            val s16 = s8 * 256
                            out[outPos] = (s16 and 0xFF).toByte()
                            out[outPos + 1] = ((s16 ushr 8) and 0xFF).toByte()
                            inPos += 1
                            outPos += 2
                        }
                    }
                    32 -> {
                        // 32-bit PCM: convert to 16-bit by taking the upper 2 bytes
                        repeat(frames * ch) {
                            out[outPos] = bytes[inPos + 2]
                            out[outPos + 1] = bytes[inPos + 3]
                            inPos += 4
                            outPos += 2
                        }
                    }
                    else -> return null // Unsupported bits per sample for manual PCM decoding
                }
            } else {
                return null // Unsupported format for manual decoding (e.g. A-law, u-law, etc.)
            }
        } catch (e: Throwable) {
            println("AudioDecoder: Failed during manual WAV decoding: ${e.message}")
            return null
        }

        val bytesPerSample = 2 * ch
        val totalSamples = out.size / bytesPerSample
        val durationMs = (totalSamples * 1000L) / info.sampleRate

        return Signal.AudioSignal(
            origin = "AudioDecoder.WAV_MANUAL",
            rawData = out,
            sampleRate = info.sampleRate,
            channels = ch,
            bitDepth = 16,
            durationMs = durationMs
        )
    }

    private fun decodeViaJavaSound(bytes: ByteArray, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? {
        val input = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
        val src = input.format

        // Preserve original sample rate - don't resample unless necessary
        val targetSampleRate = if (src.sampleRate > 0) src.sampleRate else 44100f

        // Properly handle channels:
        // - Mono stays mono
        // - Stereo stays stereo
        // - Multi-channel (>2) gets downmixed to stereo by JavaSound
        val targetChannels = when {
            src.channels <= 0 -> 2
            src.channels == 1 -> 1  // Keep mono as mono
            src.channels == 2 -> 2  // Keep stereo as stereo
            else -> 2  // Downmix multi-channel to stereo
        }

        val target = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            targetSampleRate,
            16,
            targetChannels,
            targetChannels * 2,  // frameSize = channels * bytesPerSample
            targetSampleRate,
            false  // little-endian
        )

        val pcmStream: AudioInputStream = if (AudioSystem.isConversionSupported(target, src)) {
            AudioSystem.getAudioInputStream(target, input)
        } else {
            input
        }

        val data = pcmStream.readAllBytes()
        pcmStream.close()
        input.close()

        val frameSize = target.frameSize
        val trimmed = if (frameSize > 0 && (sampleStart != null || sampleEnd != null)) {
            val totalFrames = data.size / frameSize
            val startF = (sampleStart ?: 0L).coerceAtLeast(0L)
            val endF = (sampleEnd ?: totalFrames.toLong()).coerceAtMost(totalFrames.toLong())
            if (startF >= endF) ByteArray(0) else {
                val sb = (startF * frameSize).toInt()
                val eb = (endF * frameSize).toInt()
                data.copyOfRange(sb, eb)
            }
        } else data

        val bytesPerSample = target.channels * 2
        val totalSamples = if (bytesPerSample > 0) trimmed.size / bytesPerSample else 0
        val durationMs = if (target.sampleRate > 0) (totalSamples * 1000L) / target.sampleRate.toInt() else 0L

        return Signal.AudioSignal(
            origin = "AudioDecoder.Generic",
            rawData = trimmed,
            sampleRate = if (target.sampleRate > 0) target.sampleRate.toInt() else 44100,
            channels = target.channels,
            bitDepth = 16,
            durationMs = durationMs
        )
    }

    private fun decodeFlac(bytes: ByteArray, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? {
        val collector = FlacPCMDataCollector()
        val decoder = FLACDecoder(ByteArrayInputStream(bytes))
        decoder.addPCMProcessor(collector)
        decoder.decode()

        val si = collector.streamInfo ?: return null
        var pcm = collector.getPCMData()

        if (sampleStart != null || sampleEnd != null) {
            val bps = (si.bitsPerSample / 8) * si.channels
            val totalSamples = if (bps > 0) pcm.size / bps else 0
            val s = (sampleStart ?: 0L).coerceAtLeast(0L)
            val e = (sampleEnd ?: totalSamples.toLong()).coerceAtMost(totalSamples.toLong())
            pcm = if (s < e && bps > 0) {
                val sb = (s * bps).toInt()
                val eb = (e * bps).toInt()
                pcm.copyOfRange(sb, eb)
            } else ByteArray(0)
        }

        var out = pcm
        if (si.bitsPerSample != 16) out = convertBitDepth(out, si.bitsPerSample, 16, si.channels)

        // Keep original channel count - don't force mono to stereo
        val channels = si.channels

        val bytesPerSample = (16 / 8) * channels
        val totalSamples = if (bytesPerSample > 0) out.size / bytesPerSample else 0
        val durationMs = (totalSamples * 1000L) / si.sampleRate

        return Signal.AudioSignal(
            origin = "AudioDecoder.FLAC",
            rawData = out,
            sampleRate = si.sampleRate,
            channels = channels,
            bitDepth = 16,
            durationMs = durationMs
        )
    }

    private class FlacPCMDataCollector : PCMProcessor {
        private val chunks = mutableListOf<ByteArray>()
        var streamInfo: StreamInfo? = null; private set
        override fun processStreamInfo(streamInfo: StreamInfo) { this.streamInfo = streamInfo }
        override fun processPCM(pcm: ByteData) {
            val copy = ByteArray(pcm.len)
            System.arraycopy(pcm.data, 0, copy, 0, pcm.len)
            chunks.add(copy)
        }
        fun getPCMData(): ByteArray {
            val total = chunks.sumOf { it.size }
            val out = ByteArray(total)
            var o = 0
            for (c in chunks) { System.arraycopy(c, 0, out, o, c.size); o += c.size }
            return out
        }
    }

    private fun convertBitDepth(data: ByteArray, srcBits: Int, dstBits: Int, channels: Int): ByteArray {
        if (srcBits == dstBits) return data
        val srcB = srcBits / 8
        val dstB = dstBits / 8
        val samples = if (srcB > 0 && channels > 0) data.size / (srcB * channels) else 0
        val out = ByteArray(samples * dstB * channels)

        var si = 0
        var di = 0
        for (i in 0 until samples * channels) {
            when {
                srcBits == 24 && dstBits == 16 -> {
                    val s24 = ((data[si + 2].toInt() and 0xFF) shl 16) or
                            ((data[si + 1].toInt() and 0xFF) shl 8) or
                            (data[si].toInt() and 0xFF)
                    val s16 = (s24 shr 8).toShort().toInt()
                    out[di] = (s16 and 0xFF).toByte()
                    out[di + 1] = ((s16 ushr 8) and 0xFF).toByte()
                }
                srcBits == 32 && dstBits == 16 -> {
                    val s32 = ((data[si + 3].toInt() and 0xFF) shl 24) or
                            ((data[si + 2].toInt() and 0xFF) shl 16) or
                            ((data[si + 1].toInt() and 0xFF) shl 8) or
                            (data[si].toInt() and 0xFF)
                    val s16 = (s32 shr 16).toShort().toInt()
                    out[di] = (s16 and 0xFF).toByte()
                    out[di + 1] = ((s16 ushr 8) and 0xFF).toByte()
                }
                srcBits == 8 && dstBits == 16 -> {
                    val s8 = (data[si].toInt() and 0xFF) - 128
                    val s16 = s8 * 256
                    out[di] = (s16 and 0xFF).toByte()
                    out[di + 1] = ((s16 ushr 8) and 0xFF).toByte()
                }
                else -> {
                    val copy = kotlin.math.min(srcB, dstB)
                    for (b in 0 until copy) out[di + b] = data[si + b]
                    for (b in copy until dstB) out[di + b] = 0
                }
            }
            si += srcB
            di += dstB
        }
        return out
    }

    private fun monoToStereo(data: ByteArray, bitsPerSample: Int): ByteArray {
        val bps = bitsPerSample / 8
        val samples = if (bps > 0) data.size / bps else 0
        val out = ByteArray(data.size * 2)
        var si = 0
        var di = 0
        for (i in 0 until samples) {
            for (b in 0 until bps) {
                val v = data[si + b]
                out[di + b] = v
                out[di + bps + b] = v
            }
            si += bps
            di += bps * 2
        }
        return out
    }
}
