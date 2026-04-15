package dev.anthonyhfm.amethyst.timeline.data

data class TimelineTrackRow(
    val trackIndex: Int,
    val track: TimelineTrack<*>,
    val nestingLevel: Int = 0,
)

fun List<TimelineTrack<*>>.timelineTrackRows(
    includeCollapsedChildren: Boolean = false
): List<TimelineTrackRow> {
    return mapIndexed { index, track ->
        TimelineTrackRow(trackIndex = index, track = track)
    }
}

fun List<TimelineTrack<*>>.trackById(trackId: String?): TimelineTrack<*>? {
    if (trackId == null) return null
    return firstOrNull { it.trackId == trackId }
}
