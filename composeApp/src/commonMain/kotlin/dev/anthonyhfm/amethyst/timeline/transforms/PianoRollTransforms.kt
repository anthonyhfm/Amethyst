package dev.anthonyhfm.amethyst.timeline.transforms

import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToLong

object PianoRollTransforms {

    // --- Length ---

    fun doubleLength(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(durationMs = it.durationMs * 2) }

    fun halveLength(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(durationMs = maxOf(1L, it.durationMs / 2)) }

    /** Compress all start times toward the selection start and halve all durations (2× playback speed). */
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

    /** Expand all start times away from the selection start and double all durations (½ playback speed). */
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

    // --- Time ---

    /**
     * Maps a fraction through an invertible power curve: f(x) = x^(e^factor).
     * The inverse is simply f(x) = x^(e^(-factor)), so pinch(p) followed by pinch(-p) = identity.
     * Bilateral mode applies the curve symmetrically from the midpoint — also invertible.
     */
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

    /**
     * Remaps all note positions (start and end) through an invertible power curve.
     * pinch(x) followed by pinch(-x) returns to the original exactly.
     * pinch > 0 = cluster toward start, pinch < 0 = spread toward end.
     * bilateral = apply curve symmetrically from the midpoint outward.
     */
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

    // --- Grid (pitch axis): x = pitch%10, y = pitch/10; pitch = x + y*10 ---
    // Up/Down move one row (y±1 = pitch±10). Left/Right move one column (x±1 = pitch±1).

    fun shiftUp(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(pitch = it.pitch + 10) }

    fun shiftDown(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(pitch = it.pitch - 10) }

    fun shiftLeft(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(pitch = it.pitch - 1) }

    fun shiftRight(notes: List<MidiNote>): List<MidiNote> =
        notes.map { it.copy(pitch = it.pitch + 1) }

    // --- Spatial transforms (bounding-box relative) ---
    // Coordinate system: col = pitch%10 = x (0=left, 9=right),
    //                    row = pitch/10 = y (0=bottom, 9=top) — y goes UP, matching the Launchpad.
    // Bounding box: minCol..maxCol, minRow..maxRow of the selection.
    // Relative coords: relCol = col - minCol, relRow = row - minRow
    // New pitch = (minCol + newRelCol) + (minRow + newRelRow) * 10

    fun rotateCW(notes: List<MidiNote>): List<MidiNote> {
        if (notes.isEmpty()) return notes
        val cols = notes.map { it.pitch % 10 }
        val rows = notes.map { it.pitch / 10 }
        val minCol = cols.min(); val minRow = rows.min()
        val maxRelCol = cols.max() - minCol
        val maxRelRow = rows.max() - minRow
        return notes.map { note ->
            val relCol = note.pitch % 10 - minCol
            val relRow = note.pitch / 10 - minRow
            // CW in y-up: (relCol, relRow) → (relRow, maxRelCol - relCol)
            // Verify: TL(0, maxRelRow) → (maxRelRow, maxRelCol) = TR ✓
            val newRelCol = relRow
            val newRelRow = maxRelCol - relCol
            val newPitch = (minCol + newRelCol) + (minRow + newRelRow) * 10
            note.copy(pitch = newPitch)
        }
    }

    fun rotateCCW(notes: List<MidiNote>): List<MidiNote> {
        if (notes.isEmpty()) return notes
        val cols = notes.map { it.pitch % 10 }
        val rows = notes.map { it.pitch / 10 }
        val minCol = cols.min(); val minRow = rows.min()
        val maxRelCol = cols.max() - minCol
        val maxRelRow = rows.max() - minRow
        return notes.map { note ->
            val relCol = note.pitch % 10 - minCol
            val relRow = note.pitch / 10 - minRow
            // CCW in y-up: (relCol, relRow) → (maxRelRow - relRow, relCol)
            // Verify: TL(0, maxRelRow) → (0, 0) = BL ✓
            val newRelCol = maxRelRow - relRow
            val newRelRow = relCol
            val newPitch = (minCol + newRelCol) + (minRow + newRelRow) * 10
            note.copy(pitch = newPitch)
        }
    }

    fun rotate180(notes: List<MidiNote>): List<MidiNote> {
        if (notes.isEmpty()) return notes
        val cols = notes.map { it.pitch % 10 }
        val rows = notes.map { it.pitch / 10 }
        val minCol = cols.min(); val minRow = rows.min()
        val maxRelCol = cols.max() - minCol
        val maxRelRow = rows.max() - minRow
        return notes.map { note ->
            val relCol = note.pitch % 10 - minCol
            val relRow = note.pitch / 10 - minRow
            val newRelCol = maxRelCol - relCol
            val newRelRow = maxRelRow - relRow
            val newPitch = (minCol + newRelCol) + (minRow + newRelRow) * 10
            note.copy(pitch = newPitch)
        }
    }

    fun mirrorHorizontal(notes: List<MidiNote>): List<MidiNote> {
        if (notes.isEmpty()) return notes
        val cols = notes.map { it.pitch % 10 }
        val minCol = cols.min()
        val maxRelCol = cols.max() - minCol
        return notes.map { note ->
            val relCol = note.pitch % 10 - minCol
            val row = note.pitch / 10
            val newPitch = (minCol + maxRelCol - relCol) + row * 10
            note.copy(pitch = newPitch)
        }
    }

    fun mirrorVertical(notes: List<MidiNote>): List<MidiNote> {
        if (notes.isEmpty()) return notes
        val rows = notes.map { it.pitch / 10 }
        val minRow = rows.min()
        val maxRelRow = rows.max() - minRow
        return notes.map { note ->
            val col = note.pitch % 10
            val relRow = note.pitch / 10 - minRow
            val newPitch = col + (minRow + maxRelRow - relRow) * 10
            note.copy(pitch = newPitch)
        }
    }

    // --- Color ---

    /** Distributes gradient colors across notes sorted by startTimeMs. Each note becomes solid. */
    fun gradientSpread(notes: List<MidiNote>, gradientStops: List<NoteGradientStop>): List<MidiNote> {
        if (notes.size < 2 || gradientStops.size < 2) return notes
        val sorted = notes.sortedBy { it.startTimeMs }
        return sorted.mapIndexed { i, note ->
            val t = i.toFloat() / (sorted.size - 1).toFloat()
            val (r, g, b) = GradientInterpolator.interpolate(gradientStops, t)
            note.copy(led = note.led.copy(red = r, green = g, blue = b, gradient = null))
        }
    }

    /** Assigns random colors from colorPool to each note. */
    fun randomizeColors(notes: List<MidiNote>, colorPool: List<Triple<Float, Float, Float>>): List<MidiNote> {
        if (colorPool.isEmpty()) return notes
        return notes.map { note ->
            val (r, g, b) = colorPool.random()
            note.copy(led = note.led.copy(red = r, green = g, blue = b, gradient = null))
        }
    }
}
