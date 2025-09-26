package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import platform.AVFAudio.*
import platform.Foundation.*
import kotlinx.cinterop.*
import platform.posix.memcpy
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object AudioOutput {
    private var audioEngine: AVAudioEngine? = null
    private var isInitialized = false
    private val activeSources = mutableMapOf<String, AVAudioPlayerNode>()

    private const val MAX_SOURCES = 16

    init { initializeAVAudioEngine() }

    private fun initializeAVAudioEngine() {
        if (isInitialized) return
        try {
            val engine = AVAudioEngine()
            audioEngine = engine
            val audioSession = AVAudioSession.sharedInstance()
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                audioSession.setCategory(AVAudioSessionCategoryPlayback, error = err.ptr)
                audioSession.setActive(true, err.ptr)
            }
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                engine.startAndReturnError(err.ptr)
                if (err.value != null) return
            }
            isInitialized = true
        } catch (_: Exception) { isInitialized = false }
    }

    private fun generateSourceKey(origin: Any?): String =
        (origin?.hashCode()?.toString() ?: "src") + "_" + (activeSources.size + 1)

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!isInitialized) initializeAVAudioEngine()
        if (!isInitialized) return null

        val rawData = audioSignal.rawData ?: return null
        if (rawData.isEmpty()) return null

        if (activeSources.size >= MAX_SOURCES) {
            activeSources.keys.firstOrNull()?.let { stop(it) }
        }

        return try {
            val channels = audioSignal.channels.coerceAtLeast(1)
            if (audioSignal.bitDepth != 16) return null
            val bytesPerFrame = channels * 2
            val validSize = (rawData.size / bytesPerFrame) * bytesPerFrame
            val frames = validSize / bytesPerFrame
            if (frames <= 0) return null

            val format = AVAudioFormat(
                commonFormat = AVAudioPCMFormatInt16,
                sampleRate = audioSignal.sampleRate.toDouble(),
                channels = channels.toUInt(),
                interleaved = true
            )

            val engine = audioEngine ?: return null
            val playerNode = AVAudioPlayerNode()
            engine.attachNode(playerNode)
            engine.connect(playerNode, to = engine.mainMixerNode, format = format)

            val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frames.toUInt())
            val channelData = buffer.int16ChannelData ?: run {
                engine.detachNode(playerNode); return null
            }
            val dstShorts = channelData[0] ?: run { engine.detachNode(playerNode); return null }

            rawData.usePinned { pinned ->
                // Ziel als Byte-Pointer neu interpretieren
                val dstBytes = dstShorts.reinterpret<ByteVar>()
                memcpy(dstBytes, pinned.addressOf(0), validSize.convert())
            }
            buffer.frameLength = frames.toUInt()

            val key = generateSourceKey(audioSignal.origin)
            playerNode.scheduleBuffer(buffer, atTime = null, options = 0u) {
                // Cleanup nach Ende des Buffers
                dispatch_async(dispatch_get_main_queue()) {
                    activeSources.remove(key)?.let { finishedNode ->
                        try {
                            finishedNode.stop()
                            audioEngine?.detachNode(finishedNode)
                        } catch (_: Exception) {}
                    }
                }
            }
            playerNode.play()
            activeSources[key] = playerNode
            key
        } catch (_: Exception) { null }
    }

    actual fun stop(sourceId: String) {
        activeSources.remove(sourceId)?.let { node ->
            try { node.stop(); audioEngine?.detachNode(node) } catch (_: Exception) {}
        }
    }

    actual fun stopAll() {
        val nodes = activeSources.values.toList()
        activeSources.clear()
        nodes.forEach { n -> try { n.stop(); audioEngine?.detachNode(n) } catch (_: Exception) {} }
    }
}
