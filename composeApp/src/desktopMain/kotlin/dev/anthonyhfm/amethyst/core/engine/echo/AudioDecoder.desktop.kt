package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import javax.sound.sampled.*
import kotlin.math.min
import org.jflac.FLACDecoder
import org.jflac.PCMProcessor
import org.jflac.metadata.StreamInfo
import org.jflac.util.ByteData

actual object AudioDecoder {

    private val supportedFormats = listOf("wav", "mp3", "flac", "ogg", "m4a", "aac")

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

            val audioData = file.readBytes()
            return@withContext decodeAudioData(audioData, file.name, sampleStart, sampleEnd)

        } catch (e: Exception) {
            println("Failed to load audio file '$filePath': ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }

    actual suspend fun decodeAudioData(
        audioData: ByteArray,
        fileName: String,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? = withContext(Dispatchers.IO) {
        try {
            val extension = fileName.substringAfterLast('.', "").lowercase()

            val audioSignal = when (extension) {
                "wav" -> decodeWav(audioData, sampleStart, sampleEnd)
                "mp3" -> decodeMp3(audioData, sampleStart, sampleEnd)
                "flac" -> decodeFlac(audioData, sampleStart, sampleEnd)
                "ogg" -> decodeOgg(audioData, sampleStart, sampleEnd)
                "m4a", "aac" -> decodeM4a(audioData, sampleStart, sampleEnd)
                else -> {
                    println("Unsupported audio format: $extension")
                    null
                }
            }

            return@withContext audioSignal

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

    // WAV Decoder mit korrektem Sample-Cutting
    private fun decodeWav(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            val sourceFormat = audioInputStream.format

            val trimmedStream = if (sampleStart != null || sampleEnd != null) {
                trimAudioInputStream(audioInputStream, sourceFormat, sampleStart, sampleEnd)
            } else {
                audioInputStream
            }

            decodeFromAudioInputStream(trimmedStream)
        } catch (e: Exception) {
            println("Failed to decode WAV: ${e.message}")
            null
        }
    }

    // MP3 Decoder
    private fun decodeMp3(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            val sourceFormat = audioInputStream.format

            val trimmedStream = if (sampleStart != null || sampleEnd != null) {
                trimAudioInputStream(audioInputStream, sourceFormat, sampleStart, sampleEnd)
            } else {
                audioInputStream
            }

            decodeFromAudioInputStream(trimmedStream)
        } catch (e: Exception) {
            println("Failed to decode MP3: ${e.message}")
            null
        }
    }

    // FLAC Decoder
    private fun decodeFlac(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
        return try {
            decodeFlacWithJFLAC(audioData, sampleStart, sampleEnd)
        } catch (e: Exception) {
            println("Failed to decode FLAC with JFLAC: ${e.message}")
            try {
                val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
                val sourceFormat = audioInputStream.format

                val trimmedStream = if (sampleStart != null || sampleEnd != null) {
                    trimAudioInputStream(audioInputStream, sourceFormat, sampleStart, sampleEnd)
                } else {
                    audioInputStream
                }

                decodeFromAudioInputStream(trimmedStream)
            } catch (fallbackException: Exception) {
                println("Fallback FLAC decoding also failed: ${fallbackException.message}")
                null
            }
        }
    }

    // OGG Decoder
    private fun decodeOgg(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            val sourceFormat = audioInputStream.format

            val trimmedStream = if (sampleStart != null || sampleEnd != null) {
                trimAudioInputStream(audioInputStream, sourceFormat, sampleStart, sampleEnd)
            } else {
                audioInputStream
            }

            decodeFromAudioInputStream(trimmedStream)
        } catch (e: Exception) {
            println("Failed to decode OGG: ${e.message}")
            null
        }
    }

    // M4A/AAC Decoder
    private fun decodeM4a(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            val sourceFormat = audioInputStream.format

            val trimmedStream = if (sampleStart != null || sampleEnd != null) {
                trimAudioInputStream(audioInputStream, sourceFormat, sampleStart, sampleEnd)
            } else {
                audioInputStream
            }

            decodeFromAudioInputStream(trimmedStream)
        } catch (e: Exception) {
            println("Failed to decode M4A/AAC: ${e.message}")
            null
        }
    }

    /**
     * Trimme AudioInputStream mit der ursprünglichen Sample-Rate für korrekte Timings
     */
    private fun trimAudioInputStream(
        audioInputStream: AudioInputStream,
        sourceFormat: AudioFormat,
        sampleStart: Long?,
        sampleEnd: Long?
    ): AudioInputStream {
        val allData = audioInputStream.readAllBytes()
        audioInputStream.close()

        val bytesPerFrame = sourceFormat.frameSize
        val totalFrames = allData.size / bytesPerFrame

        val startFrame = (sampleStart ?: 0L).coerceAtLeast(0L)
        val endFrame = (sampleEnd ?: totalFrames.toLong()).coerceAtMost(totalFrames.toLong())

        if (startFrame >= endFrame || startFrame >= totalFrames) {
            return AudioInputStream(
                ByteArrayInputStream(ByteArray(0)),
                sourceFormat,
                0
            )
        }

        val startByte = (startFrame * bytesPerFrame).toInt()
        val endByte = (endFrame * bytesPerFrame).toInt()
        val trimmedData = allData.sliceArray(startByte until endByte)

        return AudioInputStream(
            ByteArrayInputStream(trimmedData),
            sourceFormat,
            trimmedData.size / bytesPerFrame.toLong()
        )
    }

    /**
     * FLAC Decoder mit JFLAC Library
     */
    private fun decodeFlacWithJFLAC(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
        val pcmProcessor = FlacPCMDataCollector()

        try {
            val decoder = FLACDecoder(ByteArrayInputStream(audioData))
            decoder.addPCMProcessor(pcmProcessor)
            decoder.decode()

            val streamInfo = pcmProcessor.getStreamInfo()
            val pcmData = pcmProcessor.getPCMData()

            if (streamInfo == null || pcmData.isEmpty()) {
                println("FLAC decoding failed: No stream info or PCM data")
                return null
            }

            // Trim audio BEFORE format conversion to maintain correct timing
            val trimmedData = if (sampleStart != null || sampleEnd != null) {
                trimAudioDataAtOriginalRate(pcmData, streamInfo.sampleRate, streamInfo.channels, streamInfo.bitsPerSample, sampleStart, sampleEnd)
            } else {
                pcmData
            }

            return convertFlacToTargetFormat(trimmedData, streamInfo)

        } catch (e: Exception) {
            println("JFLAC decoding error: ${e.message}")
            throw e
        }
    }

    /**
     * Custom PCM processor to collect FLAC audio data
     */
    private class FlacPCMDataCollector : PCMProcessor {
        private val pcmChunks = mutableListOf<ByteArray>()
        private var streamInfo: StreamInfo? = null

        override fun processStreamInfo(streamInfo: StreamInfo) {
            this.streamInfo = streamInfo
        }

        override fun processPCM(pcm: ByteData) {
            val chunk = ByteArray(pcm.len)
            System.arraycopy(pcm.data, 0, chunk, 0, pcm.len)
            pcmChunks.add(chunk)
        }

        fun getStreamInfo(): StreamInfo? = streamInfo

        fun getPCMData(): ByteArray {
            if (pcmChunks.isEmpty()) return ByteArray(0)

            val totalSize = pcmChunks.sumOf { it.size }
            val result = ByteArray(totalSize)
            var offset = 0

            for (chunk in pcmChunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.size)
                offset += chunk.size
            }

            return result
        }
    }

    /**
     * Trim audio data at the original sample rate
     */
    private fun trimAudioDataAtOriginalRate(
        audioData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        sampleStart: Long?,
        sampleEnd: Long?
    ): ByteArray {
        val bytesPerSample = (bitsPerSample / 8) * channels
        val totalSamples = audioData.size / bytesPerSample

        val startSample = (sampleStart ?: 0L).coerceAtLeast(0L)
        val endSample = (sampleEnd ?: totalSamples.toLong()).coerceAtMost(totalSamples.toLong())

        if (startSample >= endSample || startSample >= totalSamples) {
            return ByteArray(0)
        }

        val startByte = (startSample * bytesPerSample).toInt()
        val endByte = (endSample * bytesPerSample).toInt()
        return audioData.sliceArray(startByte until endByte)
    }

    /**
     * Convert FLAC PCM data to target format (44.1kHz, 16-bit, Stereo)
     */
    private fun convertFlacToTargetFormat(pcmData: ByteArray, streamInfo: StreamInfo): Signal.AudioSignal {
        val sourceSampleRate = streamInfo.sampleRate
        val sourceChannels = streamInfo.channels
        val sourceBitsPerSample = streamInfo.bitsPerSample

        val targetSampleRate = 44100
        val targetChannels = 2
        val targetBitsPerSample = 16

        var processedData = pcmData

        // Step 1: Convert bit depth if necessary
        if (sourceBitsPerSample != targetBitsPerSample) {
            processedData = convertBitDepth(
                processedData,
                sourceBitsPerSample,
                targetBitsPerSample,
                sourceChannels
            )
        }

        // Step 2: Convert channels (mono to stereo)
        if (sourceChannels == 1) {
            processedData = convertMonoToStereoFlac(processedData, targetBitsPerSample)
        }

        // Step 3: Resample if necessary
        if (sourceSampleRate != targetSampleRate) {
            processedData = resampleAudioImproved(
                processedData,
                sourceSampleRate,
                targetSampleRate,
                targetChannels,
                targetBitsPerSample
            )
        }

        return Signal.AudioSignal(
            origin = "AudioDecoder.FLAC",
            rawData = processedData,
            sampleRate = targetSampleRate,
            channels = targetChannels,
            bitDepth = targetBitsPerSample
        )
    }

    /**
     * Convert bit depth of audio samples
     */
    private fun convertBitDepth(data: ByteArray, sourceBits: Int, targetBits: Int, channels: Int): ByteArray {
        if (sourceBits == targetBits) return data

        val sourceBytesPerSample = sourceBits / 8
        val targetBytesPerSample = targetBits / 8
        val sampleCount = data.size / (sourceBytesPerSample * channels)
        val result = ByteArray(sampleCount * targetBytesPerSample * channels)

        for (i in 0 until sampleCount * channels) {
            val sourceOffset = i * sourceBytesPerSample
            val targetOffset = i * targetBytesPerSample

            when {
                sourceBits == 24 && targetBits == 16 -> {
                    result[targetOffset] = data[sourceOffset + 1]
                    result[targetOffset + 1] = data[sourceOffset + 2]
                }
                sourceBits == 32 && targetBits == 16 -> {
                    result[targetOffset] = data[sourceOffset + 2]
                    result[targetOffset + 1] = data[sourceOffset + 3]
                }
                sourceBits == 8 && targetBits == 16 -> {
                    val sample = (data[sourceOffset].toInt() and 0xFF) - 128
                    val sample16 = sample * 256
                    result[targetOffset] = (sample16 and 0xFF).toByte()
                    result[targetOffset + 1] = ((sample16 shr 8) and 0xFF).toByte()
                }
                else -> {
                    val copyBytes = min(sourceBytesPerSample, targetBytesPerSample)
                    System.arraycopy(data, sourceOffset, result, targetOffset, copyBytes)
                }
            }
        }

        return result
    }

    /**
     * Convert mono to stereo for FLAC
     */
    private fun convertMonoToStereoFlac(data: ByteArray, bitsPerSample: Int): ByteArray {
        val bytesPerSample = bitsPerSample / 8
        val sampleCount = data.size / bytesPerSample
        val result = ByteArray(data.size * 2)

        for (i in 0 until sampleCount) {
            val sourceOffset = i * bytesPerSample
            val leftOffset = i * 2 * bytesPerSample
            val rightOffset = leftOffset + bytesPerSample

            System.arraycopy(data, sourceOffset, result, leftOffset, bytesPerSample)
            System.arraycopy(data, sourceOffset, result, rightOffset, bytesPerSample)
        }

        return result
    }

    /**
     * Verbesserte Resampling-Funktion mit linearer Interpolation
     */
    private fun resampleAudioImproved(
        data: ByteArray,
        sourceSampleRate: Int,
        targetSampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        if (sourceSampleRate == targetSampleRate) return data

        val bytesPerSample = bitsPerSample / 8
        val bytesPerFrame = bytesPerSample * channels
        val sourceFrames = data.size / bytesPerFrame
        val targetFrames = (sourceFrames.toLong() * targetSampleRate / sourceSampleRate).toInt()
        val result = ByteArray(targetFrames * bytesPerFrame)

        val ratio = sourceFrames.toDouble() / targetFrames.toDouble()

        for (i in 0 until targetFrames) {
            val sourceFloat = i * ratio
            val sourceIndex = sourceFloat.toInt()
            val fraction = sourceFloat - sourceIndex

            val sourceOffset = sourceIndex * bytesPerFrame
            val targetOffset = i * bytesPerFrame

            if (sourceOffset + bytesPerFrame <= data.size) {
                if (fraction < 0.001 || sourceIndex >= sourceFrames - 1) {
                    System.arraycopy(data, sourceOffset, result, targetOffset, bytesPerFrame)
                } else {
                    val nextOffset = (sourceIndex + 1) * bytesPerFrame
                    if (nextOffset + bytesPerFrame <= data.size) {
                        for (c in 0 until channels) {
                            for (b in 0 until bytesPerSample) {
                                val byteIndex = c * bytesPerSample + b
                                val sample1 = data[sourceOffset + byteIndex].toInt() and 0xFF
                                val sample2 = data[nextOffset + byteIndex].toInt() and 0xFF
                                val interpolated = (sample1 + (sample2 - sample1) * fraction).toInt()
                                result[targetOffset + byteIndex] = interpolated.toByte()
                            }
                        }
                    } else {
                        System.arraycopy(data, sourceOffset, result, targetOffset, bytesPerFrame)
                    }
                }
            }
        }

        return result
    }

    /**
     * Common decoder for AudioInputStream
     */
    private fun decodeFromAudioInputStream(audioInputStream: AudioInputStream): Signal.AudioSignal? {
        return try {
            val sourceFormat = audioInputStream.format

            val targetFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100f,
                16,
                2,
                4,
                44100f,
                false
            )

            val convertedStream = if (AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
                AudioSystem.getAudioInputStream(targetFormat, audioInputStream)
            } else {
                convertToTargetFormat(audioInputStream, targetFormat)
            }

            val pcmData = convertedStream.readAllBytes()
            convertedStream.close()
            audioInputStream.close()

            val processedData = ensureLittleEndian(pcmData, targetFormat)

            Signal.AudioSignal(
                origin = "AudioDecoder",
                rawData = processedData,
                sampleRate = targetFormat.sampleRate.toInt(),
                channels = targetFormat.channels,
                bitDepth = targetFormat.sampleSizeInBits
            )

        } catch (e: Exception) {
            println("Failed to decode audio stream: ${e.message}")
            null
        }
    }

    private fun convertToTargetFormat(audioInputStream: AudioInputStream, targetFormat: AudioFormat): AudioInputStream {
        var currentStream = audioInputStream
        val sourceFormat = audioInputStream.format

        // Step 1: Convert encoding if needed
        if (sourceFormat.encoding != AudioFormat.Encoding.PCM_SIGNED) {
            val pcmFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.sampleRate,
                16,
                sourceFormat.channels,
                sourceFormat.channels * 2,
                sourceFormat.sampleRate,
                false
            )

            if (AudioSystem.isConversionSupported(pcmFormat, sourceFormat)) {
                currentStream = AudioSystem.getAudioInputStream(pcmFormat, currentStream)
            }
        }

        // Step 2: Convert sample rate if needed
        val currentFormat = currentStream.format
        if (currentFormat.sampleRate != targetFormat.sampleRate) {
            val sampleRateFormat = AudioFormat(
                currentFormat.encoding,
                targetFormat.sampleRate,
                currentFormat.sampleSizeInBits,
                currentFormat.channels,
                currentFormat.frameSize,
                targetFormat.sampleRate,
                currentFormat.isBigEndian
            )

            if (AudioSystem.isConversionSupported(sampleRateFormat, currentFormat)) {
                currentStream = AudioSystem.getAudioInputStream(sampleRateFormat, currentStream)
            }
        }

        // Step 3: Convert channels if needed
        val currentFormat2 = currentStream.format
        if (currentFormat2.channels != 2) {
            val stereoFormat = AudioFormat(
                currentFormat2.encoding,
                currentFormat2.sampleRate,
                currentFormat2.sampleSizeInBits,
                2,
                4,
                currentFormat2.frameRate,
                currentFormat2.isBigEndian
            )

            if (AudioSystem.isConversionSupported(stereoFormat, currentFormat2)) {
                currentStream = AudioSystem.getAudioInputStream(stereoFormat, currentStream)
            } else {
                currentStream = convertMonoToStereo(currentStream)
            }
        }

        // Step 4: Convert to final format
        val currentFormat3 = currentStream.format
        if (AudioSystem.isConversionSupported(targetFormat, currentFormat3)) {
            currentStream = AudioSystem.getAudioInputStream(targetFormat, currentStream)
        }

        return currentStream
    }

    private fun convertMonoToStereo(monoStream: AudioInputStream): AudioInputStream {
        val monoFormat = monoStream.format
        val stereoFormat = AudioFormat(
            monoFormat.encoding,
            monoFormat.sampleRate,
            monoFormat.sampleSizeInBits,
            2,
            4,
            monoFormat.frameRate,
            monoFormat.isBigEndian
        )

        val monoData = monoStream.readAllBytes()
        val stereoData = ByteArray(monoData.size * 2)

        for (i in monoData.indices step 2) {
            stereoData[i * 2] = monoData[i]
            stereoData[i * 2 + 1] = monoData[i + 1]
            stereoData[i * 2 + 2] = monoData[i]
            stereoData[i * 2 + 3] = monoData[i + 1]
        }

        return AudioInputStream(
            ByteArrayInputStream(stereoData),
            stereoFormat,
            stereoData.size / stereoFormat.frameSize.toLong()
        )
    }

    private fun ensureLittleEndian(data: ByteArray, format: AudioFormat): ByteArray {
        if (!format.isBigEndian) {
            return data
        }

        val result = ByteArray(data.size)
        val sampleSize = format.sampleSizeInBits / 8

        for (i in data.indices step sampleSize) {
            for (j in 0 until sampleSize) {
                result[i + j] = data[i + sampleSize - 1 - j]
            }
        }

        return result
    }
}
