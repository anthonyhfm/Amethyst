package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal

expect object AudioOutput {
    fun play(audioSignal: Signal.AudioSignal): String?
    fun playMultiple(signals: List<Signal.AudioSignal>): List<String?>
    fun update(sourceId: String, gain: Float, pan: Float)
    fun stop(sourceId: String)
    fun stopAll()
    fun stopByOrigin(origin: Any?)
}
