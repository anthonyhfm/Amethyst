package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.flow.StateFlow

internal val EchoSupportedAudioFormats = listOf(
    "wav", "wave", "mp1", "mp2", "mp3", "flac", "ogg", "oga",
    "aac", "m4a", "caf", "aiff", "aif", "aifc",
)

data class AudioOutputDevice(
    val id: String,
    val displayName: String,
    val isDefault: Boolean = false,
)

enum class AudioOutputMode {
    Shared,
    Exclusive,
}

data class AudioOutputStatus(
    val available: Boolean = false,
    val deviceId: String = "",
    val deviceName: String = "",
    val backend: String = "",
    val requestedMode: AudioOutputMode = AudioOutputMode.Shared,
    val activeMode: AudioOutputMode = AudioOutputMode.Shared,
    val sampleRate: Int = 0,
    val periodFrames: Int = 0,
    val fallbackReason: String? = null,
    val error: String? = null,
    val underrunCount: Long = 0L,
    val streamErrorCount: Long = 0L,
    val renderDeadlineMissCount: Long = 0L,
)

data class AudioSourcePlayback(
    val sourceId: String,
    val startFrame: Long,
    val endFrameExclusive: Long,
    val gain: Float = 1f,
    val pan: Float = 0f,
    val origin: Any? = null,
)

data class AudioFileMetadata(
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val totalSamples: Long,
    val bitDepth: Int = 16,
)

/** Cross-platform output-only interface for the Echo audio engine. */
expect object Echo {
    suspend fun probeAudioFile(filePath: String): AudioFileMetadata?
    suspend fun decodeAudioFile(filePath: String, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal?
    suspend fun decodeAudioData(audioData: ByteArray, fileName: String, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal?
    fun isFormatSupported(fileName: String): Boolean
    fun getSupportedFormats(): List<String>
    fun getActiveDragFile(): String?

    /** Opens the platform output using the configured low-latency buffer size. */
    fun initialize(): Boolean
    fun setPreferredBufferFrames(frames: Int)
    fun outputDevices(): List<AudioOutputDevice>
    fun setPreferredOutputDevice(id: String?)
    fun setExclusiveMode(enabled: Boolean)
    val outputStatus: StateFlow<AudioOutputStatus>
    fun setMasterGain(gain: Float)
    fun attachAudioChain(chain: AudioChain)
    fun play(audioSignal: Signal.AudioSignal): String?
    fun playSource(
        sourceId: String,
        startFrame: Long,
        endFrameExclusive: Long,
        gain: Float = 1f,
        pan: Float = 0f,
        origin: Any? = null,
    ): String?
    fun playSources(sources: List<AudioSourcePlayback>): List<String?>
    fun playMultiple(signals: List<Signal.AudioSignal>): List<String?>
    fun update(sourceId: String, gain: Float, pan: Float)
    fun stop(sourceId: String)
    fun stopAll()
    fun stopByOrigin(origin: Any?)
    fun audioEnter(signals: List<Signal.AudioSignal>)
    fun cancel(signalOrigin: Any?)
    fun reset()
    fun shutdown()
}
