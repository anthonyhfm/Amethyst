package dev.anthonyhfm.amethyst.core.midi.data

data class MidiEffectData(
    val x: Int,
    val y: Int,
    val r: Int,
    val g: Int,
    val b: Int
) {
    fun isEmpty(): Boolean {
        return r == 0 && g == 0 && b == 0
    }
}