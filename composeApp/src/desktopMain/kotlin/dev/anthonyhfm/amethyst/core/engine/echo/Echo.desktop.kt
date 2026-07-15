package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.nativeengine.EchoAudioBuffer
import dev.anthonyhfm.amethyst.nativeengine.EchoEngine as NativeEchoEngine
import dev.anthonyhfm.amethyst.settings.data.AudioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

actual object Echo {
    private val native = NativeEchoEngine()
    private val origins = ConcurrentHashMap<String, String>()

    @Volatile
    private var initialized = false
    private val formats = listOf("wav", "mp3", "flac", "ogg")

    actual fun initialize(): Boolean {
        if (initialized) return true
        initialized = native.initialize().available
        return initialized
    }

    actual fun setPreferredBufferFrames(frames: Int) {
        native.setPreferredBufferFrames(frames.toUInt())
        if (initialized) {
            native.shutdown()
            initialized = false
            initialize()
        }
    }

    actual fun outputDevices(): List<String> = native.outputDevices()

    actual fun setPreferredOutputDevice(name: String?) {
        native.setPreferredOutputDevice(name.orEmpty())
        if (initialized) {
            native.shutdown()
            initialized = false
            initialize()
        }
    }

    actual suspend fun decodeAudioFile(filePath: String, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? =
        withContext(Dispatchers.IO) { native.decodeFile(filePath).buffer?.toSignal("Echo.Decoder")?.trim(sampleStart, sampleEnd) }

    actual suspend fun decodeAudioData(audioData: ByteArray, fileName: String, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? =
        withContext(Dispatchers.IO) { native.decodeBytes(audioData, fileName).buffer?.toSignal("Echo.Decoder")?.trim(sampleStart, sampleEnd) }

    actual fun isFormatSupported(fileName: String) = fileName.substringAfterLast('.', "").lowercase() in formats
    actual fun getSupportedFormats(): List<String> = formats

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!initialize()) return null
        val buffer = audioSignal.toNativeBuffer() ?: return null
        val origin = audioSignal.origin?.hashCode()?.toString() ?: "anonymous"
        native.setMasterGain(AudioSettings.masterVolume.value)
        return native.play(buffer, origin, audioSignal.gain, audioSignal.pan)?.also { origins[it] = origin }
    }

    actual fun playMultiple(signals: List<Signal.AudioSignal>): List<String?> = signals.map(::play)
    actual fun update(sourceId: String, gain: Float, pan: Float) {
        native.setMasterGain(AudioSettings.masterVolume.value)
        native.update(sourceId, gain, pan)
    }
    actual fun stop(sourceId: String) { native.stop(sourceId); origins.remove(sourceId) }
    actual fun stopAll() { native.stopAll(); origins.clear() }
    actual fun stopByOrigin(origin: Any?) {
        if (origin == null) return
        native.stopByOrigin(origin.hashCode().toString())
        origins.entries.removeIf { it.value == origin.hashCode().toString() }
    }
    actual fun audioEnter(signals: List<Signal.AudioSignal>) { playMultiple(signals) }
    actual fun cancel(signalOrigin: Any?) = stopByOrigin(signalOrigin)
    actual fun reset() = stopAll()
    actual fun shutdown() { native.shutdown(); initialized = false; origins.clear() }

    private fun EchoAudioBuffer.toSignal(origin: String): Signal.AudioSignal {
        val pcm = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, sample ->
            val value = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
            pcm[index * 2] = (value and 0xFF).toByte()
            pcm[index * 2 + 1] = ((value ushr 8) and 0xFF).toByte()
        }
        return Signal.AudioSignal(origin, pcm, sampleRate.toInt(), channels.toInt(), 16,
            (samples.size / channels.toInt().coerceAtLeast(1) * 1000L) / sampleRate.toLong().coerceAtLeast(1))
    }

    private fun Signal.AudioSignal.trim(start: Long?, end: Long?): Signal.AudioSignal {
        val bytes = rawData ?: return this
        val frameSize = (channels * (bitDepth / 8)).coerceAtLeast(1)
        val frames = bytes.size / frameSize
        val from = (start ?: 0).coerceIn(0, frames.toLong()).toInt()
        val until = (end ?: frames.toLong()).coerceIn(from.toLong(), frames.toLong()).toInt()
        return copy(rawData = bytes.copyOfRange(from * frameSize, until * frameSize), durationMs = (until - from) * 1000L / sampleRate)
    }

    private fun Signal.AudioSignal.toNativeBuffer(): EchoAudioBuffer? {
        val bytes = rawData ?: return null
        if (bitDepth != 16 || channels !in 1..2) return null
        val values = ArrayList<Float>(bytes.size / 2)
        var index = 0
        while (index + 1 < bytes.size) {
            val sample = (bytes[index].toInt() and 0xFF) or (bytes[index + 1].toInt() shl 8)
            values += sample.toShort() / 32768f
            index += 2
        }
        return EchoAudioBuffer(values, sampleRate.toUInt(), channels.toUInt())
    }
}
