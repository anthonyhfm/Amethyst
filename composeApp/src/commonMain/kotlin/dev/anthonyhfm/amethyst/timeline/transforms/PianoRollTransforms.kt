package dev.anthonyhfm.amethyst.timeline.transforms

import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToLong

object PianoRollTransforms {

    fun doubleLength(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(durationMs = it.durationMs * 2) }

    fun halveLength(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(durationMs = maxOf(1L, it.durationMs / 2)) }

    fun doubleSpeed(notes: List<MidiNote>): List<MidiNote> {
        if (notes.isEmpty()) return notes
        val origin = notes.minOf { it.startTimeMs }
        return notes.map {
            it.copy(
                startTimeMs = origin + (it.startTimeMs - origin) / 2,
                durationMs = maxOf(1L, it.durationMs / 2)
            )
        }
    }

    fun halveSpeed(notes: List<MidiNote>): List<MidiNote> {
        if (notes.isEmpty()) return notes
        val origin = notes.minOf { it.startTimeMs }
        return notes.map {
            it.copy(
                startTimeMs = origin + (it.startTimeMs - origin) * 2,
                durationMs = it.durationMs * 2
            )
        }
    }

    private fun mapPinchFraction(fraction: Double, exponent: Double, bilateral: Boolean): Double {
        return if (bilateral) {
            if (fraction < 0.5) {
                mapPinchFraction(fraction * 2.0, exponent, false) * 0.5
            } else {
                1.0 - mapPinchFraction((1.0 - fraction) * 2.0, exponent, false) * 0.5
            }
        } else {
            fraction.pow(exponent)
        }
    }

    fun pinch(notes: List<MidiNote>, factor: Float, bilateral: Boolean = false): List<MidiNote> {
        if (notes.isEmpty()) return notes
        val spanStart = notes.minOf { it.startTimeMs }
        val spanEnd = notes.maxOf { it.startTimeMs + it.durationMs }
        val span = (spanEnd - spanStart).toDouble()
        if (span <= 0.0) return notes
        val exponent = exp(factor.toDouble()) // e^factor; inverse = e^(-factor) = 1/exponent

        fun mapMs(ms: Long): Long {
            val fraction = ((ms - spanStart).toDouble() / span).coerceIn(0.0, 1.0)
            return (spanStart + mapPinchFraction(fraction, exponent, bilateral) * span).roundToLong()
        }

        return notes.map { note ->
            val newStart = mapMs(note.startTimeMs)
            val newEnd = mapMs(note.startTimeMs + note.durationMs)
            note.copy(startTimeMs = newStart, durationMs = (newEnd - newStart).coerceAtLeast(1L))
        }
    }

    fun shiftUp(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(pitch = it.pitch + 10) }

    fun shiftDown(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(pitch = it.pitch - 10) }

    fun shiftLeft(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(pitch = it.pitch - 1) }

    fun shiftRight(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(pitch = it.pitch + 1) }

    private fun transformPitch(pitch: Int, transform: (col: Int, row: Int) -> Pair<Int, Int>): Int {
        val basePitch = pitch - (pitch % 100)
        val localPitch = pitch % 100
        val col = localPitch % 10
        val row = localPitch / 10
        val (newCol, newRow) = transform(col, row)
        val clampedCol = newCol.coerceIn(0, 9)
        val clampedRow = newRow.coerceIn(0, 9)
        return basePitch + clampedCol + clampedRow * 10
    }

    fun rotateCW(notes: List<MidiNote>): List<MidiNote> {
        return notes.map { note ->
            note.copy(pitch = transformPitch(note.pitch) { col, row -> Pair(row, 9 - col) })
        }
    }

    fun rotateCCW(notes: List<MidiNote>): List<MidiNote> {
        return notes.map { note ->
            note.copy(pitch = transformPitch(note.pitch) { col, row -> Pair(9 - row, col) })
        }
    }

    fun rotate180(notes: List<MidiNote>): List<MidiNote> {
        return notes.map { note ->
            note.copy(pitch = transformPitch(note.pitch) { col, row -> Pair(9 - col, 9 - row) })
        }
    }

    fun mirrorHorizontal(notes: List<MidiNote>): List<MidiNote> {
        return notes.map { note ->
            note.copy(pitch = transformPitch(note.pitch) { col, row -> Pair(9 - col, row) })
        }
    }

    fun mirrorVertical(notes: List<MidiNote>): List<MidiNote> {
        return notes.map { note ->
            note.copy(pitch = transformPitch(note.pitch) { col, row -> Pair(col, 9 - row) })
        }
    }

    fun gradientSpread(notes: List<MidiNote>, gradientStops: List<NoteGradientStop>): List<MidiNote> {
        if (notes.size < 2 || gradientStops.size < 2) return notes
        val sorted = notes.sortedBy { it.startTimeMs }
        return sorted.mapIndexed { i, note ->
            val t = i.toFloat() / (sorted.size - 1).toFloat()
            val (r, g, b) = GradientInterpolator.interpolate(gradientStops, t)
            note.copy(led = note.led.copy(red = r, green = g, blue = b, gradient = null))
        }
    }
}
