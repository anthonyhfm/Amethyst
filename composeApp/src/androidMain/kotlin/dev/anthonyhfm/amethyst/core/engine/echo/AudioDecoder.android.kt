package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.os.Build
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min

actual object AudioDecoder {

    private val supportedFormats = listOf("wav", "mp3", "flac", "m4a", "aac", "ogg")

    private const val TARGET_SR = 44100
    private const val TARGET_CHANNELS = 2
    private const val TARGET_BITS = 16

    actual suspend fun decodeAudioFile(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? = withContext(Dispatchers.IO) {
        try {
            val f = File(filePath)
            if (!f.exists()) return@withContext null

            val ext = f.extension.lowercase()
            if (ext == "wav") {
                val bytes = f.readBytes()
                return@withContext decodeWav(bytes, sampleStart, sampleEnd)
            }

            return@withContext decodeWithMediaExtractor(path = filePath, bytes = null, sampleStart = sampleStart, sampleEnd = sampleEnd)
        } catch (e: Exception) {
            e.printStackTrace()
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
            val ext = fileName.substringAfterLast('.', "tmp").lowercase()
            if (ext == "wav") {
                return@withContext decodeWav(audioData, sampleStart, sampleEnd)
            }

            return@withContext decodeWithMediaExtractor(
                path = null,
                bytes = audioData,
                sampleStart = sampleStart,
                sampleEnd = sampleEnd
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    actual fun isFormatSupported(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in supportedFormats
    }

    actual fun getSupportedFormats(): List<String> = supportedFormats.toList()

    private data class WavInfo(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffset: Int,
        val dataSize: Int
    )

    private fun sniffWavInfo(bytes: ByteArray): WavInfo? {
        if (bytes.size < 44) return null
        fun rd32(o: Int) = (bytes[o].toInt() and 0xFF) or
                ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                ((bytes[o + 2].toInt() and 0xFF) shl 16) or
                ((bytes[o + 3].toInt() and 0xFF) shl 24)
        fun tag(o: Int) = String(bytes, o, 4)

        if (tag(0) != "RIFF" || tag(8) != "WAVE") return null

        var p = 12
        var fmtOff = -1
        var dataOff = -1
        var dataSize = 0
        while (p + 8 <= bytes.size) {
            val id = tag(p)
            val sz = rd32(p + 4)
            val next = p + 8 + sz + (sz % 2)
            if (id == "fmt ") fmtOff = p + 8
            if (id == "data") { dataOff = p + 8; dataSize = sz }
            p = next
            if (p > bytes.size) break
        }
        if (fmtOff < 0 || dataOff < 0) return null

        val audioFormat = (bytes[fmtOff].toInt() and 0xFF) or ((bytes[fmtOff + 1].toInt() and 0xFF) shl 8)
        val channels = (bytes[fmtOff + 2].toInt() and 0xFF) or ((bytes[fmtOff + 3].toInt() and 0xFF) shl 8)
        val sampleRate = rd32(fmtOff + 4)
        val bitsPerSample = (bytes[fmtOff + 14].toInt() and 0xFF) or ((bytes[fmtOff + 15].toInt() and 0xFF) shl 8)

        return WavInfo(audioFormat, channels, sampleRate, bitsPerSample, dataOff, dataSize)
    }

    private fun decodeWav(bytes: ByteArray, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? {
        val info = sniffWavInfo(bytes) ?: return null
        val ch = if (info.channels >= 1) info.channels else 2
        val bps = info.bitsPerSample
        val sr = if (info.sampleRate > 0) info.sampleRate else TARGET_SR
        val dataEnd = (info.dataOffset + info.dataSize).coerceAtMost(bytes.size)

        if (dataEnd <= info.dataOffset) return null
        val raw = bytes.copyOfRange(info.dataOffset, dataEnd)

        val frameBytesSrc = (bps / 8) * ch
        val totalFrames = if (frameBytesSrc > 0) raw.size / frameBytesSrc else 0

        val startF = (sampleStart ?: 0L).coerceAtLeast(0L)
        val endF = (sampleEnd ?: totalFrames.toLong()).coerceAtMost(totalFrames.toLong())
        val trimmed = if (startF < endF && frameBytesSrc > 0) {
            val sb = (startF * frameBytesSrc).toInt()
            val eb = (endF * frameBytesSrc).toInt().coerceAtMost(raw.size)
            raw.copyOfRange(sb, eb)
        } else ByteArray(0)

        val pcm16 = when {
            info.audioFormat == 3 && bps == 32 -> // Float32
                float32ToInt16LE(trimmed, ch)
            info.audioFormat == 1 && bps == 16 ->
                trimmed
            info.audioFormat == 1 && bps == 8 ->
                pcm8To16(trimmed)
            info.audioFormat == 1 && bps == 24 ->
                pcm24To16(trimmed, ch)
            info.audioFormat == 1 && bps == 32 ->
                pcm32To16(trimmed, ch)
            else -> trimmed
        }

        // Resample + Channel-Convert auf Ziel
        val afterRate = if (sr != TARGET_SR) resampleLinear16(pcm16, sr, TARGET_SR, ch) else pcm16
        val final = if (ch != TARGET_CHANNELS) convertChannels16(afterRate, ch, TARGET_CHANNELS) else afterRate

        val frameBytesDst = TARGET_CHANNELS * 2
        val framesDst = if (frameBytesDst > 0) final.size / frameBytesDst else 0
        val durMs = if (framesDst > 0) (framesDst * 1000L) / TARGET_SR else 0L

        return Signal.AudioSignal(
            origin = "AudioDecoder.Android.WAV",
            rawData = final,
            sampleRate = TARGET_SR,
            channels = TARGET_CHANNELS,
            bitDepth = TARGET_BITS,
            durationMs = durMs
        )
    }

    private fun float32ToInt16LE(src: ByteArray, channels: Int): ByteArray {
        val samples = src.size / 4
        val out = ByteArray(samples * 2)
        var inPos = 0
        var outPos = 0
        while (inPos + 3 < src.size) {
            val b0 = src[inPos].toInt() and 0xFF
            val b1 = src[inPos + 1].toInt() and 0xFF
            val b2 = src[inPos + 2].toInt() and 0xFF
            val b3 = src[inPos + 3].toInt() and 0xFF
            val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            val f = Float.fromBits(bits).coerceIn(-1f, 1f)
            val s = (f * 32767.0f).toInt().coerceIn(-32768, 32767)
            out[outPos] = (s and 0xFF).toByte()
            out[outPos + 1] = ((s ushr 8) and 0xFF).toByte()
            inPos += 4
            outPos += 2
        }
        return out
    }

    private fun pcm8To16(src: ByteArray): ByteArray {
        val out = ByteArray(src.size * 2)
        var i = 0
        var o = 0
        while (i < src.size) {
            val s = (src[i].toInt() and 0xFF) - 128
            val s16 = s * 256
            out[o] = (s16 and 0xFF).toByte()
            out[o + 1] = ((s16 ushr 8) and 0xFF).toByte()
            i++
            o += 2
        }
        return out
    }

    private fun pcm24To16(src: ByteArray, channels: Int): ByteArray {
        val samples = src.size / (3)
        val out = ByteArray(samples * 2)
        var i = 0
        var o = 0
        while (i + 2 < src.size) {
            val s24 = ((src[i + 2].toInt() and 0xFF) shl 16) or
                    ((src[i + 1].toInt() and 0xFF) shl 8) or
                    (src[i].toInt() and 0xFF)
            val s16 = (s24 shr 8).toShort().toInt()
            out[o] = (s16 and 0xFF).toByte()
            out[o + 1] = ((s16 ushr 8) and 0xFF).toByte()
            i += 3
            o += 2
        }
        return out
    }

    private fun pcm32To16(src: ByteArray, channels: Int): ByteArray {
        val samples = src.size / 4
        val out = ByteArray(samples * 2)
        var i = 0
        var o = 0
        while (i + 3 < src.size) {
            val s32 = ((src[i + 3].toInt() and 0xFF) shl 24) or
                    ((src[i + 2].toInt() and 0xFF) shl 16) or
                    ((src[i + 1].toInt() and 0xFF) shl 8) or
                    (src[i].toInt() and 0xFF)
            val s16 = (s32 shr 16).toShort().toInt()
            out[o] = (s16 and 0xFF).toByte()
            out[o + 1] = ((s16 ushr 8) and 0xFF).toByte()
            i += 4
            o += 2
        }
        return out
    }

    private fun decodeWithMediaExtractor(
        path: String?,
        bytes: ByteArray?,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            if (bytes != null && Build.VERSION.SDK_INT >= 23) {
                extractor.setDataSource(object : MediaDataSource() {
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        if (position >= bytes.size) return -1
                        val len = min(size, bytes.size - position.toInt())
                        System.arraycopy(bytes, position.toInt(), buffer, offset, len)
                        return len
                    }
                    override fun getSize(): Long = bytes.size.toLong()
                    override fun close() {}
                })
            } else if (path != null) {
                extractor.setDataSource(path)
            } else {
                return null
            }

            // Track finden
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i; fmt = f; break
                }
            }
            if (track < 0 || fmt == null) return null
            extractor.selectTrack(track)

            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            val srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcCh = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Optionales Trimmen schon beim Lesen: sampleStart/sampleEnd sind Samples am Originalrate
            val startF = (sampleStart ?: 0L).coerceAtLeast(0L)
            val endF = sampleEnd
            if (startF > 0) {
                val tUs = (startF * 1_000_000L) / srcRate
                extractor.seekTo(tUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(fmt, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            val chunks = ArrayList<ByteArray>()

            var reachedEOS = false
            var outputPCMEncoding = 2 // 2 = PCM 16 bit, 4 = float (MediaFormat.ENCODING_PCM_FLOAT)
            fun readOutputBuffer(outIndex: Int) {
                val outBuf = if (Build.VERSION.SDK_INT >= 21) {
                    decoder!!.getOutputBuffer(outIndex)
                } else {
                    @Suppress("DEPRECATION")
                    decoder!!.outputBuffers[outIndex]
                } ?: return

                if (info.size > 0) {
                    val bytes = ByteArray(info.size)
                    outBuf.get(bytes)
                    outBuf.clear()
                    chunks.add(bytes)
                }
                val endFlag = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                decoder!!.releaseOutputBuffer(outIndex, false)
                if (endFlag) reachedEOS = true
            }

            while (!reachedEOS) {
                // input
                val inIndex = decoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuf = if (Build.VERSION.SDK_INT >= 21) {
                        decoder.getInputBuffer(inIndex)
                    } else {
                        @Suppress("DEPRECATION")
                        decoder.inputBuffers[inIndex]
                    }!!
                    val sampleSize = extractor.readSampleData(inBuf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                    } else {
                        val pts = extractor.sampleTime
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                        extractor.advance()
                        // Early exit, wenn wir am Ende trimmen wollen
                        if (endF != null && extractor.sampleTime >= 0) {
                            val playedFrames = extractor.sampleTime * srcRate / 1_000_000L
                            if (playedFrames >= endF) {
                                // Kein weiterer Input mehr einspeisen
                                decoder.queueInputBuffer(
                                    inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                            }
                        }
                    }
                }

                // output
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex >= 0 -> readOutputBuffer(outIndex)
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = decoder.outputFormat
                        // Check PCM Encoding (Float vs 16)
                        if (Build.VERSION.SDK_INT >= 24 && newFmt.containsKey("pcm-encoding")) {
                            outputPCMEncoding = newFmt.getInteger("pcm-encoding")
                        } else {
                            outputPCMEncoding = 2
                        }
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // no output yet
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        // ignore; we fetch with getOutputBuffer on API>=21
                    }
                }
            }

            // Join PCM
            val total = chunks.sumOf { it.size }
            var pcm = ByteArray(total)
            var pos = 0
            for (c in chunks) {
                System.arraycopy(c, 0, pcm, pos, c.size)
                pos += c.size
            }

            // MediaCodec gibt i. d. R. 16-bit PCM zurück. Falls float (ENCODING_PCM_FLOAT=4), konvertieren:
            if (outputPCMEncoding == 4) { // float
                pcm = float32ToInt16LE(pcm, srcCh)
            }

            // Safety-Trim nach Frames (wenn start offset inside first sample)
            val frameSize = srcCh * 2
            val trimmed = trimPcm(pcm, frameSize, sampleStart, sampleEnd)

            // Resample + Channel convert auf Ziel
            val res = if (srcRate != TARGET_SR) resampleLinear16(trimmed, srcRate, TARGET_SR, srcCh) else trimmed
            val final = if (srcCh != TARGET_CHANNELS) convertChannels16(res, srcCh, TARGET_CHANNELS) else res

            val frameBytes = TARGET_CHANNELS * 2
            val frames = if (frameBytes > 0) final.size / frameBytes else 0
            val durMs = if (frames > 0) (frames * 1000L) / TARGET_SR else 0L

            return Signal.AudioSignal(
                origin = "AudioDecoder.Android.Media",
                rawData = final,
                sampleRate = TARGET_SR,
                channels = TARGET_CHANNELS,
                bitDepth = TARGET_BITS,
                durationMs = durMs
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun trimPcm(
        pcm: ByteArray,
        frameSize: Int,
        sampleStart: Long?,
        sampleEnd: Long?
    ): ByteArray {
        if (frameSize <= 0 || (sampleStart == null && sampleEnd == null)) return pcm
        val totalFrames = pcm.size / frameSize
        val s = (sampleStart ?: 0L).coerceAtLeast(0L).coerceAtMost(totalFrames.toLong())
        val e = (sampleEnd ?: totalFrames.toLong()).coerceAtLeast(0L).coerceAtMost(totalFrames.toLong())
        if (s >= e) return ByteArray(0)
        val sb = (s * frameSize).toInt()
        val eb = (e * frameSize).toInt().coerceAtMost(pcm.size)
        return pcm.copyOfRange(sb, eb)
    }

    private fun resampleLinear16(
        pcm: ByteArray,
        sourceSampleRate: Int,
        targetSampleRate: Int,
        channels: Int
    ): ByteArray {
        if (sourceSampleRate == targetSampleRate) return pcm
        val bps = 2
        val srcFrame = channels * bps
        val srcFrames = if (srcFrame > 0) pcm.size / srcFrame else 0
        if (srcFrames <= 1) return pcm

        val ratio = targetSampleRate.toDouble() / sourceSampleRate.toDouble()
        val dstFrames = (srcFrames * ratio).toInt().coerceAtLeast(1)
        val dst = ByteArray(dstFrames * srcFrame)

        fun rd16LE(off: Int): Int {
            val s = (pcm[off].toInt() and 0xFF) or ((pcm[off + 1].toInt() and 0xFF) shl 8)
            return if (s > 32767) s - 65536 else s
        }
        fun wr16LE(off: Int, v: Int) {
            val cl = v.coerceIn(-32768, 32767)
            dst[off] = (cl and 0xFF).toByte()
            dst[off + 1] = ((cl ushr 8) and 0xFF).toByte()
        }

        for (i in 0 until dstFrames) {
            val srcPos = i / ratio
            val i0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val i1 = (i0 + 1).coerceAtMost(srcFrames - 1)
            val frac = (srcPos - i0).coerceIn(0.0, 1.0)
            val base0 = i0 * srcFrame
            val base1 = i1 * srcFrame
            val outBase = i * srcFrame

            var c = 0
            while (c < channels) {
                val o = c * bps
                val s0 = rd16LE(base0 + o)
                val s1 = rd16LE(base1 + o)
                val interp = s0 + ((s1 - s0) * frac).toInt()
                wr16LE(outBase + o, interp)
                c++
            }
        }
        return dst
    }

    private fun convertChannels16(pcmData: ByteArray, sourceChannels: Int, targetChannels: Int): ByteArray {
        if (sourceChannels == targetChannels) return pcmData
        val bps = 2
        val srcFrame = sourceChannels * bps
        val frames = if (srcFrame > 0) pcmData.size / srcFrame else 0
        if (frames == 0) return ByteArray(0)

        return when {
            sourceChannels == 1 && targetChannels == 2 -> {
                val dst = ByteArray(frames * targetChannels * bps)
                var inPos = 0
                var outPos = 0
                while (inPos + 1 < pcmData.size) {
                    val b0 = pcmData[inPos]
                    val b1 = pcmData[inPos + 1]
                    // L
                    dst[outPos] = b0; dst[outPos + 1] = b1
                    // R
                    dst[outPos + 2] = b0; dst[outPos + 3] = b1
                    inPos += 2
                    outPos += 4
                }
                dst
            }
            sourceChannels == 2 && targetChannels == 1 -> {
                val dst = ByteArray(frames * targetChannels * bps)
                var inPos = 0
                var outPos = 0
                while (inPos + 3 < pcmData.size) {
                    val l = (pcmData[inPos].toInt() and 0xFF) or ((pcmData[inPos + 1].toInt() and 0xFF) shl 8)
                    val r = (pcmData[inPos + 2].toInt() and 0xFF) or ((pcmData[inPos + 3].toInt() and 0xFF) shl 8)
                    val ls = if (l > 32767) l - 65536 else l
                    val rs = if (r > 32767) r - 65536 else r
                    val m = ((ls + rs) / 2).coerceIn(-32768, 32767)
                    dst[outPos] = (m and 0xFF).toByte()
                    dst[outPos + 1] = ((m ushr 8) and 0xFF).toByte()
                    inPos += 4
                    outPos += 2
                }
                dst
            }
            else -> pcmData
        }
    }
}
