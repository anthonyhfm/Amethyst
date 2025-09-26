package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.Foundation.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object AudioDecoder {

    private val supportedFormats = listOf("wav", "mp3", "m4a", "aac", "aif", "aiff")

    actual suspend fun decodeAudioFile(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? = withContext(Dispatchers.Default) {
        try {
            val url = NSURL.fileURLWithPath(filePath)
            return@withContext decodeFromURL(url, sampleStart, sampleEnd)
        } catch (e: Exception) {
            println("Failed to load audio file '$filePath': ${e.message}")
            null
        }
    }

    actual suspend fun decodeAudioData(
        audioData: ByteArray,
        fileName: String,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? = withContext(Dispatchers.Default) {
        try {
            val nsData = audioData.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = audioData.size.toULong())
            }
            return@withContext decodeFromNSData(nsData, fileName, sampleStart, sampleEnd)
        } catch (e: Exception) {
            println("Failed to decode audio data for '$fileName': ${e.message}")
            null
        }
    }

    actual fun isFormatSupported(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in supportedFormats
    }

    actual fun getSupportedFormats(): List<String> = supportedFormats.toList()

    private fun decodeFromURL(url: NSURL, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? {
        return try {
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val audioFile = AVAudioFile(url, errorPtr.ptr)
                if (errorPtr.value != null) {
                    println("Failed to open audio file: ${errorPtr.value?.localizedDescription}")
                    return null
                }
                decodeFromAVAudioFile(audioFile, sampleStart, sampleEnd)
            }
        } catch (e: Exception) {
            println("Failed to decode from URL: ${e.message}")
            null
        }
    }

    private fun decodeFromNSData(data: NSData, fileName: String, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? {
        return try {
            val ext = fileName.substringAfterLast('.', "tmp")
            val tempPath = NSTemporaryDirectory() + "/amethyst_tmp_${NSUUID().UUIDString}.$ext"
            data.writeToFile(tempPath, true)
            val url = NSURL.fileURLWithPath(tempPath)
            val signal = decodeFromURL(url, sampleStart, sampleEnd)
            NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
            signal
        } catch (e: Exception) {
            println("Failed to decode from NSData via temp file: ${e.message}")
            null
        }
    }

    private fun decodeFromAVAudioFile(
        audioFile: AVAudioFile,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? {
        return try {
            val format = audioFile.processingFormat
            val totalFrames = audioFile.length

            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val buffer: AVAudioPCMBuffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = totalFrames.toUInt())
                audioFile.readIntoBuffer(buffer, errorPtr.ptr)
                if (errorPtr.value != null) {
                    println("Failed to read audio data: ${errorPtr.value?.localizedDescription}")
                    return null
                }

                val frameLength = buffer.frameLength.toLong()
                val start = (sampleStart ?: 0L).coerceIn(0L, frameLength)
                val end = (sampleEnd ?: frameLength).coerceIn(0L, frameLength)
                val effStart = if (start > end) end else start
                val effEnd = if (end < effStart) effStart else end
                val framesToCopy = (effEnd - effStart).coerceAtLeast(0L)

                val channelCount = format.channelCount.toInt().coerceAtLeast(1)
                val isInt16 = format.commonFormat == AVAudioPCMFormatInt16
                val isFloat32 = format.commonFormat == AVAudioPCMFormatFloat32
                val interleaved = format.interleaved

                val outBytes = ByteArray((framesToCopy * channelCount * 2).toInt())

                if (framesToCopy == 0L) {
                    return Signal.AudioSignal(
                        origin = "AudioDecoder",
                        rawData = outBytes,
                        sampleRate = format.sampleRate.toInt(),
                        channels = channelCount,
                        bitDepth = 16,
                        durationMs = 0
                    )
                }

                if (isInt16) {
                    val channelData = buffer.int16ChannelData
                    if (channelData != null) {
                        var outIndex = 0
                        for (frame in effStart until effEnd) {
                            for (ch in 0 until channelCount) {
                                val sample = if (interleaved) {
                                    val basePtr = channelData[0]!!
                                    basePtr[((frame * channelCount) + ch).toInt()].toInt()
                                } else {
                                    val chPtr = channelData[ch]!!
                                    chPtr[frame.toInt()].toInt()
                                }
                                outBytes[outIndex++] = (sample and 0xFF).toByte()
                                outBytes[outIndex++] = ((sample shr 8) and 0xFF).toByte()
                            }
                        }
                    }
                } else if (isFloat32) {
                    val channelData = buffer.floatChannelData
                    if (channelData != null) {
                        var outIndex = 0
                        for (frame in effStart until effEnd) {
                            for (ch in 0 until channelCount) {
                                val fSample = if (interleaved) {
                                    val basePtr = channelData[0]!!
                                    basePtr[((frame * channelCount) + ch).toInt()]
                                } else {
                                    val chPtr = channelData[ch]!!
                                    chPtr[frame.toInt()]
                                }
                                val clamped = fSample.coerceIn(-1f, 1f)
                                val sample = (clamped * 32767f).toInt().coerceIn(-32768, 32767)
                                outBytes[outIndex++] = (sample and 0xFF).toByte()
                                outBytes[outIndex++] = ((sample shr 8) and 0xFF).toByte()
                            }
                        }
                    }
                } else {
                    val channelData = buffer.int16ChannelData
                    if (channelData != null) {
                        var outIndex = 0
                        for (frame in effStart until effEnd) {
                            for (ch in 0 until channelCount) {
                                val sample = if (interleaved) {
                                    val basePtr = channelData[0]!!
                                    basePtr[((frame * channelCount) + ch).toInt()].toInt()
                                } else {
                                    val chPtr = channelData[ch]!!
                                    chPtr[frame.toInt()].toInt()
                                }
                                outBytes[outIndex++] = (sample and 0xFF).toByte()
                                outBytes[outIndex++] = ((sample shr 8) and 0xFF).toByte()
                            }
                        }
                    } else {
                        println("Unsupported commonFormat -> returning empty")
                        return Signal.AudioSignal(
                            origin = "AudioDecoder",
                            rawData = ByteArray(0),
                            sampleRate = format.sampleRate.toInt(),
                            channels = channelCount,
                            bitDepth = 16,
                            durationMs = 0
                        )
                    }
                }

                val durationMs = if (format.sampleRate > 0.0) ((framesToCopy * 1000.0) / format.sampleRate).toLong() else 0L

                Signal.AudioSignal(
                    origin = "AudioDecoder",
                    rawData = outBytes,
                    sampleRate = format.sampleRate.toInt(),
                    channels = channelCount,
                    bitDepth = 16,
                    durationMs = durationMs
                )
            }
        } catch (e: Exception) {
            println("Failed to decode AVAudioFile: ${e.message}")
            null
        }
    }
}
