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

    private val supportedFormats = listOf("wav", "mp3", "flac", "ogg", "m4a", "aac", "aif", "aiff")

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
                "aif", "aiff" -> decodeAif(audioData, sampleStart, sampleEnd)
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

    actual fun getSupportedFormats(): List<String> = supportedFormats.toList()

    actual fun isFormatSupported(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in supportedFormats
    }

    private fun decodeWav(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            val sourceFormat = audioInputStream.format

            if (sourceFormat.sampleSizeInBits == 32) {
                return decodeWavWith32BitSupport(audioData, sourceFormat, sampleStart, sampleEnd)
            }

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

    private fun decodeWavWith32BitSupport(
        audioData: ByteArray,
        sourceFormat: AudioFormat,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            val rawData = audioInputStream.readAllBytes()
            audioInputStream.close()

            val trimmedData = if (sampleStart != null || sampleEnd != null) {
                trimRawAudioData(rawData, sourceFormat, sampleStart, sampleEnd)
            } else {
                rawData
            }

            val convertedData = convert32BitTo16BitImproved(trimmedData, sourceFormat)

            val targetFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.sampleRate,
                16,
                sourceFormat.channels,
                sourceFormat.channels * 2,
                sourceFormat.sampleRate,
                false
            )

            val bytesPerSample = 2 * sourceFormat.channels
            val totalSamples = convertedData.size / bytesPerSample
            val durationMs = (totalSamples * 1000L) / sourceFormat.sampleRate.toInt()

            Signal.AudioSignal(
                origin = "AudioDecoder.WAV32",
                rawData = convertedData,
                sampleRate = sourceFormat.sampleRate.toInt(),
                channels = sourceFormat.channels,
                bitDepth = 16,
                durationMs = durationMs
            )

        } catch (e: Exception) {
            println("32-bit WAV conversion failed: ${e.message}")
            null
        }
    }

    private fun trimRawAudioData(
        audioData: ByteArray,
        format: AudioFormat,
        sampleStart: Long?,
        sampleEnd: Long?
    ): ByteArray {
        if (sampleStart == null && sampleEnd == null) return audioData

        val bytesPerFrame = format.frameSize
        val totalFrames = audioData.size / bytesPerFrame

        val startFrame = (sampleStart ?: 0L).coerceAtLeast(0L)
        val endFrame = (sampleEnd ?: totalFrames.toLong()).coerceAtMost(totalFrames.toLong())

        if (startFrame >= endFrame || startFrame >= totalFrames) {
            return ByteArray(0)
        }

        val startByte = (startFrame * bytesPerFrame).toInt()
        val endByte = (endFrame * bytesPerFrame).toInt()

        return audioData.sliceArray(startByte until endByte)
    }

    private fun convert32BitTo16BitImproved(data: ByteArray, sourceFormat: AudioFormat): ByteArray {
        val channels = sourceFormat.channels
        val sourceBytesPerSample = 4
        val targetBytesPerSample = 2
        val sampleCount = data.size / (sourceBytesPerSample * channels)

        val result = ByteArray(sampleCount * targetBytesPerSample * channels)

        for (i in 0 until sampleCount * channels) {
            val sourceOffset = i * sourceBytesPerSample
            val targetOffset = i * targetBytesPerSample

            val sample32 = if (sourceFormat.encoding == AudioFormat.Encoding.PCM_FLOAT) {
                convertFloat32ToInt16(data, sourceOffset)
            } else {
                convertInt32ToInt16(data, sourceOffset, sourceFormat.isBigEndian)
            }

            result[targetOffset] = (sample32 and 0xFF).toByte()
            result[targetOffset + 1] = ((sample32 shr 8) and 0xFF).toByte()
        }

        return result
    }

    private fun convertFloat32ToInt16(data: ByteArray, offset: Int): Int {
        val intBits = (data[offset].toInt() and 0xFF) or
                     ((data[offset + 1].toInt() and 0xFF) shl 8) or
                     ((data[offset + 2].toInt() and 0xFF) shl 16) or
                     ((data[offset + 3].toInt() and 0xFF) shl 24)

        val floatValue = Float.fromBits(intBits)
        val clampedValue = floatValue.coerceIn(-1.0f, 1.0f)
        val sample16 = (clampedValue * 32767.0f).toInt()

        return sample16.coerceIn(-32768, 32767)
    }

    private fun convertInt32ToInt16(data: ByteArray, offset: Int, isBigEndian: Boolean): Int {
        val sample32 = if (isBigEndian) {
            ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
        } else {
            (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
        }

        val sample16 = (sample32 shr 16)
        val dither = (sample32 and 0xFFFF) shr 15

        return (sample16 + dither).coerceIn(-32768, 32767)
    }

    private fun decodeMp3(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            val sourceFormat = audioInputStream.format

            if (sourceFormat.sampleSizeInBits <= 0) {
                return decodeMp3WithForceConversion(audioData, sampleStart, sampleEnd)
            }

            val trimmedStream = if (sampleStart != null || sampleEnd != null) {
                trimAudioInputStream(audioInputStream, sourceFormat, sampleStart, sampleEnd)
            } else {
                audioInputStream
            }

            decodeFromAudioInputStream(trimmedStream)
        } catch (e: Exception) {
            decodeMp3WithForceConversion(audioData, sampleStart, sampleEnd)
        }
    }

    private fun decodeMp3WithForceConversion(audioData: ByteArray, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? {
        return try {
            val audioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioData))
            val sourceFormat = audioInputStream.format
            val targetFormat = createRobustTargetFormat(sourceFormat)
            val convertedStream = performMultiStageConversion(audioInputStream, targetFormat)

            val trimmedStream = if (sampleStart != null || sampleEnd != null) {
                trimAudioInputStream(convertedStream, convertedStream.format, sampleStart, sampleEnd)
            } else {
                convertedStream
            }

            decodeFromAudioInputStream(trimmedStream)

        } catch (e: Exception) {
            println("Force conversion failed: ${e.message}")
            null
        }
    }

    private fun createRobustTargetFormat(sourceFormat: AudioFormat): AudioFormat {
        val sampleRate = when {
            sourceFormat.sampleRate <= 0 || sourceFormat.sampleRate.isNaN() -> 44100f
            else -> sourceFormat.sampleRate
        }

        val channels = when {
            sourceFormat.channels <= 0 -> 2
            sourceFormat.channels == 1 -> 2
            sourceFormat.channels > 2 -> 2
            else -> sourceFormat.channels
        }

        return AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            16,
            channels,
            channels * 2,
            sampleRate,
            false
        )
    }

    private fun performMultiStageConversion(sourceStream: AudioInputStream, targetFormat: AudioFormat): AudioInputStream {
        var currentStream = sourceStream
        val sourceFormat = sourceStream.format

        try {
            if (sourceFormat.encoding != AudioFormat.Encoding.PCM_SIGNED) {
                val intermediateFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.sampleRate,
                    16,
                    sourceFormat.channels,
                    sourceFormat.channels * 2,
                    sourceFormat.sampleRate,
                    false
                )

                if (AudioSystem.isConversionSupported(intermediateFormat, sourceFormat)) {
                    currentStream = AudioSystem.getAudioInputStream(intermediateFormat, currentStream)
                } else {
                    currentStream = convertToValidPCMManually(currentStream)
                }
            }

            val currentFormat = currentStream.format
            if (currentFormat.channels != targetFormat.channels) {
                val channelFormat = AudioFormat(
                    currentFormat.encoding,
                    currentFormat.sampleRate,
                    currentFormat.sampleSizeInBits,
                    targetFormat.channels,
                    targetFormat.channels * (currentFormat.sampleSizeInBits / 8),
                    currentFormat.sampleRate,
                    currentFormat.isBigEndian
                )

                if (AudioSystem.isConversionSupported(channelFormat, currentFormat)) {
                    currentStream = AudioSystem.getAudioInputStream(channelFormat, currentStream)
                } else {
                    currentStream = convertChannelsManually(currentStream, targetFormat.channels)
                }
            }

            val finalFormat = currentStream.format
            if (!formatsAreEquivalent(finalFormat, targetFormat)) {
                if (AudioSystem.isConversionSupported(targetFormat, finalFormat)) {
                    currentStream = AudioSystem.getAudioInputStream(targetFormat, currentStream)
                }
            }

            return currentStream

        } catch (e: Exception) {
            return sourceStream
        }
    }

    private fun convertToValidPCMManually(audioInputStream: AudioInputStream): AudioInputStream {
        return try {
            val sourceFormat = audioInputStream.format
            val data = audioInputStream.readAllBytes()
            audioInputStream.close()

            val pcmFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                if (sourceFormat.sampleRate > 0) sourceFormat.sampleRate else 44100f,
                16,
                if (sourceFormat.channels > 0) sourceFormat.channels else 2,
                if (sourceFormat.channels > 0) sourceFormat.channels * 2 else 4,
                if (sourceFormat.sampleRate > 0) sourceFormat.sampleRate else 44100f,
                false
            )

            val processedData = if (data.size % pcmFormat.frameSize == 0) {
                data
            } else {
                val validFrames = data.size / pcmFormat.frameSize
                val validSize = validFrames * pcmFormat.frameSize
                data.sliceArray(0 until validSize)
            }

            AudioInputStream(
                ByteArrayInputStream(processedData),
                pcmFormat,
                processedData.size / pcmFormat.frameSize.toLong()
            )

        } catch (e: Exception) {
            audioInputStream
        }
    }

    private fun convertChannelsManually(audioInputStream: AudioInputStream, targetChannels: Int): AudioInputStream {
        return try {
            val sourceFormat = audioInputStream.format
            val sourceData = audioInputStream.readAllBytes()
            audioInputStream.close()

            if (sourceFormat.channels == targetChannels) {
                return AudioInputStream(
                    ByteArrayInputStream(sourceData),
                    sourceFormat,
                    sourceData.size / sourceFormat.frameSize.toLong()
                )
            }

            val bytesPerSample = sourceFormat.sampleSizeInBits / 8
            val sourceChannels = sourceFormat.channels
            val sourceFrames = sourceData.size / (bytesPerSample * sourceChannels)

            val targetData = ByteArray(sourceFrames * bytesPerSample * targetChannels)

            for (frame in 0 until sourceFrames) {
                val sourceFrameOffset = frame * bytesPerSample * sourceChannels
                val targetFrameOffset = frame * bytesPerSample * targetChannels

                when {
                    sourceChannels == 1 && targetChannels == 2 -> {
                        System.arraycopy(sourceData, sourceFrameOffset, targetData, targetFrameOffset, bytesPerSample)
                        System.arraycopy(sourceData, sourceFrameOffset, targetData, targetFrameOffset + bytesPerSample, bytesPerSample)
                    }
                    sourceChannels > targetChannels -> {
                        for (ch in 0 until targetChannels) {
                            System.arraycopy(
                                sourceData,
                                sourceFrameOffset + ch * bytesPerSample,
                                targetData,
                                targetFrameOffset + ch * bytesPerSample,
                                bytesPerSample
                            )
                        }
                    }
                    else -> {
                        val copyChannels = minOf(sourceChannels, targetChannels)
                        for (ch in 0 until copyChannels) {
                            System.arraycopy(
                                sourceData,
                                sourceFrameOffset + ch * bytesPerSample,
                                targetData,
                                targetFrameOffset + ch * bytesPerSample,
                                bytesPerSample
                            )
                        }
                    }
                }
            }

            val targetFormat = AudioFormat(
                sourceFormat.encoding,
                sourceFormat.sampleRate,
                sourceFormat.sampleSizeInBits,
                targetChannels,
                targetChannels * bytesPerSample,
                sourceFormat.sampleRate,
                sourceFormat.isBigEndian
            )

            AudioInputStream(
                ByteArrayInputStream(targetData),
                targetFormat,
                targetData.size / targetFormat.frameSize.toLong()
            )

        } catch (e: Exception) {
            audioInputStream
        }
    }

    private fun formatsAreEquivalent(format1: AudioFormat, format2: AudioFormat): Boolean {
        return format1.encoding == format2.encoding &&
                format1.sampleRate == format2.sampleRate &&
                format1.sampleSizeInBits == format2.sampleSizeInBits &&
                format1.channels == format2.channels &&
                format1.isBigEndian == format2.isBigEndian
    }

    private fun decodeFlac(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
        return try {
            decodeFlacWithJFLAC(audioData, sampleStart, sampleEnd)
        } catch (e: Exception) {
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
                null
            }
        }
    }

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
            null
        }
    }

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
            null
        }
    }

    private fun decodeAif(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
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
            null
        }
    }

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

    private fun decodeFlacWithJFLAC(audioData: ByteArray, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal? {
        val pcmProcessor = FlacPCMDataCollector()

        try {
            val decoder = FLACDecoder(ByteArrayInputStream(audioData))
            decoder.addPCMProcessor(pcmProcessor)
            decoder.decode()

            val streamInfo = pcmProcessor.getStreamInfo()
            val pcmData = pcmProcessor.getPCMData()

            if (streamInfo == null || pcmData.isEmpty()) {
                return null
            }

            val trimmedData = if (sampleStart != null || sampleEnd != null) {
                trimAudioDataAtOriginalRate(pcmData, streamInfo.sampleRate, streamInfo.channels, streamInfo.bitsPerSample, sampleStart, sampleEnd)
            } else {
                pcmData
            }

            val targetSampleRate = 44100
            val targetChannels = 2
            val targetBitsPerSample = 16

            var processedData = trimmedData

            if (streamInfo.bitsPerSample != targetBitsPerSample) {
                processedData = convertBitDepth(
                    processedData,
                    streamInfo.bitsPerSample,
                    targetBitsPerSample,
                    streamInfo.channels
                )
            }

            if (streamInfo.channels == 1) {
                processedData = convertMonoToStereoFlac(processedData, targetBitsPerSample)
            }

            if (streamInfo.sampleRate != targetSampleRate) {
                processedData = resampleAudioImproved(
                    processedData,
                    streamInfo.sampleRate,
                    targetSampleRate,
                    targetChannels,
                    targetBitsPerSample
                )
            }

            val bytesPerSample = (targetBitsPerSample / 8) * targetChannels
            val totalSamples = processedData.size / bytesPerSample
            val durationMs = (totalSamples * 1000L) / targetSampleRate

            return Signal.AudioSignal(
                origin = "AudioDecoder.FLAC",
                rawData = processedData,
                sampleRate = targetSampleRate,
                channels = targetChannels,
                bitDepth = targetBitsPerSample,
                durationMs = durationMs
            )

        } catch (e: Exception) {
            throw e
        }
    }

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
                    val sample24 = ((data[sourceOffset + 2].toInt() and 0xFF) shl 16) or
                                  ((data[sourceOffset + 1].toInt() and 0xFF) shl 8) or
                                  (data[sourceOffset].toInt() and 0xFF)
                    val sample16 = (sample24 shr 8).toShort()
                    result[targetOffset] = (sample16.toInt() and 0xFF).toByte()
                    result[targetOffset + 1] = ((sample16.toInt() shr 8) and 0xFF).toByte()
                }
                sourceBits == 32 && targetBits == 16 -> {
                    val sample32 = ((data[sourceOffset + 3].toInt() and 0xFF) shl 24) or
                                  ((data[sourceOffset + 2].toInt() and 0xFF) shl 16) or
                                  ((data[sourceOffset + 1].toInt() and 0xFF) shl 8) or
                                  (data[sourceOffset].toInt() and 0xFF)
                    val sample16 = (sample32 shr 16).toShort()
                    result[targetOffset] = (sample16.toInt() and 0xFF).toByte()
                    result[targetOffset + 1] = ((sample16.toInt() shr 8) and 0xFF).toByte()
                }
                sourceBits == 8 && targetBits == 16 -> {
                    val sample8 = (data[sourceOffset].toInt() and 0xFF) - 128
                    val sample16 = sample8 * 256
                    result[targetOffset] = (sample16 and 0xFF).toByte()
                    result[targetOffset + 1] = ((sample16 shr 8) and 0xFF).toByte()
                }
                else -> {
                    val copyBytes = min(sourceBytesPerSample, targetBytesPerSample)
                    System.arraycopy(data, sourceOffset, result, targetOffset, copyBytes)
                    if (targetBytesPerSample > sourceBytesPerSample) {
                        for (j in copyBytes until targetBytesPerSample) {
                            result[targetOffset + j] = 0
                        }
                    }
                }
            }
        }

        return result
    }

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

    private fun decodeFromAudioInputStream(audioInputStream: AudioInputStream): Signal.AudioSignal? {
        return try {
            val sourceFormat = audioInputStream.format
            val targetFormat = determineOptimalFormat(sourceFormat)

            val convertedStream = if (AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
                AudioSystem.getAudioInputStream(targetFormat, audioInputStream)
            } else {
                convertToTargetFormatSafely(audioInputStream, targetFormat)
            }

            val pcmData = convertedStream.readAllBytes()
            convertedStream.close()
            audioInputStream.close()

            val processedData = normalizeAudioDataForOpenAL(pcmData, targetFormat)

            val sampleRate = targetFormat.sampleRate.toInt()
            val channels = targetFormat.channels
            val bitDepth = targetFormat.sampleSizeInBits
            val bytesPerSample = (bitDepth / 8) * channels
            val totalSamples = processedData.size / bytesPerSample
            val durationMs = (totalSamples * 1000L) / sampleRate

            Signal.AudioSignal(
                origin = "AudioDecoder",
                rawData = processedData,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth,
                durationMs = durationMs
            )

        } catch (e: Exception) {
            null
        }
    }

    private fun determineOptimalFormat(sourceFormat: AudioFormat): AudioFormat {
        val targetSampleRate = when {
            sourceFormat.sampleRate <= 0 || sourceFormat.sampleRate.isNaN() -> 44100f
            sourceFormat.sampleRate <= 22050f -> 22050f
            sourceFormat.sampleRate <= 44100f -> 44100f
            sourceFormat.sampleRate <= 48000f -> 48000f
            sourceFormat.sampleRate <= 96000f -> 48000f
            else -> 44100f
        }

        val targetChannels = when {
            sourceFormat.channels <= 0 -> 2
            sourceFormat.channels == 1 -> 2
            sourceFormat.channels > 2 -> 2
            else -> sourceFormat.channels
        }

        return AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            targetSampleRate,
            16,
            targetChannels,
            targetChannels * 2,
            targetSampleRate,
            false
        )
    }

    private fun convertToTargetFormatSafely(audioInputStream: AudioInputStream, targetFormat: AudioFormat): AudioInputStream {
        var currentStream = audioInputStream
        val sourceFormat = audioInputStream.format

        try {
            if (sourceFormat.encoding != AudioFormat.Encoding.PCM_SIGNED) {
                val pcmFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.sampleRate,
                    maxOf(sourceFormat.sampleSizeInBits, 16),
                    sourceFormat.channels,
                    sourceFormat.channels * maxOf(sourceFormat.sampleSizeInBits, 16) / 8,
                    sourceFormat.sampleRate,
                    false
                )

                if (AudioSystem.isConversionSupported(pcmFormat, sourceFormat)) {
                    currentStream = AudioSystem.getAudioInputStream(pcmFormat, currentStream)
                }
            }

            val currentFormat = currentStream.format
            if (AudioSystem.isConversionSupported(targetFormat, currentFormat)) {
                currentStream = AudioSystem.getAudioInputStream(targetFormat, currentStream)
            } else {
                currentStream = convertChannelsSafely(currentStream, targetFormat.channels)
                currentStream = convertSampleRateSafely(currentStream, targetFormat.sampleRate)
            }

            return currentStream

        } catch (e: Exception) {
            return audioInputStream
        }
    }

    private fun convertChannelsSafely(audioInputStream: AudioInputStream, targetChannels: Int): AudioInputStream {
        val currentFormat = audioInputStream.format

        if (currentFormat.channels == targetChannels) {
            return audioInputStream
        }

        val newFormat = AudioFormat(
            currentFormat.encoding,
            currentFormat.sampleRate,
            currentFormat.sampleSizeInBits,
            targetChannels,
            targetChannels * (currentFormat.sampleSizeInBits / 8),
            currentFormat.frameRate,
            currentFormat.isBigEndian
        )

        return if (AudioSystem.isConversionSupported(newFormat, currentFormat)) {
            AudioSystem.getAudioInputStream(newFormat, audioInputStream)
        } else {
            audioInputStream
        }
    }

    private fun convertSampleRateSafely(audioInputStream: AudioInputStream, targetSampleRate: Float): AudioInputStream {
        val currentFormat = audioInputStream.format

        if (currentFormat.sampleRate == targetSampleRate) {
            return audioInputStream
        }

        val newFormat = AudioFormat(
            currentFormat.encoding,
            targetSampleRate,
            currentFormat.sampleSizeInBits,
            currentFormat.channels,
            currentFormat.frameSize,
            targetSampleRate,
            currentFormat.isBigEndian
        )

        return if (AudioSystem.isConversionSupported(newFormat, currentFormat)) {
            AudioSystem.getAudioInputStream(newFormat, audioInputStream)
        } else {
            audioInputStream
        }
    }

    private fun normalizeAudioDataForOpenAL(data: ByteArray, format: AudioFormat): ByteArray {
        return if (format.isBigEndian && format.sampleSizeInBits == 16) {
            swapEndianness16Bit(data)
        } else {
            data
        }
    }

    private fun swapEndianness16Bit(data: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (i in data.indices step 2) {
            if (i + 1 < data.size) {
                result[i] = data[i + 1]
                result[i + 1] = data[i]
            }
        }

        return result
    }
}
