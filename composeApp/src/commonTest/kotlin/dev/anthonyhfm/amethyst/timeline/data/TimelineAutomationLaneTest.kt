package dev.anthonyhfm.amethyst.timeline.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

class TimelineAutomationLaneTest {
    @Test
    fun valueAtInterpolatesLinearlyAndHoldsFallbackBeforeFirstPoint() {
        val lane = TimelineAutomationLane(
            target = TimelineTrackAutomationTarget.VOLUME,
            points = listOf(
                TimelineAutomationPoint(timeMs = 1_000L, value = -1f),
                TimelineAutomationPoint(timeMs = 3_000L, value = 1f)
            )
        )

        assertEquals(0f, lane.valueAt(timeMs = 0L, defaultValue = 0f), 0.0001f)
        assertEquals(-1f, lane.valueAt(timeMs = 1_000L, defaultValue = 0f), 0.0001f)
        assertEquals(0f, lane.valueAt(timeMs = 2_000L, defaultValue = 0f), 0.0001f)
        assertEquals(1f, lane.valueAt(timeMs = 3_000L, defaultValue = 0f), 0.0001f)
        assertEquals(1f, lane.valueAt(timeMs = 4_000L, defaultValue = 0f), 0.0001f)
    }

    @Test
    fun normalizedCleansLegacyBindingsAndDuplicateTimes() {
        val lane = TimelineAutomationLane(
            target = TimelineTrackAutomationTarget.VOLUME,
            bindingId = "legacy-binding",
            points = listOf(
                TimelineAutomationPoint(timeMs = -25L, value = 1.5f, pointId = ""),
                TimelineAutomationPoint(timeMs = 500L, value = 0.25f, pointId = "first"),
                TimelineAutomationPoint(timeMs = 500L, value = 0.75f, pointId = "last")
            )
        ).normalized()

        assertNull(lane.bindingId)
        assertEquals(2, lane.points.size)
        assertEquals(0L, lane.points.first().timeMs)
        assertEquals(1.5f, lane.points.first().value, 0.0001f)
        assertEquals("last", lane.points.last().pointId)
        assertEquals(0.75f, lane.points.last().value, 0.0001f)
        assertTrue(lane.points.first().pointId.isNotBlank())
    }

    @Test
    fun disabledLaneFallsBackToProvidedDefaultValue() {
        val lane = TimelineAutomationLane(
            target = TimelineTrackAutomationTarget.VOLUME,
            enabled = false,
            points = listOf(
                TimelineAutomationPoint(timeMs = 0L, value = 0.1f)
            )
        )

        assertEquals(0.42f, lane.valueAt(timeMs = 2_000L, defaultValue = 0.42f), 0.0001f)
    }

    @Test
    fun valueAtAppliesCurveStoredOnSegmentStartPoint() {
        val curvedLane = TimelineAutomationLane(
            target = TimelineTrackAutomationTarget.VOLUME,
            points = listOf(
                TimelineAutomationPoint(timeMs = 0L, value = 0f, curve = 0.75f, pointId = "start"),
                TimelineAutomationPoint(timeMs = 1_000L, value = 1f, pointId = "end")
            )
        )

        val midpoint = curvedLane.valueAt(timeMs = 500L, defaultValue = 0f)

        assertNotEquals(0.5f, midpoint, 0.0001f)
        assertTrue(midpoint > 0.5f)
    }

    @Test
    fun valueAtPassesExactlyThroughExplicitCurveHandle() {
        val curvedLane = TimelineAutomationLane(
            target = TimelineTrackAutomationTarget.VOLUME,
            points = listOf(
                TimelineAutomationPoint(
                    timeMs = 0L,
                    value = 0f,
                    curveHandleTime = 0.25f,
                    curveHandleValue = 0.75f,
                    pointId = "start"
                ),
                TimelineAutomationPoint(timeMs = 1_000L, value = 1f, pointId = "end")
            )
        )

        assertEquals(0.75f, curvedLane.valueAt(timeMs = 250L, defaultValue = 0f), 0.0001f)
    }

