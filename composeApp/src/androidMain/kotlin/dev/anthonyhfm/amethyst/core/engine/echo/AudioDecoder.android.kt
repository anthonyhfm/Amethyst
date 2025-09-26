package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import java.io.File
import java.nio.ByteBuffer

actual object AudioDecoder {

    private val supportedFormats = listOf("wav", "mp3", "flac", "m4a", "aac", "ogg")

    actual suspend fun decodeAudioFile(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                println("Audio file not found: $filePath")
                return@withContext null
            }
            return@withContext decodeWithMediaExtractor(filePath, sampleStart, sampleEnd)
        } catch (e: Exception) {
            println("Failed to load audio file '$filePath': ${e.message}")
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
            val ext = fileName.substringAfterLast('.', "tmp")
            val tempFile = File.createTempFile("audio_decode", ".$ext")
            tempFile.writeBytes(audioData)
            val result = decodeWithMediaExtractor(tempFile.absolutePath, sampleStart, sampleEnd)
            tempFile.delete()
            result
        } catch (e: Exception) {
            println("Failed to decode audio data for '$fileName': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    actual fun isFormatSupported(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in supportedFormats
    }

    actual fun getSupportedFormats(): List<String> = supportedFormats.toList()

    private fun decodeWithMediaExtractor(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                println("No audio track found in file")
                return null
            }
            extractor.selectTrack(audioTrackIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val sourceSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            val inputBuffers = decoder.inputBuffers
            val outputBuffers = decoder.outputBuffers
            val bufferInfo = MediaCodec.BufferInfo()

            val pcmChunks = ArrayList<ByteArray>()
            var endOfStream = false

            while (!endOfStream) {
                val inIndex = decoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inputBuffer: ByteBuffer = inputBuffers[inIndex]
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                    } else {
                        decoder.queueInputBuffer(
                            inIndex, 0, sampleSize,
                            extractor.sampleTime,
                            0
                        )
                        extractor.advance()
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outputBuffer: ByteBuffer = outputBuffers[outIndex]
                        if (bufferInfo.size > 0) {
                            val bytes = ByteArray(bufferInfo.size)
                            outputBuffer.get(bytes)
                            outputBuffer.clear()
                            pcmChunks.add(bytes)
                        }
                        val flagsEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (flagsEnd) endOfStream = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                    }
                    outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {

                        decoder.outputBuffers.let {  }
                    }
                }
            }

            val sourceFrameSize = sourceChannels * 2
            val totalSize = pcmChunks.sumOf { it.size }
            val sourcePcm = ByteArray(totalSize)
            var copyPos = 0
            for (c in pcmChunks) {
                System.arraycopy(c, 0, sourcePcm, copyPos, c.size)
                copyPos += c.size
            }

            val trimmedSource = trimPcm(
                pcm = sourcePcm,
                frameSize = sourceFrameSize,
                sampleStart = sampleStart,
                sampleEnd = sampleEnd
            )

            val converted = convertToTargetFormat(
                pcmData = trimmedSource,
                sourceSampleRate = sourceSampleRate,
                sourceChannels = sourceChannels,
                targetSampleRate = 44100,
                targetChannels = 2
            )

            val targetFrameSize = 2 * 2
            val totalFrames = if (targetFrameSize > 0) converted.size / targetFrameSize else 0
            val durationMs = if (totalFrames > 0) (totalFrames * 1000L) / 44100L else 0L

            return Signal.AudioSignal(
                origin = "AudioDecoder.Android",
                rawData = converted,
                sampleRate = 44100,
                channels = 2,
                bitDepth = 16,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            println("Failed to decode audio with MediaExtractor: ${e.message}")
            e.printStackTrace()
            return null
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
                extractor?.release()
            } catch (_: Exception) {}
        }
    }

    private fun trimPcm(
        pcm: ByteArray,
        frameSize: Int,
        sampleStart: Long?,
        sampleEnd: Long?
    ): ByteArray {
        if (sampleStart == null && sampleEnd == null) return pcm
        if (frameSize <= 0) return pcm
        val totalFrames = pcm.size / frameSize
        val startFrame = (sampleStart ?: 0L).coerceAtLeast(0L).coerceAtMost(totalFrames.toLong())
        val endFrame = (sampleEnd ?: totalFrames.toLong()).coerceAtLeast(0L).coerceAtMost(totalFrames.toLong())
        if (startFrame >= endFrame) return ByteArray(0)
        val startByte = (startFrame * frameSize).toInt()
        val endByte = (endFrame * frameSize).toInt().coerceAtMost(pcm.size)
        return pcm.copyOfRange(startByte, endByte)
    }

    private fun convertToTargetFormat(
        pcmData: ByteArray,
        sourceSampleRate: Int,
        sourceChannels: Int,
        targetSampleRate: Int,
        targetChannels: Int
    ): ByteArray {
        var data = pcmData
        if (sourceSampleRate != targetSampleRate) {
            data = resampleLinear16(data, sourceSampleRate, targetSampleRate, sourceChannels)
        }
        if (sourceChannels != targetChannels) {
            data = convertChannels16(data, sourceChannels, targetChannels)
        }
        return data
    }

    private fun resampleLinear16(
        pcmData: ByteArray,
        sourceSampleRate: Int,
        targetSampleRate: Int,
        channels: Int
    ): ByteArray {
        val bytesPerSample = 2
        val frameSize = channels * bytesPerSample
        val sourceFrames = if (frameSize > 0) pcmData.size / frameSize else 0
        if (sourceFrames == 0 || sourceSampleRate == targetSampleRate) return pcmData
        val ratio = sourceSampleRate.toDouble() / targetSampleRate.toDouble()
        val targetFrames = (sourceFrames / ratio).toInt().coerceAtLeast(1)
        val target = ByteArray(targetFrames * frameSize)
        for (i in 0 until targetFrames) {
            val srcIndex = (i * ratio).toInt().coerceAtMost(sourceFrames - 1)
            val srcByte = srcIndex * frameSize
            val destByte = i * frameSize
            System.arraycopy(pcmData, srcByte, target, destByte, frameSize)
        }
        return target
    }

    private fun convertChannels16(pcmData: ByteArray, sourceChannels: Int, targetChannels: Int): ByteArray {
        if (sourceChannels == targetChannels) return pcmData
        val bytesPerSample = 2
        val sourceFrameSize = sourceChannels * bytesPerSample
        val frames = if (sourceFrameSize > 0) pcmData.size / sourceFrameSize else 0
        if (frames == 0) return ByteArray(0)
        return when {
            sourceChannels == 1 && targetChannels == 2 -> {
                val target = ByteArray(frames * targetChannels * bytesPerSample)
                var inPos = 0
                var outPos = 0
                while (inPos + 1 < pcmData.size) {
                    val b0 = pcmData[inPos]
                    val b1 = pcmData[inPos + 1]

                    target[outPos] = b0
                    target[outPos + 1] = b1
                    target[outPos + 2] = b0
                    target[outPos + 3] = b1
                    inPos += 2
                    outPos += 4
                }
                target
            }
            sourceChannels == 2 && targetChannels == 1 -> {
                val target = ByteArray(frames * targetChannels * bytesPerSample)
                var inPos = 0
                var outPos = 0
                while (inPos + 3 < pcmData.size) {
                    val left = (pcmData[inPos].toInt() and 0xFF) or ((pcmData[inPos + 1].toInt() and 0xFF) shl 8)
                    val right = (pcmData[inPos + 2].toInt() and 0xFF) or ((pcmData[inPos + 3].toInt() and 0xFF) shl 8)
                    val mono = ((left + right) / 2).coerceIn(-32768, 32767)
                    target[outPos] = (mono and 0xFF).toByte()
                    target[outPos + 1] = ((mono shr 8) and 0xFF).toByte()
                    inPos += 4
                    outPos += 2
                }
                target
            }
            else -> {
                pcmData
            }
        }
    }
}
