package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.utils.GridUtils.GridType
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils.narrower
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils.wider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GridTypeSteppingTest {
    @Test
    fun fixedGridHalvesAndDoubles() {
        assertEquals(GridType.Fixed._1_8, GridType.Fixed._1_4.narrower())
        assertEquals(GridType.Fixed._1_2, GridType.Fixed._1_4.wider())
        assertEquals(GridType.Fixed._1_2, GridType.Fixed.Bar_1.narrower())
        assertEquals(GridType.Fixed.Bar_2, GridType.Fixed.Bar_1.wider())
    }

    @Test
    fun fixedGridStopsAtLadderEnds() {
        assertSame(GridType.Fixed._1_32, GridType.Fixed._1_32.narrower())
        assertSame(GridType.Fixed.Bar_8, GridType.Fixed.Bar_8.wider())
    }

    @Test
    fun flexibleGridStepsBetweenSizes() {
        assertEquals(GridType.Flexible.Small, GridType.Flexible.Medium.narrower())
        assertEquals(GridType.Flexible.Large, GridType.Flexible.Medium.wider())
        assertSame(GridType.Flexible.Smallest, GridType.Flexible.Smallest.narrower())
    }

    @Test
    fun autoStartsFromMediumAndNoGridIsUnchanged() {
        assertEquals(GridType.Flexible.Small, GridType.None.narrower())
        assertEquals(GridType.Flexible.Large, GridType.None.wider())
        assertSame(GridType.NoGrid, GridType.NoGrid.narrower())
        assertSame(GridType.NoGrid, GridType.NoGrid.wider())
    }
}
