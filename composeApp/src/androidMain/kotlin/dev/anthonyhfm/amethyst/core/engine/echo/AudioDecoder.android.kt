package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.AudioFormat
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

actual object AudioDecoder {

    private val supportedFormats = listOf("wav", "mp3", "flac", "m4a", "aac", "ogg")

    actual suspend fun decodeAudioFile(filePath: String): Signal.AudioSignal? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                println("Audio file not found: $filePath")
                return@withContext null
            }

            return@withContext decodeWithMediaExtractor(filePath)

        } catch (e: Exception) {
            println("Failed to load audio file '$filePath': ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }

    actual suspend fun decodeAudioData(audioData: ByteArray, fileName: String): Signal.AudioSignal? = withContext(Dispatchers.IO) {
        try {
            // Create temporary file for MediaExtractor
            val tempFile = File.createTempFile("audio_decode", ".${fileName.substringAfterLast('.')}")
            tempFile.writeBytes(audioData)

            val result = decodeWithMediaExtractor(tempFile.absolutePath)
            tempFile.delete()

            return@withContext result

        } catch (e: Exception) {
            println("Failed to decode audio data for '$fileName': ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }

    actual fun isFormatSupported(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in supportedFormats
    }

    actual fun getSupportedFormats(): List<String> = supportedFormats.toList()

    private fun decodeWithMediaExtractor(filePath: String): Signal.AudioSignal? {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            // Find audio track
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

            // Get audio properties
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Create and configure decoder
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            val inputBuffers = decoder.inputBuffers
            val outputBuffers = decoder.outputBuffers
            val bufferInfo = MediaCodec.BufferInfo()

            var isEOS = false
            val pcmDataList = mutableListOf<ByteArray>()

            while (!isEOS) {
                // Feed input
                val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = inputBuffers[inputBufferIndex]
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)

                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }

                // Get output
                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = outputBuffers[outputBufferIndex]

                        if (bufferInfo.size > 0) {
                            val pcmData = ByteArray(bufferInfo.size)
                            outputBuffer.get(pcmData)
                            outputBuffer.rewind()
                            pcmDataList.add(pcmData)
                        }

                        decoder.releaseOutputBuffer(outputBufferIndex, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            isEOS = true
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Output format changed
                        val newFormat = decoder.outputFormat
                        println("Output format changed: $newFormat")
                    }
                }
            }

            // Combine all PCM data
            val totalSize = pcmDataList.sumOf { it.size }
            val combinedPcmData = ByteArray(totalSize)
            var offset = 0
            for (data in pcmDataList) {
                System.arraycopy(data, 0, combinedPcmData, offset, data.size)
                offset += data.size
            }

            // Convert to target format (44.1kHz, 16-bit, Stereo)
            val convertedData = convertToTargetFormat(
                pcmData = combinedPcmData,
                sourceSampleRate = sampleRate,
                sourceChannels = channels,
                targetSampleRate = 44100,
                targetChannels = 2
            )

            return Signal.AudioSignal(
                origin = "AudioDecoder",
                rawData = convertedData,
                sampleRate = 44100,
                channels = 2,
                bitDepth = 16
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
            } catch (e: Exception) {
                println("Error cleaning up decoder/extractor: ${e.message}")
            }
        }
    }

    private fun convertToTargetFormat(
        pcmData: ByteArray,
        sourceSampleRate: Int,
        sourceChannels: Int,
        targetSampleRate: Int,
        targetChannels: Int
    ): ByteArray {
        var convertedData = pcmData

        // Convert sample rate if needed
        if (sourceSampleRate != targetSampleRate) {
            convertedData = resampleAudio(convertedData, sourceSampleRate, targetSampleRate, sourceChannels)
        }

        // Convert channels if needed
        if (sourceChannels != targetChannels) {
            convertedData = convertChannels(convertedData, sourceChannels, targetChannels)
        }

        return convertedData
    }

    private fun resampleAudio(
        pcmData: ByteArray,
        sourceSampleRate: Int,
        targetSampleRate: Int,
        channels: Int
    ): ByteArray {
        // Simple linear interpolation resampling
        val ratio = sourceSampleRate.toDouble() / targetSampleRate.toDouble()
        val bytesPerSample = 2 // 16-bit
        val frameSize = channels * bytesPerSample

        val sourceFrames = pcmData.size / frameSize
        val targetFrames = (sourceFrames / ratio).toInt()
        val targetData = ByteArray(targetFrames * frameSize)

        for (i in 0 until targetFrames) {
            val sourceIndex = (i * ratio).toInt()
            val sourceOffset = sourceIndex * frameSize
            val targetOffset = i * frameSize

            if (sourceOffset + frameSize <= pcmData.size) {
                System.arraycopy(pcmData, sourceOffset, targetData, targetOffset, frameSize)
            }
        }

        return targetData
    }

    private fun convertChannels(pcmData: ByteArray, sourceChannels: Int, targetChannels: Int): ByteArray {
        val bytesPerSample = 2 // 16-bit
        val sourceFrameSize = sourceChannels * bytesPerSample
        val targetFrameSize = targetChannels * bytesPerSample
        val frames = pcmData.size / sourceFrameSize

        val targetData = ByteArray(frames * targetFrameSize)

        for (i in 0 until frames) {
            val sourceOffset = i * sourceFrameSize
            val targetOffset = i * targetFrameSize

            when {
                sourceChannels == 1 && targetChannels == 2 -> {
                    // Mono to Stereo: duplicate mono channel
                    targetData[targetOffset] = pcmData[sourceOffset]
                    targetData[targetOffset + 1] = pcmData[sourceOffset + 1]
                    targetData[targetOffset + 2] = pcmData[sourceOffset]
                    targetData[targetOffset + 3] = pcmData[sourceOffset + 1]
                }
                sourceChannels == 2 && targetChannels == 1 -> {
                    // Stereo to Mono: average both channels
                    val left = (pcmData[sourceOffset].toInt() and 0xFF) or ((pcmData[sourceOffset + 1].toInt() and 0xFF) shl 8)
                    val right = (pcmData[sourceOffset + 2].toInt() and 0xFF) or ((pcmData[sourceOffset + 3].toInt() and 0xFF) shl 8)
                    val mono = ((left + right) / 2).toShort()
                    targetData[targetOffset] = (mono.toInt() and 0xFF).toByte()
                    targetData[targetOffset + 1] = ((mono.toInt() shr 8) and 0xFF).toByte()
                }
                else -> {
                    // Copy as-is or truncate/pad as needed
                    val copySize = minOf(sourceFrameSize, targetFrameSize)
                    System.arraycopy(pcmData, sourceOffset, targetData, targetOffset, copySize)
                }
            }
        }

        return targetData
    }

    actual suspend fun decodeAudioData(
        audioData: ByteArray,
        fileName: String,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? {
        TODO("Not yet implemented")
    }
}
