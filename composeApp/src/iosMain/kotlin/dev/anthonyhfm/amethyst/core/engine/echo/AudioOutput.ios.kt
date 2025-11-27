package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.cinterop.*
import platform.AVFAudio.*
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.*
import kotlin.math.abs
import kotlin.time.TimeSource

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object AudioOutput {
    private const val MAX_SOURCES = 16
    private const val TARGET_BIT_DEPTH = 16
    private const val NORMALIZE_THRESHOLD_RATIO = 0.95
    private const val NORMALIZE_REDUCTION = 0.85

    private var sessionConfigured = false

    private data class PlayerHolder(
        val id: String,
        val player: AVAudioPlayer,
        val origin: Any?
    )

    private val activePlayers = mutableMapOf<String, PlayerHolder>()

    private fun ensureSession() {
        if (sessionConfigured) return
        try {
            val session = AVAudioSession.sharedInstance()
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                // Verwende String statt Konstante für Kategorie (falls Konstante nicht verfügbar)
                session.setCategory("AVAudioSessionCategoryPlayback", error = err.ptr)
                if (err.value == null) {
                    session.setActive(true, err.ptr)
                }
            }
            sessionConfigured = true
        } catch (_: Throwable) {
            sessionConfigured = false
        }
    }

    private fun generateSourceKey(signal: Signal.AudioSignal): String =
        "aud_${signal.origin?.hashCode() ?: 0}_${TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds}"

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        ensureSession()
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

        val wavData = buildWav(maybeNormalized, channels, audioSignal.sampleRate, TARGET_BIT_DEPTH)
        val nsData = wavData.toNSData() ?: return null

        val id = generateSourceKey(audioSignal)

        // Limit: entferne ältesten Player
        if (activePlayers.size >= MAX_SOURCES) {
            activePlayers.keys.firstOrNull()?.let { stop(it) }
        }

        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            val player = AVAudioPlayer(data = nsData, error = err.ptr)
            if (err.value != null) return null
            player.volume = GlobalSettings.masterVolume
            player.prepareToPlay()
            player.play()
            activePlayers[id] = PlayerHolder(id, player, audioSignal.origin)
        }

        return id
    }

    actual fun stop(sourceId: String) {
        activePlayers.remove(sourceId)?.let { holder ->
            try { holder.player.stop() } catch (_: Throwable) {}
        }
    }

    actual fun stopAll() {
        val ids = activePlayers.keys.toList()
        ids.forEach { stop(it) }
    }

    // ---------------- WAV Aufbau & Normalisierung ----------------

    private fun buildWav(pcm: ByteArray, channels: Int, sampleRate: Int, bitDepth: Int): ByteArray {
        val dataSize = pcm.size
        val headerSize = 44
        val totalSize = headerSize + dataSize
        val byteRate = sampleRate * channels * (bitDepth / 8)
        val blockAlign = channels * (bitDepth / 8)

        val out = ByteArray(totalSize)
        var o = 0
        fun putAscii(s: String) { s.forEach { out[o++] = it.code.toByte() } }
        fun putIntLE(value: Int) {
            out[o++] = (value and 0xFF).toByte()
            out[o++] = (value shr 8 and 0xFF).toByte()
            out[o++] = (value shr 16 and 0xFF).toByte()
            out[o++] = (value shr 24 and 0xFF).toByte()
        }
        fun putShortLE(value: Int) {
            out[o++] = (value and 0xFF).toByte()
            out[o++] = (value shr 8 and 0xFF).toByte()
        }

        // RIFF Header
        putAscii("RIFF")
        putIntLE(36 + dataSize) // ChunkSize
        putAscii("WAVE")
        // fmt subchunk
        putAscii("fmt ")
        putIntLE(16) // Subchunk1Size
        putShortLE(1) // AudioFormat PCM
        putShortLE(channels)
        putIntLE(sampleRate)
        putIntLE(byteRate)
        putShortLE(blockAlign)
        putShortLE(bitDepth)
        // data subchunk
        putAscii("data")
        putIntLE(dataSize)

        // PCM Daten
        // System.arraycopy ersetzt durch copyInto für Kotlin/Native
        pcm.copyInto(out, destinationOffset = headerSize, startIndex = 0, endIndex = dataSize)
        return out
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
