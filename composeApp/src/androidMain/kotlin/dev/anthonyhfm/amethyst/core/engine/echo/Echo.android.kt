package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal

/** Echo has intentionally no mobile backend yet. */
actual object Echo {
    actual suspend fun decodeAudioFile(filePath: String, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? = null
    actual suspend fun decodeAudioData(audioData: ByteArray, fileName: String, sampleStart: Long?, sampleEnd: Long?): Signal.AudioSignal? = null
    actual fun isFormatSupported(fileName: String) = false
    actual fun getSupportedFormats() = emptyList<String>()
    actual fun initialize() = false
    actual fun setPreferredBufferFrames(frames: Int) = Unit
    actual fun outputDevices() = emptyList<String>()
    actual fun setPreferredOutputDevice(name: String?) = Unit
    actual fun play(audioSignal: Signal.AudioSignal): String? = null
    actual fun playMultiple(signals: List<Signal.AudioSignal>): List<String?> = List(signals.size) { null }
    actual fun update(sourceId: String, gain: Float, pan: Float) = Unit
    actual fun stop(sourceId: String) = Unit
    actual fun stopAll() = Unit
    actual fun stopByOrigin(origin: Any?) = Unit
    actual fun audioEnter(signals: List<Signal.AudioSignal>) = Unit
    actual fun cancel(signalOrigin: Any?) = Unit
    actual fun reset() = Unit
    actual fun shutdown() = Unit
}