    @Test
    fun explicitCurveHandlesStaySymmetricForMirroredHandleValues() {
        val upperLane = TimelineAutomationLane(
            target = TimelineTrackAutomationTarget.VOLUME,
            points = listOf(
                TimelineAutomationPoint(
                    timeMs = 0L,
                    value = 0f,
                    curveHandleTime = 0.5f,
                    curveHandleValue = 0.8f,
                    pointId = "upper-start"
                ),
                TimelineAutomationPoint(timeMs = 1_000L, value = 1f, pointId = "upper-end")
            )
        )
        val lowerLane = TimelineAutomationLane(
            target = TimelineTrackAutomationTarget.VOLUME,
            points = listOf(
                TimelineAutomationPoint(
                    timeMs = 0L,
                    value = 0f,
                    curveHandleTime = 0.5f,
                    curveHandleValue = 0.2f,
                    pointId = "lower-start"
                ),
                TimelineAutomationPoint(timeMs = 1_000L, value = 1f, pointId = "lower-end")
            )
        )
        val linearLane = TimelineAutomationLane(
            target = TimelineTrackAutomationTarget.VOLUME,
            points = listOf(
                TimelineAutomationPoint(timeMs = 0L, value = 0f, pointId = "linear-start"),
                TimelineAutomationPoint(timeMs = 1_000L, value = 1f, pointId = "linear-end")
            )
        )

        assertEquals(
            linearLane.valueAt(timeMs = 250L, defaultValue = 0f) * 2f,
            upperLane.valueAt(timeMs = 250L, defaultValue = 0f) +
                lowerLane.valueAt(timeMs = 250L, defaultValue = 0f),
            0.0001f
        )
        assertEquals(
            linearLane.valueAt(timeMs = 750L, defaultValue = 0f) * 2f,
            upperLane.valueAt(timeMs = 750L, defaultValue = 0f) +
                lowerLane.valueAt(timeMs = 750L, defaultValue = 0f),
            0.0001f
        )
    }

    @Test
    fun clippedRangeRebuildsHandleWithoutOffset() {
        val sourceLane = TimelineAutomationLane(
            target = TimelineTrackAutomationTarget.VOLUME,
            points = listOf(
                TimelineAutomationPoint(
                    timeMs = 0L,
                    value = 0f,
                    curveHandleTime = 0.3f,
                    curveHandleValue = 0.8f,
                    pointId = "start"
                ),
                TimelineAutomationPoint(timeMs = 1_000L, value = 1f, pointId = "end")
            )
        )

        val clippedLane = sourceLane.clippedToRange(
            startMs = 200L,
            endMs = 800L,
            baseValue = 0f
        )
        assertNotNull(clippedLane)

        assertEquals(
            sourceLane.valueAt(timeMs = 350L, defaultValue = 0f),
            clippedLane.valueAt(timeMs = 150L, defaultValue = 0f),
            0.0001f
        )
        assertEquals(
            sourceLane.valueAt(timeMs = 500L, defaultValue = 0f),
            clippedLane.valueAt(timeMs = 300L, defaultValue = 0f),
            0.0001f
        )
    }

    @Test
    fun volumeAutomationMapsUnityGainToCenteredDisplayValue() {
        assertEquals(0.5f, TimelineTrackAutomationTarget.VOLUME.valueToDisplayProgress(1f), 0.0001f)
        assertEquals(1f, TimelineTrackAutomationTarget.VOLUME.displayProgressToValue(0.5f), 0.0001f)
        assertEquals("0 dB", TimelineTrackAutomationTarget.VOLUME.formatValue(1f))
    }

    @Test
    fun volumeAutomationSnapsNearZeroDbBackToUnityGain() {
        assertEquals(0f, TimelineTrackAutomationTarget.VOLUME.snapDisplayValue(0.2f), 0.0001f)
        assertEquals(1f, TimelineTrackAutomationTarget.VOLUME.snapValue(1.02f), 0.0001f)
    }

    @Test
    fun normalizedKeepsExpandedCurveRangeUsedByUi() {
        val point = TimelineAutomationPoint(
            timeMs = 0L,
            value = 1f,
            curve = 2.5f
        ).normalized(TimelineTrackAutomationTarget.VOLUME)

        assertEquals(2.5f, point.curve, 0.0001f)
    }
}
