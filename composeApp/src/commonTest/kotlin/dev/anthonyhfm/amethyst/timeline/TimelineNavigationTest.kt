package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineNavigationTest {
    @BeforeTest
    fun resetTimelineNavigation() {
        TimelineRepository.updateTracksSnapshot(emptyList())
        SelectionManager.clear()
    }

    @Test
    fun rightMovesToNextGridLine() {
        assertEquals(1_000L, adjacentTimelineGridTimeMs(500L, 500L, +1))
        assertEquals(1_000L, adjacentTimelineGridTimeMs(750L, 500L, +1))
    }

    @Test
    fun leftMovesToPreviousGridLine() {
        assertEquals(500L, adjacentTimelineGridTimeMs(1_000L, 500L, -1))
        assertEquals(500L, adjacentTimelineGridTimeMs(750L, 500L, -1))
        assertEquals(0L, adjacentTimelineGridTimeMs(0L, 500L, -1))
    }

    @Test
    fun verticalNavigationKeepsTrackBoundariesStable() {
        assertEquals(null, adjacentTimelineTrackIndex(0, +1, trackCount = 1))
        assertEquals(null, adjacentTimelineTrackIndex(0, -1, trackCount = 3))
        assertEquals(null, adjacentTimelineTrackIndex(2, +1, trackCount = 3))
    }

    @Test
    fun verticalNavigationMovesExactlyOneTrack() {
        assertEquals(0, adjacentTimelineTrackIndex(1, -1, trackCount = 3))
        assertEquals(2, adjacentTimelineTrackIndex(1, +1, trackCount = 3))
    }

    @Test
    fun timeSelectionMovesToAdjacentTrackWithoutChangingTime() {
        TimelineRepository.updateTracksSnapshot(listOf(AudioTimelineTrack(), AudioTimelineTrack()))
        SelectionManager.select(Selectable.TimelineTime(trackIndex = 0, timeMs = 750L))

        assertTrue(TimelineKeyHandler.handleVerticalNavigation(direction = +1, extend = false))

        val selection = SelectionManager.selections.value.single() as Selectable.TimelineTime
        assertEquals(1, selection.trackIndex)
        assertEquals(750L, selection.timeMs)
    }

    @Test
    fun singleTrackTimeSelectionDoesNothing() {
        TimelineRepository.updateTracksSnapshot(listOf(AudioTimelineTrack()))
        val original = Selectable.TimelineTime(trackIndex = 0, timeMs = 750L)
        SelectionManager.select(original)

        assertTrue(TimelineKeyHandler.handleVerticalNavigation(direction = +1, extend = false))
        assertEquals(original, SelectionManager.selections.value.single())
    }

    @Test
    fun verticalNavigationDoesNotInventTrackHeaderSelection() {
        TimelineRepository.updateTracksSnapshot(listOf(AudioTimelineTrack(), AudioTimelineTrack()))
        val original = Selectable.TimelineEntryItem(trackIndex = 0, entryStartMs = 0L)
        SelectionManager.select(original)

        assertTrue(TimelineKeyHandler.handleVerticalNavigation(direction = +1, extend = false))
        assertEquals(original, SelectionManager.selections.value.single())
    }

    @Test
    fun explicitTrackHeaderSelectionNavigatesTrackHeaders() {
        TimelineRepository.updateTracksSnapshot(listOf(AudioTimelineTrack(), AudioTimelineTrack()))
        SelectionManager.select(Selectable.TimelineTrack(trackIndex = 0))

        assertTrue(TimelineKeyHandler.handleVerticalNavigation(direction = +1, extend = false))
        assertEquals(
            1,
            (SelectionManager.selections.value.single() as Selectable.TimelineTrack).trackIndex,
        )
    }
}
