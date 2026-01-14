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
            fastPathRead(avFile, sampleStart, sampleEnd)?.let { return it }
            return decodeViaConverter(avFile, sampleStart, sampleEnd)
        }
    }

    private fun fastPathRead(avFile: AVAudioFile, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? = memScoped {
        val fmt = avFile.processingFormat
        val isInt16 = fmt.commonFormat == AVAudioPCMFormatInt16
        val isInterleaved = fmt.isInterleaved()
        val srOk = kotlin.math.abs(fmt.sampleRate - TARGET_SR) < 1e-6
        val ch = fmt.channelCount.toInt()
        if (!isInt16 || !srOk || ch <= 0 || ch > 2) return null

        val totalFrames = avFile.length
        val start = (sampleStart ?: 0L).coerceAtLeast(0L).let { if (totalFrames > 0) it.coerceAtMost(totalFrames) else it }
        val end = (sampleEnd ?: totalFrames.takeIf { it > 0 } ?: Long.MAX_VALUE)
            .coerceAtLeast(0L)
            .let { if (totalFrames > 0) it.coerceAtMost(totalFrames) else it }
        if (start > 0) avFile.framePosition = start
        val framesToRead = if (end == Long.MAX_VALUE || end <= start) totalFrames - start else end - start
        if (framesToRead <= 0) return null

        val buf = AVAudioPCMBuffer(pCMFormat = fmt, frameCapacity = framesToRead.toUInt()) ?: return null
        val err = alloc<ObjCObjectVar<NSError?>>()
        val ok = avFile.readIntoBuffer(buf, frameCount = framesToRead.toUInt(), error = err.ptr)
        if (!ok) {
            err.value?.let { println("Fast-path read error: ${it.localizedDescription}") }
        }
        val frames = buf.frameLength.toInt()
        if (frames <= 0) return null

        val out: ByteArray = if (isInterleaved) {
            val bytes = frames * ch * 2
            val outBA = ByteArray(bytes)
            val src = buf.int16ChannelData?.get(0)?.reinterpret<ByteVar>() ?: return null
            outBA.usePinned { pinned -> platform.posix.memcpy(pinned.addressOf(0), src, bytes.convert()) }
            if (ch == TARGET_CHANNELS.toInt()) outBA else monoToStereo(outBA)
        } else {
            // planar -> interleave manually, but still cheaper than converter
            val channels = minOf(ch, TARGET_CHANNELS.toInt())
            val outBA = ByteArray(frames * channels * 2)
            val c0 = buf.int16ChannelData?.get(0)
            val c1 = if (channels > 1) buf.int16ChannelData?.get(1) else null
            var o = 0
            for (f in 0 until frames) {
                val s0 = c0?.get(f)?.toInt() ?: 0
                outBA[o++] = (s0 and 0xFF).toByte()
                outBA[o++] = ((s0 ushr 8) and 0xFF).toByte()
                if (channels > 1) {
                    val s1 = c1?.get(f)?.toInt() ?: 0
                    outBA[o++] = (s1 and 0xFF).toByte()
                    outBA[o++] = ((s1 ushr 8) and 0xFF).toByte()
                }
            }
            if (channels == 1) monoToStereo(outBA) else outBA
        }

        val bytesPerFrame = TARGET_CHANNELS.toInt() * 2
        val durMs = (out.size / bytesPerFrame) * 1000L / TARGET_SR.toLong()
        return Signal.AudioSignal(
            origin = "AudioDecoder.iOS.Fast",
            rawData = out,
            sampleRate = TARGET_SR.toInt(),
            channels = TARGET_CHANNELS.toInt(),
            bitDepth = TARGET_BITS,
            durationMs = durMs
        )
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

            val inChunkFrames: UInt = 16384u
            val ratio = if (inRate > 0.0) TARGET_SR / inRate else 1.0
            val scaledOutFrames = (inChunkFrames.toDouble() * ratio).toUInt()
            val outChunkFrames: UInt = maxOf(inChunkFrames, maxOf(4096u, scaledOutFrames))

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
                        val c0 = chData[0]
                        val c1 = if (channels > 1) chData[1] else null
                        for (f in 0 until frames) {
                            val s0 = c0?.get(f)?.toInt() ?: 0
                            outBA[o++] = (s0 and 0xFF).toByte()
                            outBA[o++] = ((s0 ushr 8) and 0xFF).toByte()
                            if (channels > 1) {
                                val s1 = c1?.get(f)?.toInt() ?: 0
                                outBA[o++] = (s1 and 0xFF).toByte()
                                outBA[o++] = ((s1 ushr 8) and 0xFF).toByte()
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

    private fun monoToStereo(input: ByteArray): ByteArray {
        val output = ByteArray(input.size * 2)
        var o = 0
        for (i in input.indices step 2) {
            val sample = (input[i].toInt() and 0xFF) or ((input[i + 1].toInt() and 0xFF) shl 8)
            // Left channel
            output[o++] = input[i]
            output[o++] = input[i + 1]
            // Right channel (duplicate of left)
            output[o++] = input[i]
            output[o++] = input[i + 1]
        }
        return output
    }
}
