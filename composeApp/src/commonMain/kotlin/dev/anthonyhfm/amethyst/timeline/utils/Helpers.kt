package dev.anthonyhfm.amethyst.timeline.utils

import androidx.compose.foundation.ScrollState
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import kotlin.math.roundToLong

internal fun trackIndexOf(track: TimelineTrack<*>): Int = TimelineRepository.tracks.value.indexOf(track)

internal fun isPointInsideAnyEntry(track: TimelineTrack<*>, timeMs: Long): Boolean = when (track) {
    is AudioTimelineTrack -> track.entries.values.any { timeMs in it.startTimeMs..(it.startTimeMs + it.durationMs) }
    is MidiTimelineTrack -> track.entries.values.any { timeMs in it.startTimeMs..it.endTimeMs }
    is LightsTimelineTrack -> track.entries.values.any { timeMs in it.startTimeMs..it.endTimeMs }
    else -> false
}

internal fun computeSnappedTime(x: Float, zoomLevel: Float, bpm: Double, gridType: GridUtils.GridType): Long {
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

internal fun computeStrictGridTime(x: Float, scrollState: ScrollState, zoomLevel: Float, bpm: Double, gridType: GridUtils.GridType): Long {
    val rawPx = scrollState.value.toDouble() + x.toDouble()
    val rawTimeMsDouble = if (zoomLevel > 0f) rawPx / zoomLevel.toDouble() else 0.0
    val rawTimeMs = rawTimeMsDouble.roundToLong().coerceAtLeast(0L)
    return GridUtils.snapToGrid(rawTimeMs, zoomLevel, bpm, gridType)
}

internal fun findHeaderEntryHit(
    track: TimelineTrack<*>,
    x: Float,
    y: Float,
    zoom: Float,
    scrollPx: Float,
    headerHeightPx: Float
): Long? {
    if (y > headerHeightPx) return null
    return when (track) {
        is AudioTimelineTrack -> track.entries.values.firstOrNull { entry ->
            val left = entry.startTimeMs.toDouble() * zoom.toDouble() - scrollPx.toDouble()
            val right = left + entry.durationMs.toDouble() * zoom.toDouble()
            x.toDouble() in left..right
        }?.startTimeMs
        is MidiTimelineTrack -> track.entries.values.firstOrNull { entry ->
            val left = entry.startTimeMs.toDouble() * zoom.toDouble() - scrollPx.toDouble()
            val right = left + entry.durationMs.toDouble() * zoom.toDouble()
            x.toDouble() in left..right
        }?.startTimeMs
        is LightsTimelineTrack -> track.entries.values.firstOrNull { entry ->
            val left = entry.startTimeMs.toDouble() * zoom.toDouble() - scrollPx.toDouble()
            val right = left + entry.durationMs.toDouble() * zoom.toDouble()
            x.toDouble() in left..right
        }?.startTimeMs
        else -> null
    }
}
