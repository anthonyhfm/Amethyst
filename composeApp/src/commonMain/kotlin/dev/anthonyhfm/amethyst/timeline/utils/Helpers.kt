package dev.anthonyhfm.amethyst.timeline.utils

import dev.anthonyhfm.amethyst.timeline.data.endTimeUs
import dev.anthonyhfm.amethyst.timeline.data.msToUs
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import kotlin.math.roundToLong

// ─── Coordinate System Conventions ────────────────────────────────────────────
//
// All interaction math is driven by EditorViewportState projections:
//
//   Screen-space:   Pixel offset from the visible LEFT edge of the viewport.
//                   This is what pointer-input handlers see on the outer Box.
//
//   Content-space:  Pixel offset from the timeline origin (time = 0).
//                   contentX = timeMs * viewport.zoomX
//
// Canonical conversions:
//   contentX  = viewport.screenToContentX(screenX)   // == screenX + viewport.scrollX
//   timeMs    = viewport.screenToTimeMs(screenX)      // == contentX / zoomX
//   screenX   = viewport.timeMsToScreenX(timeMs)      // == timeMs*zoomX - scrollX
//
// Use the viewport helpers everywhere instead of raw `+ scrollOffsetPx` math to
// ensure hit-testing and rendering always share the same projection.
//
// ──────────────────────────────────────────────────────────────────────────────

internal fun trackIndexOf(track: TimelineTrack<*>): Int = TimelineRepository.tracks.value.indexOf(track)

internal fun isPointInsideAnyEntry(track: TimelineTrack<*>, timeMs: Long): Boolean = when (track) {
    is AudioTimelineTrack -> {
        val timeUs = msToUs(timeMs)
        track.entries.values.any { timeUs in it.startTimeUs..it.endTimeUs }
    }
    is MidiTimelineTrack -> track.entries.values.any { timeMs in it.startTimeMs..it.endTimeMs }
    else -> false
}

/**
 * Converts a CONTENT-SPACE x position to milliseconds with threshold-based grid snap.
 *
 * Use this when you have already obtained the content-x via [EditorViewportState.screenToContentX],
 * or for clip-body drag where `contentX = clipStartMs * zoom + clip_local_offset`.
 *
 * Prefer [computeSnappedTimeFromViewport] when you have the full [EditorViewportState] available.
 */
internal fun computeSnappedTimeFromContentX(x: Float, zoomLevel: Float, bpm: Double, gridType: GridUtils.GridType): Long {
    val rawPx = x.toDouble()
    val rawTimeMsDouble = if (zoomLevel > 0f) rawPx / zoomLevel.toDouble() else 0.0
    val rawTimeMs = rawTimeMsDouble.roundToLong().coerceAtLeast(0L)
    val intervals = GridUtils.computeWithGridType(zoomLevel, bpm, gridType)
    val gridIntervalMs = intervals.intervalMs
    val gridPxSpacing = gridIntervalMs * zoomLevel
    val snapThresholdPx = (gridPxSpacing * 0.40f).coerceAtLeast(6f)
    val shouldSnap = gridIntervalMs > 0 && gridPxSpacing >= 6f
    return if (shouldSnap) GridUtils.snapToGridWithThreshold(rawTimeMs, zoomLevel, bpm, gridType, thresholdPx = snapThresholdPx) else rawTimeMs
}

/**
 * Converts a SCREEN-SPACE x pointer position to milliseconds using [viewport] projections,
 * with threshold-based grid snap applied.
 *
 * Use this in outer-Box pointer handlers where `pos.x` is viewport/screen relative.
 * Replaces the former `computeStrictGridTime(pos.x, scrollOffsetPx, …)` pattern.
 */
internal fun computeSnappedTimeFromViewport(screenX: Float, viewport: EditorViewportState, bpm: Double, gridType: GridUtils.GridType): Long {
    return computeSnappedTimeFromContentX(
        x = viewport.screenToContentX(screenX),
        zoomLevel = viewport.zoomX,
        bpm = bpm,
        gridType = gridType
    )
}

/**
 * Hit-tests a pointer click against clip header areas in the given [track].
 *
 * [x] must be in **CONTENT-SPACE** — obtain via [EditorViewportState.screenToContentX].
 * Clip boundaries are also computed in content-space (`startTimeMs * zoom`).
 *
 * Returns the `startTimeMs` of the hit clip, or `null` if none is hit.
 */
internal fun findHeaderEntryHit(
    track: TimelineTrack<*>,
    x: Float,
    y: Float,
    zoom: Float,
    headerHeightPx: Float
): Long? {
    if (y > headerHeightPx) return null

    return when (track) {
        is AudioTimelineTrack -> track.entries.values.firstOrNull { entry ->
            val left = (entry.startTimeUs.toDouble() / 1000.0) * zoom.toDouble()
            val right = (entry.endTimeUs.toDouble() / 1000.0) * zoom.toDouble()
            x.toDouble() in left..right
        }?.startTimeMs

        is MidiTimelineTrack -> track.entries.values.firstOrNull { entry ->
            val left = entry.startTimeMs.toDouble() * zoom.toDouble()
            val right = left + entry.durationMs.toDouble() * zoom.toDouble()
            x.toDouble() in left..right
        }?.startTimeMs

        else -> null
    }
}
