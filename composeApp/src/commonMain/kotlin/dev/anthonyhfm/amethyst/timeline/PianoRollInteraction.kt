package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal sealed interface PianoRollHitTarget {
    data object Empty : PianoRollHitTarget
    data class NoteBody(val note: MidiNote) : PianoRollHitTarget
    data class ResizeLeft(val note: MidiNote) : PianoRollHitTarget
    data class ResizeRight(val note: MidiNote) : PianoRollHitTarget
}

internal data class PianoRollNoteRect(
    val note: MidiNote,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float
        get() = left + width

    val bottom: Float
        get() = top + height

    fun contains(point: Offset): Boolean =
        point.x in left..right && point.y in top..bottom
}

internal data class PianoRollDraftSpan(
    val startTimeMs: Long,
    val durationMs: Long,
)

internal fun findPianoRollHitTarget(
    point: Offset,
    noteRects: List<PianoRollNoteRect>,
    resizeHandleWidthPx: Float = 6f,
): PianoRollHitTarget {
    val hitRect = noteRects.firstOrNull { it.contains(point) } ?: return PianoRollHitTarget.Empty
    val handleWidth = resizeHandleWidthPx.coerceAtLeast(0f)
    return when {
        point.x <= hitRect.left + handleWidth -> PianoRollHitTarget.ResizeLeft(hitRect.note)
        point.x >= hitRect.right - handleWidth -> PianoRollHitTarget.ResizeRight(hitRect.note)
        else -> PianoRollHitTarget.NoteBody(hitRect.note)
    }
}

internal fun resolveDraftSpan(
    anchorCellStartMs: Long,
    currentCellStartMs: Long,
    cellDurationMs: Long,
): PianoRollDraftSpan {
    val safeCellDurationMs = cellDurationMs.coerceAtLeast(1L)
    val startTimeMs = min(anchorCellStartMs, currentCellStartMs)
    val endTimeMs = max(anchorCellStartMs, currentCellStartMs) + safeCellDurationMs
    return PianoRollDraftSpan(
        startTimeMs = startTimeMs,
        durationMs = (endTimeMs - startTimeMs).coerceAtLeast(safeCellDurationMs),
    )
}

/**
 * Returns a viewport-relative X coordinate for the cursor.
 *
 * Both [trackedPointerX] (from the move-tracking pointerInput) and [eventPointerX] (from the
 * scroll event) are in the VIEWPORT coordinate system because neither pointer handler sits inside
 * a horizontalScroll modifier anymore.  The preferred value is [trackedPointerX] because it was
 * last updated on a pointer-move event and therefore leads to more accurate anchoring.
 */
internal fun resolveViewportRelativeCursorX(
    trackedPointerX: Float?,
    eventPointerX: Float?,
): Float = trackedPointerX ?: (eventPointerX ?: 0f)

// ── Viewport-aware snapping ───────────────────────────────────────────────────

/**
 * Snaps [clipTimeMs] to the nearest grid boundary defined by [resolution].
 *
 * Uses [MS_PER_BEAT]-based cells so the result aligns with the visual grid drawn
 * by the renderer (which also uses the fixed [MS_PER_BEAT] constant).
 */
internal fun snapClipTimeToGrid(clipTimeMs: Double, resolution: GridResolution): Long {
    val n = resolution.snapDivisionsPerBeat
    val beatFraction = clipTimeMs / MS_PER_BEAT.toDouble()
    val snapped = kotlin.math.round(beatFraction * n) / n.toDouble()
    return (snapped * MS_PER_BEAT).toLong()
}

/**
 * Floors [clipTimeMs] to the start of the grid cell containing it.
 *
 * Used by the DRAW tool so a new note anchors to the cell the user clicked,
 * matching the floor behaviour of [PianoRollMetrics.xPxToNotePlacementMs].
 */
internal fun floorClipTimeToGrid(clipTimeMs: Double, resolution: GridResolution): Long {
    val n = resolution.snapDivisionsPerBeat
    val beatFraction = clipTimeMs / MS_PER_BEAT.toDouble()
    val floored = floor(beatFraction * n) / n.toDouble()
    return (floored * MS_PER_BEAT).toLong()
}

internal fun cellDurationAt(cellStartMs: Long, resolution: GridResolution): Long {
    val n = resolution.snapDivisionsPerBeat
    val currentBeatFraction = cellStartMs / MS_PER_BEAT.toDouble()
    val k = kotlin.math.round(currentBeatFraction * n)
    val nextCellStartMs = (((k + 1) * MS_PER_BEAT) / n.toDouble()).toLong()
    return nextCellStartMs - cellStartMs
}

/**
 * Moves [clipTimeMs] by exactly one grid cell of [resolution] in the given [direction]
 * (`+1` = forward/right, `-1` = backward/left), always landing exactly on a grid boundary —
 * even if [clipTimeMs] itself was off-grid to begin with.
 *
 * Used to grid-align keyboard-driven (arrow key) playhead nudging in the piano roll,
 * matching the same [MS_PER_BEAT]-based grid the renderer and note-drawing tools use.
 */
internal fun stepClipTimeOnGrid(clipTimeMs: Long, resolution: GridResolution, direction: Int): Long {
    val n = resolution.snapDivisionsPerBeat
    val beatFraction = clipTimeMs / MS_PER_BEAT.toDouble()
    val k = beatFraction * n
    val epsilon = 1e-6
    val steppedK = if (direction >= 0) {
        floor(k + epsilon) + 1
    } else {
        ceil(k - epsilon) - 1
    }
    return ((steppedK / n.toDouble()) * MS_PER_BEAT).toLong()
}

/**
 * Builds [PianoRollNoteRect] instances in **screen-space** (viewport coordinates).
 *
 * Use when the pointer-input handler is attached to the outer viewport box so that
 * pointer-event positions (screen-space) and note rectangles share the same coordinate
 * space, making hit-testing reliable and consistent with the renderer's viewport model.
 */
internal fun buildNoteRectsScreenSpace(
    notes: List<MidiNote>,
    metrics: PianoRollMetrics,
    viewport: EditorViewportState,
): List<PianoRollNoteRect> = notes.map { note ->
    PianoRollNoteRect(
        note = note,
        left = viewport.contentToScreenX(metrics.timeMsToXPx(note.startTimeMs)),
        top = metrics.pitchToYPx(note.pitch),
        width = metrics.durationMsToWidthPx(note.durationMs),
        height = metrics.noteRenderHeightPx,
    )
}
