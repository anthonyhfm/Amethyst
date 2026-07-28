package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.flow.StateFlow

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
    val backend: String = "",
    val requestedMode: AudioOutputMode = AudioOutputMode.Shared,
    val activeMode: AudioOutputMode = AudioOutputMode.Shared,
    val sampleRate: Int = 0,
    val periodFrames: Int = 0,
    val fallbackReason: String? = null,
    val error: String? = null,
)

data class AudioSourcePlayback(
    val sourceId: String,
    val startFrame: Long,
    val endFrameExclusive: Long,
    val gain: Float = 1f,
    val pan: Float = 0f,
    val origin: Any? = null,
)

/** Cross-platform output-only interface for the Echo audio engine. */
expect object Echo {
    suspend fun decodeAudioFile(filePath: String, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal?
    suspend fun decodeAudioData(audioData: ByteArray, fileName: String, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal?
    fun isFormatSupported(fileName: String): Boolean
    fun getSupportedFormats(): List<String>

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
