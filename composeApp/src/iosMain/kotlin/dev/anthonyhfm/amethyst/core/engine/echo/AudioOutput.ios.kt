package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.cinterop.*
import platform.AVFAudio.*
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.math.abs
import kotlin.time.TimeSource

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object AudioOutput {
    private const val MAX_SOURCES = 16
    private const val TARGET_BIT_DEPTH = 16
    private const val NORMALIZE_THRESHOLD_RATIO = 0.95
    private const val NORMALIZE_REDUCTION = 0.85
    private const val FALLBACK_SAMPLE_RATE = 44100.0
    private const val PREFERRED_IO_BUFFER = 0.002 // 2 ms target for <10 ms E2E

    private var sessionConfigured = false
    private var currentSampleRate = FALLBACK_SAMPLE_RATE

    private data class PlayerHolder(
        val id: String,
        val origin: Any?,
        val node: AVAudioPlayerNode,
        val buffer: AVAudioPCMBuffer
    )

    private val engine = AVAudioEngine()
    private val activePlayers = mutableMapOf<String, PlayerHolder>()

    private fun ensureSession(preferredRate: Double) {
        if (sessionConfigured) return
        try {
            val session = AVAudioSession.sharedInstance()
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                session.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeGameChat, options = 0u, error = err.ptr)
                if (err.value != null) println("AudioSession setCategory error: ${'$'}{err.value?.localizedDescription}")
                session.setPreferredSampleRate(preferredRate, err.ptr)
                if (err.value != null) println("AudioSession preferredSampleRate error: ${'$'}{err.value?.localizedDescription}")
                session.setPreferredIOBufferDuration(PREFERRED_IO_BUFFER, err.ptr)
                if (err.value != null) println("AudioSession preferredIOBuffer error: ${'$'}{err.value?.localizedDescription}")
                session.setActive(true, err.ptr)
                if (err.value != null) println("AudioSession setActive error: ${'$'}{err.value?.localizedDescription}")
            }
            currentSampleRate = session.sampleRate.takeIf { it > 0 } ?: preferredRate
            sessionConfigured = true
        } catch (_: Throwable) {
            sessionConfigured = false
        }
    }

    private fun generateSourceKey(signal: Signal.AudioSignal): String =
        "aud_${'$'}{signal.origin?.hashCode() ?: 0}_${'$'}{TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds}"

    private fun ensureEngineRunning(format: AVAudioFormat): Boolean {
        // Attach and connect nodes on demand; engine runs once.
        if (!engine.isRunning()) {
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                engine.prepare()
                engine.startAndReturnError(err.ptr)
                if (err.value != null) {
                    println("AudioEngine start error: ${'$'}{err.value?.localizedDescription}")
                    return false
                }
            }
        }
        return true
    }

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        val raw = audioSignal.rawData ?: return null
        if (raw.isEmpty()) return null
        if (audioSignal.bitDepth != TARGET_BIT_DEPTH) return null // Nur 16-bit PCM unterstützt

        val channels = audioSignal.channels.coerceIn(1, 2)
        val bytesPerSample = TARGET_BIT_DEPTH / 8
        val frameBytes = channels * bytesPerSample
        val validSize = (raw.size / frameBytes) * frameBytes
        if (validSize <= 0) return null

        val trimmed = if (validSize == raw.size) raw else raw.copyOf(validSize)
        val maybeNormalized = normalizeIfNeeded(trimmed, TARGET_BIT_DEPTH)

        val preferredRate = audioSignal.sampleRate.takeIf { it > 0 }?.toDouble() ?: FALLBACK_SAMPLE_RATE
        ensureSession(preferredRate)
        currentSampleRate = preferredRate

        val format = AVAudioFormat(
            commonFormat = AVAudioPCMFormatInt16,
            sampleRate = currentSampleRate,
            channels = channels.toUInt(),
            interleaved = true
        ) ?: return null

        val frames = (maybeNormalized.size / frameBytes).toUInt()
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frames) ?: return null
        buffer.frameLength = frames

        val dst = buffer.int16ChannelData?.get(0)?.reinterpret<ByteVar>() ?: return null
        maybeNormalized.usePinned { pinned ->
            memcpy(dst, pinned.addressOf(0), maybeNormalized.size.convert())
        }

        val id = generateSourceKey(audioSignal)

        // Limit: entferne ältesten Player
        if (activePlayers.size >= MAX_SOURCES) {
            activePlayers.keys.firstOrNull()?.let { stop(it) }
        }

        val node = AVAudioPlayerNode()
        node.volume = GlobalSettings.masterVolume
        engine.attachNode(node)
        engine.connect(node, to = engine.mainMixerNode, format = format)

        if (!ensureEngineRunning(format)) {
            engine.detachNode(node)
            return null
        }

        node.scheduleBuffer(buffer, atTime = null, options = AVAudioPlayerNodeBufferInterrupts) { /* no-op */ }
        node.play()

        activePlayers[id] = PlayerHolder(id, audioSignal.origin, node, buffer)
        return id
    }

    actual fun stop(sourceId: String) {
        activePlayers.remove(sourceId)?.let { holder ->
            try { holder.node.stop() } catch (_: Throwable) {}
            try { engine.detachNode(holder.node) } catch (_: Throwable) {}
        }
    }

    actual fun stopAll() {
        val ids = activePlayers.keys.toList()
        ids.forEach { stop(it) }
    }

    actual fun stopByOrigin(origin: Any?) {
        if (origin == null) return
        val toRemove = activePlayers.filter { it.value.origin == origin }.keys
        toRemove.forEach { stop(it) }
    }

    private fun normalizeIfNeeded(data: ByteArray, bitDepth: Int): ByteArray {
        if (bitDepth != 16) return data
        val maxAmp = findMaxAmplitude(data)
        val ratio = maxAmp / 32767.0
        if (ratio <= NORMALIZE_THRESHOLD_RATIO) return data
        return applyVolumeReduction(data, NORMALIZE_REDUCTION)
    }

    private fun findMaxAmplitude(data: ByteArray): Int {
        var max = 0
        var i = 0
        while (i + 1 < data.size) {
            val sample = (data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8)
            val signed = if (sample > 32767) sample - 65536 else sample
            val absVal = abs(signed)
            if (absVal > max) max = absVal
            i += 2
        }
        return max
    }

    private fun applyVolumeReduction(data: ByteArray, factor: Double): ByteArray {
        val out = ByteArray(data.size)
        var i = 0
        while (i + 1 < data.size) {
            val sample = (data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8)
            val signed = if (sample > 32767) sample - 65536 else sample
            val reduced = (signed * factor).toInt().coerceIn(-32768, 32767)
            out[i] = (reduced and 0xFF).toByte()
            out[i + 1] = ((reduced shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    private fun ByteArray.toNSData(): NSData = this.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())!!
    }
}
