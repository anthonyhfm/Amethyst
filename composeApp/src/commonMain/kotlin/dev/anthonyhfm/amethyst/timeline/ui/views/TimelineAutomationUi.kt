package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget

internal val TimelineAutomationLaneRowHeight: Dp = 96.dp
internal val TimelineAutomationLaneRowSpacing: Dp = 4.dp

internal fun TimelineTrack<*>.visibleAutomationLanes(): List<TimelineAutomationLane> {
    return automationLanes.filter(TimelineAutomationLane::visible)
}

internal fun TimelineTrack<*>.automationLaneBaseValue(lane: TimelineAutomationLane): Float {
    return baseAutomationValue(
        target = lane.target,
        bindingId = lane.bindingId
    )
}

internal fun TimelineTrack<*>.automationLaneLabel(
    lane: TimelineAutomationLane,
    allTracks: List<TimelineTrack<*>>
): String {
    return when (lane.target) {
        TimelineTrackAutomationTarget.VOLUME -> "Volume"
    }
}

internal fun timelineTrackDisplayName(
    track: TimelineTrack<*>,
    trackIndex: Int,
    allTracks: List<TimelineTrack<*>>
): String {
    return track.name.takeIf(String::isNotBlank) ?: when (track) {
        is AudioTimelineTrack -> "Audio Track ${trackIndex + 1}"
        is MidiTimelineTrack -> "Midi Track ${trackIndex + 1}"
        else -> "Track ${trackIndex + 1}"
    }
}

internal fun formatAutomationValue(
    target: TimelineTrackAutomationTarget,
    value: Float
): String {
    return target.formatValue(value)
}
