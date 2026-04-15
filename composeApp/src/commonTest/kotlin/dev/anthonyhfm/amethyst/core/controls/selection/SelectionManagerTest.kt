package dev.anthonyhfm.amethyst.core.controls.selection

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionManagerTest {
    @BeforeTest
    fun setUp() {
        SelectionManager.clear()
    }

    @AfterTest
    fun tearDown() {
        SelectionManager.clear()
    }

    @Test
    fun togglingOffTheAnchorFallsBackToTheLastRemainingTrackSelection() {
        SelectionManager.selectTimelineTracks(
            trackIndices = listOf(1, 2, 3),
            anchorTrackIndex = 3
        )

        SelectionManager.toggleTimelineTrack(trackIndex = 3)

        assertEquals(listOf(1, 2), SelectionManager.selectedTimelineTrackIndices())
        assertEquals(2, SelectionManager.lastSelectedTimelineTrackIndex)
    }
}
