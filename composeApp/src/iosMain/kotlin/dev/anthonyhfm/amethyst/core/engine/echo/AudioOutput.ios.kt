package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import platform.AVFAudio.*
import platform.Foundation.*
import kotlinx.cinterop.*
import platform.posix.memcpy
import platform.darwin.*
import kotlin.math.min

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object AudioOutput {
    private const val MAX_SOURCES = 16
    private const val QUEUE_BUFFERS = 3
    private const val CHUNK_FRAMES_DEFAULT = 256
    private const val IO_BUFFER_TARGET_SEC = 0.005

    private var audioEngine: AVAudioEngine? = null
    private var isInitialized = false

    private val activeSources = mutableMapOf<String, StreamingSource>()

    private data class StreamingSource(
        val id: String,
        val node: AVAudioPlayerNode,
        val format: AVAudioFormat,
        val data: ByteArray,
        val bytesPerFrame: Int,
        val totalFrames: Int,
        var readFrames: Int,
        val chunkFrames: Int
    )

    init { initializeAVAudioEngine() }

    private fun initializeAVAudioEngine() {
        if (isInitialized) return
        try {
            val session = AVAudioSession.sharedInstance()

            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()

                session.setCategory(
                    AVAudioSessionCategoryPlayback,
                    withOptions = AVAudioSessionCategoryOptionMixWithOthers,
                    error = err.ptr
                )

                session.setPreferredIOBufferDuration(IO_BUFFER_TARGET_SEC, error = err.ptr)
                session.setActive(true, err.ptr)
            }

            val engine = AVAudioEngine()
            audioEngine = engine

            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                engine.startAndReturnError(err.ptr)
                if (err.value != null) {
                    engine.stop()
                    engine.reset()
                    engine.startAndReturnError(err.ptr)
                    if (err.value != null) return
                }
            }

            val center = NSNotificationCenter.defaultCenter
            center.addObserverForName(
                name = AVAudioSessionInterruptionNotification,
                `object` = null,
                queue = null
            ) { _ ->
                restartEngine()
            }
            center.addObserverForName(
                name = AVAudioSessionRouteChangeNotification,
                `object` = null,
                queue = null
            ) { _ ->
                restartEngine()
            }

            isInitialized = true
        } catch (_: Throwable) {
            isInitialized = false
        }
    }

    private fun restartEngine() {
        val eng = audioEngine ?: return
        try {
            memScoped {
                eng.stop()
                eng.reset()
                val err = alloc<ObjCObjectVar<NSError?>>()
                eng.startAndReturnError(err.ptr)
            }
        } catch (_: Throwable) {}
    }

    private fun generateSourceKey(origin: Any?): String =
        (origin?.hashCode()?.toString() ?: "src") + "_" + (activeSources.size + 1)

    private fun engineOrNull(): AVAudioEngine? {
        if (!isInitialized) initializeAVAudioEngine()
        return audioEngine
    }

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        val eng = engineOrNull() ?: return null
        val raw = audioSignal.rawData ?: return null
        if (raw.isEmpty()) return null

        if (activeSources.size >= MAX_SOURCES) {
            activeSources.keys.firstOrNull()?.let { stop(it) }
        }

        if (audioSignal.bitDepth != 16) return null
        val ch = audioSignal.channels.coerceIn(1, 2)
        val bytesPerFrame = ch * 2
        val validBytes = (raw.size / bytesPerFrame) * bytesPerFrame
        val totalFrames = if (bytesPerFrame > 0) validBytes / bytesPerFrame else 0
        if (totalFrames <= 0) return null

        val fmt = AVAudioFormat(
            commonFormat = AVAudioPCMFormatInt16,
            sampleRate = audioSignal.sampleRate.toDouble(),
            channels = ch.toUInt(),
            interleaved = true
        ) ?: return null

        val node = AVAudioPlayerNode()
        try {
            eng.attachNode(node)
            eng.connect(node, to = eng.mainMixerNode, format = fmt)
        } catch (_: Throwable) {
            try { eng.detachNode(node) } catch (_: Throwable) {}
            return null
        }

        val key = generateSourceKey(audioSignal.origin)
        val src = StreamingSource(
            id = key,
            node = node,
            format = fmt,
            data = if (validBytes == raw.size) raw else raw.copyOf(validBytes),
            bytesPerFrame = bytesPerFrame,
            totalFrames = totalFrames,
            readFrames = 0,
            chunkFrames = CHUNK_FRAMES_DEFAULT
        )
        activeSources[key] = src

        var queued = 0
        while (queued < QUEUE_BUFFERS) {
            if (!scheduleNextChunk(src)) break
            queued++
        }

        node.play()

        if (queued == 0) {
            stop(key)
            return null
        }

        return key
    }

    private fun scheduleNextChunk(src: StreamingSource): Boolean {
        val remainingFrames = src.totalFrames - src.readFrames
        if (remainingFrames <= 0) return false

        val frames = min(remainingFrames, src.chunkFrames)
        val buf = AVAudioPCMBuffer(pCMFormat = src.format, frameCapacity = frames.toUInt()) ?: return false

        val dstShorts = buf.int16ChannelData?.get(0) ?: return false
        val dstBytes = dstShorts.reinterpret<UByteVar>()
        val bytes = frames * src.bytesPerFrame

        val byteOffset = src.readFrames * src.bytesPerFrame
        src.data.usePinned { pinned ->
            memcpy(dstBytes, pinned.addressOf(byteOffset), bytes.convert())
        }
        buf.frameLength = frames.toUInt()
        src.readFrames += frames

        src.node.scheduleBuffer(buf, atTime = null, options = 0u) {
            dispatch_async(dispatch_get_main_queue()) {
                val live = activeSources[src.id]
                if (live == null || live.node != src.node) return@dispatch_async

                if (!scheduleNextChunk(live)) {
                    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (10_000_000).convert()), dispatch_get_main_queue()) {
                        activeSources.remove(live.id)?.let { toClose ->
                            try {
                                toClose.node.stop()
                                engineOrNull()?.detachNode(toClose.node)
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        }
        return true
    }

    actual fun stop(sourceId: String) {
        activeSources.remove(sourceId)?.let { s ->
            try {
                s.node.stop()
                audioEngine?.detachNode(s.node)
            } catch (_: Throwable) {}
        }
    }

    actual fun stopAll() {
        val ids = activeSources.keys.toList()
        ids.forEach { stop(it) }
    }
}
