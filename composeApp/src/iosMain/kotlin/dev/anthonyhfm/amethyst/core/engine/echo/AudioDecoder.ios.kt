package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.Foundation.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object AudioDecoder {

    private const val TARGET_SR = 44100.0
    private const val TARGET_CHANNELS: UInt = 2u
    private const val TARGET_BITS = 16

    private val supportedFormats = listOf("wav", "mp3", "m4a", "aac", "aif", "aiff")

    actual suspend fun decodeAudioFile(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? = withContext(Dispatchers.Default) {
        try {
            val url = NSURL.fileURLWithPath(filePath)
            decodeFromURL(url, sampleStart, sampleEnd)
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
            val ext = fileName.substringAfterLast('.', "tmp")
            val tempPath = NSTemporaryDirectory() + "/amethyst_tmp_${NSUUID().UUIDString}.$ext"
            audioData.usePinned { pinned ->
                val nsData = NSData.create(bytes = pinned.addressOf(0), length = audioData.size.toULong())
                nsData.writeToFile(tempPath, true)
            }
            val url = NSURL.fileURLWithPath(tempPath)
            val signal = decodeFromURL(url, sampleStart, sampleEnd)
            NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
            signal
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
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            val avFile = AVAudioFile(url, err.ptr)
            if (err.value != null || avFile == null) {
                println("Failed to open audio file: ${err.value?.localizedDescription}")
                return null
            }
            return decodeViaConverter(avFile, sampleStart, sampleEnd)
        }
    }

    private fun decodeViaConverter(
        avFile: AVAudioFile,
        sampleStart: Long?,
        sampleEnd: Long?
    ): Signal.AudioSignal? = memScoped {
        try {
            val inFmt = avFile.processingFormat
            val inRate = inFmt.sampleRate
            val inCh = inFmt.channelCount

            val outFmt = AVAudioFormat(
                commonFormat = AVAudioPCMFormatInt16,
                sampleRate = TARGET_SR,
                channels = TARGET_CHANNELS,
                interleaved = false // use planar to interleave manually and avoid channel skew
            ) ?: run {
                println("Failed to create target AVAudioFormat")
                return null
            }

            val converter = AVAudioConverter(fromFormat = inFmt, toFormat = outFmt)
            if (converter == null) {
                println("Failed to create AVAudioConverter")
                return null
            }

            val totalFrames: Long = avFile.length
            val start = (sampleStart ?: 0L).coerceAtLeast(0L).let { if (totalFrames > 0) it.coerceAtMost(totalFrames) else it }
            val end = (sampleEnd ?: totalFrames.takeIf { it > 0 } ?: Long.MAX_VALUE)
                .coerceAtLeast(0L)
                .let { if (totalFrames > 0) it.coerceAtMost(totalFrames) else it }

            if (start > 0) avFile.framePosition = start

            val inChunkFrames: UInt = 4096u
            val ratio = if (inRate > 0.0) TARGET_SR / inRate else 1.0
            // Ensure output buffer capacity is at least as large as input frame capacity to satisfy AVAudioConverter requirement
            val scaledOutFrames = (inChunkFrames.toDouble() * ratio).toUInt()
            val outChunkFrames: UInt = maxOf(inChunkFrames, maxOf(1024u, scaledOutFrames))

            val inBuf = AVAudioPCMBuffer(pCMFormat = inFmt, frameCapacity = inChunkFrames)
            val outBuf = AVAudioPCMBuffer(pCMFormat = outFmt, frameCapacity = outChunkFrames)
            if (inBuf == null || outBuf == null) {
                println("Failed to allocate AVAudioPCMBuffer")
                return null
            }

            val outChunks = ArrayList<ByteArray>()
            var framesLeft = if (end == Long.MAX_VALUE || end <= start) Long.MAX_VALUE else (end - start)
            val errIn = alloc<ObjCObjectVar<NSError?>>()
            val errConv = alloc<ObjCObjectVar<NSError?>>()

            loop@ while (true) {
                val wantIn = if (framesLeft == Long.MAX_VALUE) inChunkFrames.toLong()
                else minOf(framesLeft, inChunkFrames.toLong())
                if (wantIn <= 0) break

                val framesToRead = wantIn.toUInt()
                val okRead = avFile.readIntoBuffer(inBuf, frameCount = framesToRead, error = errIn.ptr)
                if (!okRead) {
                    val e = errIn.value
                    if (e != null) println("Read error: ${e.localizedDescription}")
                }
                val gotIn = inBuf.frameLength.toLong()
                if (gotIn <= 0) break // EOF

                outBuf.frameLength = 0u
                val okConv = converter.convertToBuffer(outBuf, fromBuffer = inBuf, error = errConv.ptr)
                if (!okConv) {
                    val e = errConv.value
                    if (e != null) println("Convert error: ${e.localizedDescription}")
                }

                val produced = outBuf.frameLength.toLong()
                if (produced > 0) {
                    val chData = outBuf.int16ChannelData
                    val channels = minOf(TARGET_CHANNELS.toInt(), outFmt.channelCount.toInt())
                    val frames = produced.toInt()
                    val outBA = ByteArray(frames * channels * 2)
                    if (chData != null) {
                        var o = 0
                        for (f in 0 until frames) {
                            for (c in 0 until channels) {
                                val sample = chData[c]?.get(f)?.toInt() ?: 0
                                outBA[o++] = (sample and 0xFF).toByte()
                                outBA[o++] = ((sample ushr 8) and 0xFF).toByte()
                            }
                        }
                    } else {
                        outChunks.add(ByteArray(outBA.size))
                        inBuf.frameLength = 0u
                        continue@loop
                    }
                    outChunks.add(outBA)
                }

                if (framesLeft != Long.MAX_VALUE) framesLeft -= gotIn
                inBuf.frameLength = 0u

                if (framesLeft <= 0L) break
            }

            val totalBytes = outChunks.sumOf { it.size }
            val outAll = ByteArray(totalBytes)
            var pos = 0
            for (chunk in outChunks) {
                chunk.copyInto(outAll, destinationOffset = pos)
                pos += chunk.size
            }

            val bytesPerFrame = TARGET_CHANNELS.toInt() * 2
            val frames = if (bytesPerFrame > 0) outAll.size / bytesPerFrame else 0
            val durMs = if (TARGET_SR > 0.0) ((frames.toDouble() * 1000.0) / TARGET_SR).toLong() else 0L

            Signal.AudioSignal(
                origin = "AudioDecoder.iOS",
                rawData = outAll,
                sampleRate = TARGET_SR.toInt(),
                channels = TARGET_CHANNELS.toInt(),
                bitDepth = TARGET_BITS,
                durationMs = durMs
            )
        } catch (e: Exception) {
            println("Failed to decode with converter: ${e.message}")
            null
        }
    }
}
