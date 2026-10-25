package dev.anthonyhfm.amethyst.core.controls.selection

import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineAreaSelectionTest {
    @BeforeTest
    fun prepare() {
        SelectionManager.clear()
        TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(AudioTimelineTrack(), MidiTimelineTrack(), AudioTimelineTrack(), AudioTimelineTrack()))
    }

    @AfterTest
    fun reset() {
        SelectionManager.clear()
        TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
    }

    @Test
    fun shiftClickCreatesRectangleIncludingIntermediateTracksAndKeepsAnchor() {
        TimelineAreaSelection.select(trackIndex = 0, timeMs = 500L, extend = false)
        TimelineAreaSelection.select(trackIndex = 3, timeMs = 1500L, extend = true)
        val ranges = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
        assertEquals(expected = listOf(0, 1, 2, 3), actual = ranges.map { it.trackIndex })
        assertTrue(actual = ranges.all { it.startMs == 500L && it.endMs == 1500L && it.anchorTimeMs == 500L })

        TimelineAreaSelection.select(trackIndex = 1, timeMs = 250L, extend = true)
        assertEquals(expected = TimelineSelectionPoint(trackIndex = 0, timeMs = 500L), actual = TimelineAreaSelection.current()?.anchor)
        assertEquals(expected = listOf(0, 1), actual = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>().map { it.trackIndex })
    }

    @Test
    fun shiftArrowsShrinkAndCrossTheOriginalTrackAnchor() {
        TimelineAreaSelection.select(trackIndex = 2, timeMs = 500L, extend = false)
        TimelineAreaSelection.select(trackIndex = 3, timeMs = 1000L, extend = true)
        TimelineAreaSelection.moveVertical(direction = -1, extend = true)
        assertEquals(expected = listOf(2), actual = ranges().map { it.trackIndex })
        TimelineAreaSelection.moveVertical(direction = -1, extend = true)
        assertEquals(expected = listOf(2, 1), actual = ranges().map { it.trackIndex })
        assertTrue(actual = ranges().all { it.startMs == 500L && it.endMs == 1000L })
        TimelineAreaSelection.moveVertical(direction = +1, extend = false)
        assertEquals(expected = 2, actual = (SelectionManager.selections.value.single() as Selectable.TimelineTime).trackIndex)
    }

    @Test
    fun verticalCursorsBecomeARectangleWhenTimeIsExtended() {
        TimelineAreaSelection.select(trackIndex = 0, timeMs = 500L, extend = false)
        TimelineAreaSelection.moveVertical(direction = +1, extend = true)
        assertEquals(expected = 2, actual = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineTime>().size)
        val grid = GridUtils.GridIntervals(intervalMs = 500L, majorEvery = 4, majorIntervalMs = 2000L, type = GridUtils.GridType.Fixed._1_4)
        TimelineAreaSelection.moveHorizontal(intervals = grid, direction = +1, extend = true)
        assertEquals(expected = listOf(0, 1), actual = ranges().map { it.trackIndex })
        assertTrue(actual = ranges().all { it.startMs == 500L && it.endMs == 1000L })
        TimelineAreaSelection.moveHorizontal(intervals = grid, direction = -1, extend = true)
        assertEquals(expected = 2, actual = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineTime>().size)
        TimelineAreaSelection.moveHorizontal(intervals = grid, direction = -1, extend = true)
        assertTrue(actual = ranges().all { it.startMs == 0L && it.endMs == 500L })
    }

    @Test
    fun externalClipSelectionAndTrackReorderCannotReuseAnOldArea() {
        TimelineAreaSelection.select(trackIndex = 0, timeMs = 500L, extend = false)
        SelectionManager.select(element = Selectable.TimelineEntryItem(trackIndex = 1, entryStartMs = 0L))
        assertNull(actual = TimelineAreaSelection.current())
        TimelineAreaSelection.select(trackIndex = 3, timeMs = 1000L, extend = true)
        assertEquals(expected = 1, actual = SelectionManager.selections.value.size)
        val tracks = TimelineRepository.tracks.value.reversed()
        TimelineRepository.updateTracksSnapshot(updatedTracks = tracks)
        assertNull(actual = TimelineAreaSelection.current())
        assertTrue(actual = SelectionManager.selections.value.isEmpty())
    }

    private fun ranges(): List<Selectable.TimelineRange> = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
}
