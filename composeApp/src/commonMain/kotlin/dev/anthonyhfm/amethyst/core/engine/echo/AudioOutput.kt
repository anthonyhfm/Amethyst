package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal

expect object AudioOutput {
    fun play(audioSignal: Signal.AudioSignal): String?
    fun stop(sourceId: String)
    fun stopAll()
    fun stopByOrigin(origin: Any?)
}
