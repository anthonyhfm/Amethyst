package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineCommandSurfaceTest {
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
    fun trackTargetsForContextPreserveSelectedBatchWhenClickedRowIsSelected() {
        val selections = listOf(
            Selectable.TimelineTrack(trackIndex = 1),
            Selectable.TimelineTrack(trackIndex = 3)
        )

        assertEquals(
            listOf(1, 3),
            TimelineCommandSurface.trackTargetsForContext(
                trackIndex = 3,
                selections = selections
            )
        )
        assertEquals(
            listOf(2),
            TimelineCommandSurface.trackTargetsForContext(
                trackIndex = 2,
                selections = selections
            )
        )
    }





    @Test
    fun setTrackAutomationVisibilityClearsOnlyHiddenTrackAutomationSelections() {
        val firstTrack = AudioTimelineTrack().apply {
            trackId = "audio-main"
            automationLanes += TimelineAutomationLane(
                target = TimelineTrackAutomationTarget.VOLUME,
                visible = true
            )
        }
        val secondTrack = AudioTimelineTrack().apply {
            trackId = "audio-secondary"
            automationLanes += TimelineAutomationLane(
                target = TimelineTrackAutomationTarget.VOLUME,
                visible = true
            )
        }

        TimelineRepository.loadTracks(listOf(firstTrack, secondTrack))
        SelectionManager.replaceSelections(
            listOf(
                Selectable.TimelineAutomationLane(
                    trackIndex = 0,
                    target = TimelineTrackAutomationTarget.VOLUME
                ),
                Selectable.TimelineAutomationLane(
                    trackIndex = 1,
                    target = TimelineTrackAutomationTarget.VOLUME
                )
            )
        )

        val result = TimelineCommandSurface.setTrackAutomationVisibility(
            trackIndex = 0,
            visible = false
        )

        assertTrue(result.didChange)
        assertEquals(
            listOf(
                Selectable.TimelineAutomationLane(
                    trackIndex = 1,
                    target = TimelineTrackAutomationTarget.VOLUME
                )
            ),
            SelectionManager.selections.value
        )
        assertTrue(TimelineRepository.tracks.value[0].automationLanes.none(TimelineAutomationLane::visible))
        assertTrue(TimelineRepository.tracks.value[1].automationLanes.any(TimelineAutomationLane::visible))
    }
}
