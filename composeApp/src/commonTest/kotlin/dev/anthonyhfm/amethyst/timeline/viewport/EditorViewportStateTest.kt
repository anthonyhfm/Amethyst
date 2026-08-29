package dev.anthonyhfm.amethyst.timeline.viewport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class EditorViewportStateTest {
    @Test
    fun densityChangeKeepsVisibleTimeAndLogicalZoomStable() {
        val before = EditorViewportState(
            scrollX = 300f,
            zoomX = 0.5f,
            viewportWidth = 800f,
            contentWidth = 4_000f,
        )

        val after = before.rescaleHorizontalPixels(2f)

        assertEquals(before.screenToTimeMs(0f), after.screenToTimeMs(0f))
        assertEquals(600f, after.scrollX)
        assertEquals(1f, after.zoomX)
        assertEquals(1_600f, after.viewportWidth)
        assertEquals(8_000f, after.contentWidth)
    }

    @Test
    fun invalidDensityScaleDoesNotDamageViewport() {
        val viewport = EditorViewportState(scrollX = 42f, zoomX = 0.25f)

        assertSame(viewport, viewport.rescaleHorizontalPixels(1f))
        assertSame(viewport, viewport.rescaleHorizontalPixels(0f))
        assertSame(viewport, viewport.rescaleHorizontalPixels(Float.NaN))
    }
}
