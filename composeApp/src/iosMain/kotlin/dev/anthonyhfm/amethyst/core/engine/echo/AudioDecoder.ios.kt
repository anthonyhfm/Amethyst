package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.Foundation.*
import platform.CoreAudio.*
import kotlinx.cinterop.*

actual object AudioDecoder {

    private val supportedFormats = listOf("wav", "mp3", "flac", "m4a", "aac")

    actual suspend fun decodeAudioFile(filePath: String): Signal.AudioSignal? = withContext(Dispatchers.Default) {
        try {
            val url = NSURL.fileURLWithPath(filePath)
            return@withContext decodeFromURL(url)
        } catch (e: Exception) {
            println("Failed to load audio file '$filePath': ${e.message}")
            return@withContext null
        }
    }

    actual suspend fun decodeAudioData(audioData: ByteArray, fileName: String): Signal.AudioSignal? = withContext(Dispatchers.Default) {
        try {
            val nsData = audioData.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = audioData.size.toULong())
            }
            return@withContext decodeFromNSData(nsData)
        } catch (e: Exception) {
            println("Failed to decode audio data for '$fileName': ${e.message}")
            return@withContext null
        }
    }

    actual fun isFormatSupported(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in supportedFormats
    }

    actual fun getSupportedFormats(): List<String> = supportedFormats.toList()

    private fun decodeFromURL(url: NSURL): Signal.AudioSignal? {
        return try {
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val audioFile = AVAudioFile(url, errorPtr.ptr)

                if (errorPtr.value != null) {
                    println("Failed to open audio file: ${errorPtr.value?.localizedDescription}")
                    return null
                }

                decodeFromAVAudioFile(audioFile)
            }
        } catch (e: Exception) {
            println("Failed to decode from URL: ${e.message}")
            null
        }
    }

    private fun decodeFromNSData(data: NSData): Signal.AudioSignal? {
        return try {
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val audioFile = AVAudioFile(data, errorPtr.ptr)

                if (errorPtr.value != null) {
                    println("Failed to open audio data: ${errorPtr.value?.localizedDescription}")
                    return null
                }

                decodeFromAVAudioFile(audioFile)
            }
        } catch (e: Exception) {
            println("Failed to decode from NSData: ${e.message}")
            null
        }
    }

    private fun decodeFromAVAudioFile(audioFile: AVAudioFile): Signal.AudioSignal? {
        return try {
            val format = audioFile.processingFormat
            val frameCount = audioFile.length.toInt()

            // Target format: 44.1kHz, 16-bit, Stereo
            val targetSampleRate = 44100.0
            val targetChannels = 2

            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()

                // Create target format
                val targetFormat = AVAudioFormat(
                    commonFormat = AVAudioCommonFormatPCMFormatInt16,
                    sampleRate = targetSampleRate,
                    channels = targetChannels.toUInt(),
                    interleaved = true
                )

                if (targetFormat == null) {
                    println("Failed to create target audio format")
                    return null
                }

                // Create converter if needed
                val converter = if (format != targetFormat) {
                    AVAudioConverter(from = format, to = targetFormat)
                } else null

                // Calculate output frame count
                val outputFrameCount = if (converter != null) {
                    (frameCount * targetSampleRate / format.sampleRate).toInt()
                } else {
                    frameCount
                }

                // Create buffer for reading
                val buffer = AVAudioPCMBuffer(PCMFormat = format, frameCapacity = frameCount.toUInt())
                if (buffer == null) {
                    println("Failed to create audio buffer")
                    return null
                }

                // Read audio data
                audioFile.readIntoBuffer(buffer, errorPtr.ptr)
                if (errorPtr.value != null) {
                    println("Failed to read audio data: ${errorPtr.value?.localizedDescription}")
                    return null
                }

                // Convert if needed
                val finalBuffer = if (converter != null) {
                    val outputBuffer = AVAudioPCMBuffer(PCMFormat = targetFormat, frameCapacity = outputFrameCount.toUInt())
                    if (outputBuffer == null) {
                        println("Failed to create output buffer")
                        return null
                    }

                    converter.convertToBuffer(outputBuffer, from = buffer, errorPtr.ptr)
                    if (errorPtr.value != null) {
                        println("Failed to convert audio: ${errorPtr.value?.localizedDescription}")
                        return null
                    }

                    outputBuffer
                } else {
                    buffer
                }

                // Extract PCM data
                val pcmData = extractPCMData(finalBuffer, targetFormat)

                Signal.AudioSignal(
                    origin = "AudioDecoder",
                    rawData = pcmData,
                    sampleRate = targetFormat.sampleRate.toInt(),
                    channels = targetFormat.channelCount.toInt(),
                    bitDepth = 16
                )
            }
        } catch (e: Exception) {
            println("Failed to decode AVAudioFile: ${e.message}")
            null
        }
    }

    private fun extractPCMData(buffer: AVAudioPCMBuffer, format: AVAudioFormat): ByteArray {
        val frameLength = buffer.frameLength.toInt()
        val channelCount = format.channelCount.toInt()
        val bytesPerFrame = channelCount * 2 // 16-bit = 2 bytes per sample

        val pcmData = ByteArray(frameLength * bytesPerFrame)

        // Extract interleaved PCM data
        val int16Ptr = buffer.int16ChannelData?.get(0)
        if (int16Ptr != null) {
            for (i in 0 until frameLength * channelCount) {
                val sample = int16Ptr[i]
                pcmData[i * 2] = (sample and 0xFF).toByte()
                pcmData[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }
        }

        return pcmData
    }
}
