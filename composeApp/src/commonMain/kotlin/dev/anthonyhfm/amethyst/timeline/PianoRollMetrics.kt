package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import kotlin.math.floor
import kotlin.math.roundToInt

internal const val MS_PER_BEAT: Long = 500L

internal class PianoRollMetrics(
    val totalPitches: Int,
    val noteHeightDp: Dp,
    val zoomX: Float,
    private val density: Density,
    private val gridResolution: GridResolution,
    /** How many ms before t=0 the canvas starts. Shifts all x positions right by this amount. */
    val oobOffsetMs: Long = 0L
) {
    val noteHeightPx: Float = with(density) { noteHeightDp.toPx() }
    val pixelsPerBeatPx: Float = zoomX * MS_PER_BEAT
    val noteRenderHeightPx: Float = noteHeightPx - 4f

    fun pitchToYPx(pitch: Int): Float = (totalPitches - 1 - pitch) * noteHeightPx

    fun yPxToPitch(y: Float): Int =
        (totalPitches - 1 - (y / noteHeightPx).toInt()).coerceIn(0, totalPitches - 1)

    fun timeMsToXPx(startTimeMs: Long): Float =
        ((startTimeMs + oobOffsetMs) / MS_PER_BEAT.toFloat()) * pixelsPerBeatPx

    fun durationMsToWidthPx(durationMs: Long): Float = (durationMs / MS_PER_BEAT.toFloat()) * pixelsPerBeatPx

    /** Snaps to the *nearest* grid boundary — used for cursor and ruler placement. */
    fun xPxToTimeMs(x: Float): Long {
        val beatTime = x / pixelsPerBeatPx
        val snappedBeatTime = (beatTime * gridResolution.snapDivisionsPerBeat).roundToInt() /
            gridResolution.snapDivisionsPerBeat.toFloat()
        return (snappedBeatTime * MS_PER_BEAT).toLong() - oobOffsetMs
    }

    /** Snaps to the *start of the grid cell that contains x* — used for note creation so the
     *  note always lands in the cell the user clicked, never jumping to the next cell. */
    fun xPxToNotePlacementMs(x: Float): Long {
        val beatTime = x / pixelsPerBeatPx
        val snappedBeatTime = floor(beatTime * gridResolution.snapDivisionsPerBeat) /
            gridResolution.snapDivisionsPerBeat.toFloat()
        return (snappedBeatTime * MS_PER_BEAT).toLong() - oobOffsetMs
    }
}
