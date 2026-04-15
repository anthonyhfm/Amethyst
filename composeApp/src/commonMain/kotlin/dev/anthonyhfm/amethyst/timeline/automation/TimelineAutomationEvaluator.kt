package dev.anthonyhfm.amethyst.timeline.automation

import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget

data class TimelineTrackAutomationState(
    val volume: Float = TimelineTrackAutomationTarget.VOLUME.defaultValue,
) {
    operator fun get(target: TimelineTrackAutomationTarget): Float = when (target) {
        TimelineTrackAutomationTarget.VOLUME -> volume
    }
}

object TimelineAutomationEvaluator {
    fun isPlaybackEnabled(
        track: TimelineTrack<*>,
        anySoloedTrack: Boolean,
    ): Boolean {
        if (track.isMuted) return false
        return !anySoloedTrack || track.isSoloed
    }

    fun evaluate(
        track: TimelineTrack<*>,
        timeMs: Long,
    ): TimelineTrackAutomationState {
        return TimelineTrackAutomationState(
            volume = TimelineTrackAutomationTarget.VOLUME.normalizeValue(
                track.automationValueAt(TimelineTrackAutomationTarget.VOLUME, timeMs)
            )
        )
    }
}
