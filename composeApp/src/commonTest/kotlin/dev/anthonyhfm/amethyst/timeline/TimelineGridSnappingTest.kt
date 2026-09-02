package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.utils.computeSnappedTimeFromContentX
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineGridSnappingTest {
    @Test
    fun timelinePointerHardSnapsToActiveGrid() {
        assertEquals(
            500L,
            computeSnappedTimeFromContentX(
                x = 260f,
                zoomLevel = 1f,
                bpm = 120.0,
                gridType = GridUtils.GridType.Fixed._1_4,
            ),
        )
    }

    @Test
    fun timelinePointerBypassesGridWhenSnapIsDisabled() {
        assertEquals(
            260L,
            computeSnappedTimeFromContentX(
                x = 260f,
                zoomLevel = 1f,
                bpm = 120.0,
                gridType = GridUtils.GridType.Fixed._1_4,
                snapEnabled = false,
            ),
        )
    }
}
