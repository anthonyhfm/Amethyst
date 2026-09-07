package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import kotlin.math.floor
import kotlin.math.roundToInt

internal const val DEFAULT_MS_PER_BEAT: Double = 500.0

internal fun millisecondsPerBeat(bpm: Double): Double = 60_000.0 / bpm.coerceAtLeast(1.0)

internal class PianoRollMetrics(
    val totalPitches: Int,
    val noteHeightDp: Dp,
    val zoomX: Float,
    private val density: Density,
    private val gridResolution: GridResolution,
    val beatDurationMs: Double = DEFAULT_MS_PER_BEAT,
    /** How many ms before t=0 the canvas starts. Shifts all x positions right by this amount. */
    val oobOffsetMs: Long = 0L
) {
    val noteHeightPx: Float = with(density) { noteHeightDp.toPx() }
    val canvasHeightPx: Float = totalPitches * noteHeightPx
    val pixelsPerBeatPx: Float = zoomX * beatDurationMs.toFloat()

    /** Hit-testing height for note rects — matches the actually rendered [NoteBox] height
     *  (which uses the full row height) so tools relying on rect-based hit-testing
     *  (e.g. direct editing and marquee selection) never miss the bottom band of a note. */
    val noteRenderHeightPx: Float = noteHeightPx

    fun pitchToYPx(pitch: Int): Float = (totalPitches - 1 - pitch) * noteHeightPx

    fun yPxToPitch(y: Float): Int =
        (totalPitches - 1 - (y / noteHeightPx).toInt()).coerceIn(0, totalPitches - 1)

    fun timeMsToXPx(startTimeMs: Long): Float =
        ((startTimeMs + oobOffsetMs) / beatDurationMs.toFloat()) * pixelsPerBeatPx

    fun durationMsToWidthPx(durationMs: Long): Float = (durationMs / beatDurationMs.toFloat()) * pixelsPerBeatPx

    /** Snaps to the *nearest* grid boundary — used for cursor and ruler placement. */
    fun xPxToTimeMs(x: Float): Long {
        val beatTime = x / pixelsPerBeatPx
        val snappedBeatTime = (beatTime * gridResolution.snapDivisionsPerBeat).roundToInt() /
            gridResolution.snapDivisionsPerBeat.toFloat()
        return (snappedBeatTime * beatDurationMs).toLong() - oobOffsetMs
    }

    /** Snaps to the *start of the grid cell that contains x* — used for note creation so the
     *  note always lands in the cell the user clicked, never jumping to the next cell. */
    fun xPxToNotePlacementMs(x: Float): Long {
        val beatTime = x / pixelsPerBeatPx
        val snappedBeatTime = floor(beatTime * gridResolution.snapDivisionsPerBeat) /
            gridResolution.snapDivisionsPerBeat.toFloat()
        return (snappedBeatTime * beatDurationMs).toLong() - oobOffsetMs
    }
}
