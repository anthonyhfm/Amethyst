package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineMusicalGeometryTest {
    @Test
    fun fractionalTempoBarsShareOneAbsoluteTimeForLabelsGridAndSnapping() {
        listOf(127.0, 140.0, 123.45).forEach { bpm ->
            val grid = GridUtils.computeWithGridType(
                zoomLevel = 0.8f,
                bpm = bpm,
                gridType = GridUtils.GridType.Fixed.Bar_1,
            )
            listOf(1L, 49L, 500L).forEach { barIndex ->
                val labelTime = GridUtils.beatTimeMs(beatIndex = barIndex * 4L, bpm = bpm)
                assertEquals(labelTime, grid.timeAt(index = barIndex))
                assertEquals(
                    labelTime,
                    GridUtils.snapToGrid(
                        timeMs = labelTime + 3L,
                        zoomLevel = 0.8f,
                        bpm = bpm,
                        gridType = GridUtils.GridType.Fixed.Bar_1,
                    ),
                )
            }
        }
    }

    @Test
    fun fractionalSubdivisionAlwaysReachesTheBarAndMarksItMajor() {
        val grid = GridUtils.computeWithGridType(
            zoomLevel = 1f,
            bpm = 127.0,
            gridType = GridUtils.GridType.Fixed._1_16,
        )
        listOf(1L, 49L, 500L).forEach { barIndex ->
            assertEquals(grid.majorTimeAt(index = barIndex), grid.timeAt(index = barIndex * 16L))
            assertTrue(grid.isMajor(index = barIndex * 16L))
            assertEquals(GridUtils.beatTimeMs(beatIndex = barIndex * 4L, bpm = 127.0), grid.majorTimeAt(index = barIndex))
        }
    }

    @Test
    fun keyboardNavigationStaysOnFractionalTempoGrid() {
        val grid = GridUtils.computeWithGridType(
            zoomLevel = 0.8f,
            bpm = 127.0,
            gridType = GridUtils.GridType.Fixed._1_16,
        )
        listOf(49L, 127L, 799L).forEach { index ->
            val current = grid.timeAt(index = index)
            assertEquals(grid.timeAt(index = index + 1L), grid.adjacentTime(timeMs = current, direction = 1))
            assertEquals(grid.timeAt(index = index - 1L), grid.adjacentTime(timeMs = current, direction = -1))
        }
    }

    @Test
    fun noGridKeepsUnsnappedTimes() {
        assertEquals(
            12345L,
            GridUtils.snapToGrid(
                timeMs = 12345L,
                zoomLevel = 0.8f,
                bpm = 127.0,
                gridType = GridUtils.GridType.NoGrid,
            ),
        )
    }
}
