package dev.anthonyhfm.amethyst.timeline.automation

import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineAutomationEvaluatorTest {
    @Test
    fun evaluateVolumeAutomationInterpolatesAndFallsBackToBaseVolume() {
        val track = AudioTimelineTrack().apply {
            volume = 0.6f
            automationLanes += TimelineAutomationLane(
                target = TimelineTrackAutomationTarget.VOLUME,
                points = listOf(
                    TimelineAutomationPoint(timeMs = 1_000L, value = 0.2f),
                    TimelineAutomationPoint(timeMs = 3_000L, value = 0.8f)
                )
            )
        }

        val beforeAutomation = TimelineAutomationEvaluator.evaluate(track = track, timeMs = 0L)
        assertEquals(0.6f, beforeAutomation.volume, 0.0001f)

        val interpolated = TimelineAutomationEvaluator.evaluate(track = track, timeMs = 2_000L)
        assertEquals(0.5f, interpolated.volume, 0.0001f)

        track.automationLanes[0] = track.automationLanes[0].copy(enabled = false)
        val bypassed = TimelineAutomationEvaluator.evaluate(track = track, timeMs = 2_000L)
        assertEquals(0.6f, bypassed.volume, 0.0001f)
    }
}
