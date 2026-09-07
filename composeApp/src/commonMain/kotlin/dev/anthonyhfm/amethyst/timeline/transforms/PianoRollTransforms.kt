package dev.anthonyhfm.amethyst.timeline.transforms

import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import dev.anthonyhfm.amethyst.timeline.data.resolvedDeviceIndex
import dev.anthonyhfm.amethyst.timeline.data.resolvedPadIndex
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

object PianoRollTransforms {

    private fun MidiNote.withPitchAndLedIndex(newPitch: Int): MidiNote =
        copy(
            device = resolvedDeviceIndex,
            pitch = newPitch.coerceIn(0, 99),
            led = led.copy(index = newPitch.coerceIn(0, 99)),
        )

    fun doubleLength(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(durationMs = it.durationMs * 2) }

    fun halveLength(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(durationMs = maxOf(1L, it.durationMs / 2)) }

    fun setLength(notes: List<MidiNote>, durationMs: Long): List<MidiNote> =
        notes.map { it.copy(durationMs = durationMs.coerceAtLeast(1L)) }

    fun ensureMinimumLength(notes: List<MidiNote>, minimumDurationMs: Long): List<MidiNote> =
        notes.map { it.copy(durationMs = maxOf(it.durationMs, minimumDurationMs.coerceAtLeast(1L))) }

    fun scaleLength(notes: List<MidiNote>, ratio: Double): List<MidiNote> =
        notes.map { it.copy(durationMs = (it.durationMs * ratio.coerceAtLeast(0.0)).roundToLong().coerceAtLeast(1L)) }

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
        notes.map { it.withPitchAndLedIndex(transformPitch(it.resolvedPadIndex) { col, row -> col to row + 1 }) }

    fun shiftDown(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.withPitchAndLedIndex(transformPitch(it.resolvedPadIndex) { col, row -> col to row - 1 }) }

    fun shiftLeft(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.withPitchAndLedIndex(transformPitch(it.resolvedPadIndex) { col, row -> col - 1 to row }) }

    fun shiftRight(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.withPitchAndLedIndex(transformPitch(it.resolvedPadIndex) { col, row -> col + 1 to row }) }

    private fun transformPitch(pitch: Int, transform: (col: Int, row: Int) -> Pair<Int, Int>): Int {
        val col = pitch % 10
        val row = pitch / 10
        val (newCol, newRow) = transform(col, row)
        val clampedCol = newCol.coerceIn(0, 9)
        val clampedRow = newRow.coerceIn(0, 9)
        return clampedCol + clampedRow * 10
    }

    fun rotateCW(notes: List<MidiNote>): List<MidiNote> {
        return notes.map { note ->
            note.withPitchAndLedIndex(transformPitch(note.resolvedPadIndex) { col, row -> Pair(row, 9 - col) })
        }
    }

    fun rotateCCW(notes: List<MidiNote>): List<MidiNote> {
        return notes.map { note ->
            note.withPitchAndLedIndex(transformPitch(note.resolvedPadIndex) { col, row -> Pair(9 - row, col) })
        }
    }

    fun rotate180(notes: List<MidiNote>): List<MidiNote> {
        return notes.map { note ->
            note.withPitchAndLedIndex(transformPitch(note.resolvedPadIndex) { col, row -> Pair(9 - col, 9 - row) })
        }
    }

    fun mirrorHorizontal(notes: List<MidiNote>): List<MidiNote> {
        return notes.map { note ->
            note.withPitchAndLedIndex(transformPitch(note.resolvedPadIndex) { col, row -> Pair(9 - col, row) })
        }
    }

    fun mirrorVertical(notes: List<MidiNote>): List<MidiNote> {
        return notes.map { note ->
            note.withPitchAndLedIndex(transformPitch(note.resolvedPadIndex) { col, row -> Pair(col, 9 - row) })
        }
    }

    /** Keeps every occurrence of the same pad together while assigning pads to new locations. */
    fun scrambleByPad(notes: List<MidiNote>, random: Random = Random.Default): List<MidiNote> =
        notes.groupBy { it.resolvedDeviceIndex }.values.flatMap { deviceNotes ->
            val sourcePads = deviceNotes.map { it.resolvedPadIndex }.distinct().sorted()
            val targets = sourcePads.shuffled(random)
            val mapping = sourcePads.zip(targets).toMap()
            deviceNotes.map { note ->
                note.withPitchAndLedIndex(mapping.getValue(note.resolvedPadIndex))
            }
        }

    /** Randomizes each note independently while keeping it on its original device surface. */
    fun scrambleAll(notes: List<MidiNote>, random: Random = Random.Default): List<MidiNote> =
        notes.map { note ->
            note.withPitchAndLedIndex(random.nextInt(100))
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
