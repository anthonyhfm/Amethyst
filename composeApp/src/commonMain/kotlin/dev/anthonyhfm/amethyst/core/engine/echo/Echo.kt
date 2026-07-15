package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal

/** Cross-platform output-only interface for the Echo audio engine. */
expect object Echo {
    suspend fun decodeAudioFile(filePath: String, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal?
    suspend fun decodeAudioData(audioData: ByteArray, fileName: String, sampleStart: Long? = null, sampleEnd: Long? = null): Signal.AudioSignal?
    fun isFormatSupported(fileName: String): Boolean
    fun getSupportedFormats(): List<String>

    /** Opens the platform output. Mobile stubs return false. */
    fun initialize(): Boolean
    fun setPreferredBufferFrames(frames: Int)
    fun outputDevices(): List<String>
    fun setPreferredOutputDevice(name: String?)
    fun play(audioSignal: Signal.AudioSignal): String?
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
