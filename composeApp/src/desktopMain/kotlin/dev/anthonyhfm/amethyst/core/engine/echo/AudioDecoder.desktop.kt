package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
                "wav" -> decodeWav(audioData)
                "mp3" -> decodeMp3(audioData)
                "flac" -> decodeFlac(audioData)
                "ogg" -> decodeOgg(audioData)
                "m4a", "aac" -> decodeM4a(audioData)
                else -> {
                    println("Unsupported audio format: $extension")
                    null
                }
            }

            // Apply sample trimming if specified
            return@withContext audioSignal?.let { signal ->
                if (sampleStart != null || sampleEnd != null) {
                    trimAudioSignal(signal, sampleStart, sampleEnd)
                } else {
                    signal
                }
            }

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

    // WAV Decoder
    private fun decodeWav(audioData: ByteArray): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            decodeFromAudioInputStream(audioInputStream)
        } catch (e: Exception) {
            println("Failed to decode WAV: ${e.message}")
            null
        }
    }

    // MP3 Decoder
    private fun decodeMp3(audioData: ByteArray): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            decodeFromAudioInputStream(audioInputStream)
        } catch (e: Exception) {
            println("Failed to decode MP3: ${e.message}")
            null
        }
    }

    // FLAC Decoder using JFLAC library
    private fun decodeFlac(audioData: ByteArray): Signal.AudioSignal? {
        return try {
            // Try JFLAC first for better FLAC support
            decodeFlacWithJFLAC(audioData)
        } catch (e: Exception) {
            println("Failed to decode FLAC with JFLAC: ${e.message}")
            // Fallback to AudioSystem approach
            try {
                val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
                decodeFromAudioInputStream(audioInputStream)
            } catch (fallbackException: Exception) {
                println("Fallback FLAC decoding also failed: ${fallbackException.message}")
                null
            }
        }
    }

    /**
     * Decode FLAC using JFLAC library for better compatibility
     */
    private fun decodeFlacWithJFLAC(audioData: ByteArray): Signal.AudioSignal? {
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

            // Convert FLAC data to target format (44.1kHz, 16-bit, Stereo)
            return convertFlacToTargetFormat(pcmData, streamInfo)

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
            // Copy the PCM data chunk
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
     * Convert FLAC PCM data to target format (44.1kHz, 16-bit, Stereo)
     */
    private fun convertFlacToTargetFormat(pcmData: ByteArray, streamInfo: StreamInfo): Signal.AudioSignal {
        val sourceSampleRate = streamInfo.sampleRate
        val sourceChannels = streamInfo.channels
        val sourceBitsPerSample = streamInfo.bitsPerSample

        println("FLAC source format: ${sourceSampleRate}Hz, ${sourceChannels}ch, ${sourceBitsPerSample}bit")

        // Target format
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
        if (sourceChannels == 1 && targetChannels == 2) {
            processedData = convertMonoToStereoFlac(processedData, targetBitsPerSample)
        }

        // Step 3: Resample if necessary
        if (sourceSampleRate != targetSampleRate) {
            processedData = resampleAudio(
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
                    // 24-bit to 16-bit: take the upper 16 bits
                    result[targetOffset] = data[sourceOffset + 1]
                    result[targetOffset + 1] = data[sourceOffset + 2]
                }
                sourceBits == 32 && targetBits == 16 -> {
                    // 32-bit to 16-bit: take the upper 16 bits
                    result[targetOffset] = data[sourceOffset + 2]
                    result[targetOffset + 1] = data[sourceOffset + 3]
                }
                sourceBits == 8 && targetBits == 16 -> {
                    // 8-bit to 16-bit
                    val sample = (data[sourceOffset].toInt() and 0xFF) - 128
                    val sample16 = sample * 256
                    result[targetOffset] = (sample16 and 0xFF).toByte()
                    result[targetOffset + 1] = ((sample16 shr 8) and 0xFF).toByte()
                }
                else -> {
                    // Default: copy what fits
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

            // Copy sample to both left and right channels
            System.arraycopy(data, sourceOffset, result, leftOffset, bytesPerSample)
            System.arraycopy(data, sourceOffset, result, rightOffset, bytesPerSample)
        }

        return result
    }

    /**
     * Simple linear interpolation resampling
     */
    private fun resampleAudio(
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
            val sourceIndex = (i * ratio).toInt()
            val sourceOffset = sourceIndex * bytesPerFrame
            val targetOffset = i * bytesPerFrame

            if (sourceOffset + bytesPerFrame <= data.size) {
                System.arraycopy(data, sourceOffset, result, targetOffset, bytesPerFrame)
            }
        }

        return result
    }

    // OGG Decoder
    private fun decodeOgg(audioData: ByteArray): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            decodeFromAudioInputStream(audioInputStream)
        } catch (e: Exception) {
            println("Failed to decode OGG: ${e.message}")
            null
        }
    }

    // M4A/AAC Decoder
    private fun decodeM4a(audioData: ByteArray): Signal.AudioSignal? {
        return try {
            // For M4A/AAC, we need to use a different approach since Java's AudioSystem doesn't natively support it
            // We'll try to use the AudioSystem first and fall back to custom decoding if needed
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            decodeFromAudioInputStream(audioInputStream)
        } catch (e: Exception) {
            println("Failed to decode M4A/AAC: ${e.message}")
            null
        }
    }

    // Common decoder for AudioInputStream
    private fun decodeFromAudioInputStream(audioInputStream: AudioInputStream): Signal.AudioSignal? {
        return try {
            val sourceFormat = audioInputStream.format

            // Target format: 44.1kHz, 16-bit, Stereo, Little Endian
            val targetFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100f,
                16,
                2,
                4,
                44100f,
                false
            )

            // Convert to target format if necessary
            val convertedStream = if (AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
                AudioSystem.getAudioInputStream(targetFormat, audioInputStream)
            } else {
                // If direct conversion isn't supported, try step-by-step conversion
                convertToTargetFormat(audioInputStream, targetFormat)
            }

            // Read all PCM data
            val pcmData = convertedStream.readAllBytes()
            convertedStream.close()
            audioInputStream.close()

            // Ensure proper byte order (little endian for OpenAL)
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
                // Manual mono to stereo conversion
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
            return data // Already little endian
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

    /**
     * Trim audio signal to specified sample range
     */
    private fun trimAudioSignal(
        signal: Signal.AudioSignal,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal {
        val bytesPerSample = (signal.bitDepth / 8) * signal.channels
        val totalSamples = signal.rawData!!.size / bytesPerSample

        val startSample = (sampleStart ?: 0L).coerceAtLeast(0L)
        val endSample = (sampleEnd ?: totalSamples.toLong()).coerceAtMost(totalSamples.toLong())

        if (startSample >= endSample || startSample >= totalSamples) {
            // Return empty audio if invalid range
            return Signal.AudioSignal(
                origin = signal.origin,
                rawData = ByteArray(0),
                sampleRate = signal.sampleRate,
                channels = signal.channels,
                bitDepth = signal.bitDepth
            )
        }

        val startByte = (startSample * bytesPerSample).toInt()
        val endByte = (endSample * bytesPerSample).toInt()
        val trimmedData = signal.rawData.sliceArray(startByte until endByte)

        return Signal.AudioSignal(
            origin = signal.origin,
            rawData = trimmedData,
            sampleRate = signal.sampleRate,
            channels = signal.channels,
            bitDepth = signal.bitDepth
        )
    }
}
