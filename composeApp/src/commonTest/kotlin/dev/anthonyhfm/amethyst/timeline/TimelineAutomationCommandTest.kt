package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
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
        SelectionManager.clear()
        TimelineRepository.stop()
        TimelineRepository.loadTracks(emptyList())
    }

    @AfterTest
    fun tearDown() {
        UndoManager.clear()
        SelectionManager.clear()
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

    @Test
    fun deleteAutomationRangeCommandDoesNotRemoveAudioClips() {
        val audioTrack = AudioTimelineTrack().apply {
            trackId = "audio-main"
            entries[0L] = buildAudioEntry()
            automationLanes += TimelineAutomationLane(
                target = TimelineTrackAutomationTarget.VOLUME,
                points = listOf(
                    TimelineAutomationPoint(timeMs = 0L, value = 1f, pointId = "start"),
                    TimelineAutomationPoint(timeMs = 500L, value = 0.5f, pointId = "middle"),
                    TimelineAutomationPoint(timeMs = 1_000L, value = 0.8f, pointId = "end")
                )
            )
        }

        TimelineRepository.loadTracks(listOf(audioTrack))

        val result = TimelineCommandExecutor.execute(
            TimelineEditCommand.DeleteAutomationRange(
                trackIndex = 0,
                lane = TimelineAutomationLaneKey(TimelineTrackAutomationTarget.VOLUME),
                startMs = 200L,
                endMs = 800L
            )
        )

        assertTrue(result.didChange)
        val updatedTrack = TimelineRepository.tracks.value.single() as AudioTimelineTrack
        assertTrue(updatedTrack.entries.containsKey(0L))
        assertEquals(
            listOf(0L, 200L, 800L, 1_000L),
            updatedTrack.automationLane(TimelineTrackAutomationTarget.VOLUME)
                ?.points
                ?.map(TimelineAutomationPoint::timeMs)
        )
    }

    @Test
    fun duplicateAutomationRangeCommandKeepsAudioClipsAndAppendsAutomationCopy() {
        val audioTrack = AudioTimelineTrack().apply {
            trackId = "audio-main"
            entries[0L] = buildAudioEntry()
            automationLanes += TimelineAutomationLane(
                target = TimelineTrackAutomationTarget.VOLUME,
                points = listOf(
                    TimelineAutomationPoint(timeMs = 0L, value = 1f, pointId = "start"),
                    TimelineAutomationPoint(timeMs = 500L, value = 0.5f, pointId = "mid")
                )
            )
        }

        TimelineRepository.loadTracks(listOf(audioTrack))

        val result = TimelineCommandExecutor.execute(
            TimelineEditCommand.DuplicateAutomationRange(
                trackIndex = 0,
                lane = TimelineAutomationLaneKey(TimelineTrackAutomationTarget.VOLUME),
                startMs = 0L,
                endMs = 500L
            )
        )

        assertTrue(result.didChange)
        val updatedTrack = TimelineRepository.tracks.value.single() as AudioTimelineTrack
        assertTrue(updatedTrack.entries.containsKey(0L))
        assertEquals(
            listOf(0L, 500L, 1_000L),
            updatedTrack.automationLane(TimelineTrackAutomationTarget.VOLUME)
                ?.points
                ?.map(TimelineAutomationPoint::timeMs)
        )
    }

    @Test
    fun duplicateAutomationRangeSelectsTheAppendedRange() {
        val audioTrack = AudioTimelineTrack().apply {
            trackId = "audio-main"
            automationLanes += TimelineAutomationLane(
                target = TimelineTrackAutomationTarget.VOLUME,
                points = listOf(
                    TimelineAutomationPoint(timeMs = 0L, value = 1f, pointId = "start"),
                    TimelineAutomationPoint(timeMs = 500L, value = 0.5f, pointId = "mid")
                )
            )
        }

        TimelineRepository.loadTracks(listOf(audioTrack))

        val result = TimelineCommandSurface.duplicateAutomationRange(
            trackIndex = 0,
            lane = TimelineAutomationLaneKey(TimelineTrackAutomationTarget.VOLUME),
            startMs = 0L,
            endMs = 500L
        )

        assertTrue(result.didChange)
        val selections = SelectionManager.selections.value
        assertEquals(2, selections.size)

        val laneSelection = selections.filterIsInstance<Selectable.TimelineAutomationLane>().single()
        assertEquals(0, laneSelection.trackIndex)
        assertEquals(TimelineTrackAutomationTarget.VOLUME, laneSelection.target)
        assertEquals(null, laneSelection.bindingId)

        val rangeSelection = selections.filterIsInstance<Selectable.TimelineRange>().single()
        assertEquals(0, rangeSelection.trackIndex)
        assertEquals(500L, rangeSelection.startMs)
        assertEquals(1_000L, rangeSelection.endMs)
    }

    private fun buildAudioEntry(): AudioEntry {
        return AudioEntry(
            startTimeMs = 0L,
            durationMs = 100L,
            fileName = "clip.wav",
            sourceId = "source-id",
            clipEndSample = 4_410L,
            sampleRate = 44_100,
            channels = 2,
            bitDepth = 16,
            startTimeUs = 0L,
            durationUs = 100_000L
        )
    }
}
