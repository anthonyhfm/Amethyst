package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineAutomationCommandTest {
    @BeforeTest
    fun setUp() {
        UndoManager.clear()
        TimelineRepository.stop()
        TimelineRepository.loadTracks(emptyList())
    }

    @AfterTest
    fun tearDown() {
        UndoManager.clear()
        TimelineRepository.stop()
        TimelineRepository.loadTracks(emptyList())
    }

    @Test
    fun createAutomationPointCommandSupportsUndoAndRedo() {
        val audioTrack = AudioTimelineTrack().apply {
            trackId = "audio-main"
        }

        TimelineRepository.loadTracks(listOf(audioTrack))

        val volumeLaneKey = TimelineAutomationLaneKey(TimelineTrackAutomationTarget.VOLUME)
        val createdPoint = TimelineAutomationPoint(
            timeMs = 120L,
            value = 0.7f,
            pointId = "volume-point"
        )

        val createResult = TimelineCommandExecutor.execute(
            TimelineEditCommand.CreateAutomationPoints(
                trackIndex = 0,
                lane = volumeLaneKey,
                points = listOf(createdPoint)
            )
        )

        assertTrue(createResult.didChange)
        val trackAfterCreate = TimelineRepository.tracks.value.single() as AudioTimelineTrack
        val createdLane = trackAfterCreate.automationLane(TimelineTrackAutomationTarget.VOLUME)
        assertNotNull(createdLane)
        assertTrue(createdLane.visible)
        assertTrue(createdLane.enabled)
        assertEquals(listOf("volume-point"), createdLane.points.map(TimelineAutomationPoint::pointId))

        UndoManager.undo()
        val trackAfterUndo = TimelineRepository.tracks.value.single() as AudioTimelineTrack
        assertNull(trackAfterUndo.automationLane(TimelineTrackAutomationTarget.VOLUME))

        UndoManager.redo()
        val trackAfterRedo = TimelineRepository.tracks.value.single() as AudioTimelineTrack
        assertEquals(
            listOf("volume-point"),
            trackAfterRedo.automationLane(TimelineTrackAutomationTarget.VOLUME)?.points?.map(TimelineAutomationPoint::pointId)
        )
    }
}
