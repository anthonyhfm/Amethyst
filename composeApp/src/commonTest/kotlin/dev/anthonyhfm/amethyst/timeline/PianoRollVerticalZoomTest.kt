package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import kotlin.test.Test
import kotlin.test.assertEquals

class PianoRollVerticalZoomTest {
    @Test
    fun zoomKeepsThePitchAtTheViewportAnchorAcrossDevices() {
        assertEquals(
            expected = 800f,
            actual = pianoRollVerticalZoomScrollOffset(
                scrollOffsetPx = 336f,
                anchorPx = 200f,
                oldNoteHeightPx = 22f,
                newNoteHeightPx = 44f,
                totalPitches = 10,
                deviceHeaderHeightPx = 24f,
            ),
        )
    }

    @Test
    fun deviceHeadersDoNotScaleWithNotes() {
        assertEquals(
            expected = 344f,
            actual = pianoRollVerticalZoomScrollOffset(
                scrollOffsetPx = 124f,
                anchorPx = 130f,
                oldNoteHeightPx = 22f,
                newNoteHeightPx = 44f,
                totalPitches = 10,
                deviceHeaderHeightPx = 24f,
            ),
        )
    }

    @Test
    fun foldedNoteRenderingAndHitTestingShareTheZoomedRowHeight() {
        listOf(0.25f, 1f, 4f, 8f).forEach { zoom ->
            val metrics = PianoRollMetrics(
                totalPitches = 3,
                noteHeightDp = 22.dp * zoom,
                zoomX = 1f,
                density = Density(density = 2f),
                gridResolution = GridResolution.Quarter,
                pitches = listOf(11, 35, 99),
            )
            assertEquals(expected = 44f * zoom, actual = metrics.noteHeightPx)
            listOf(11, 35, 99).forEach { pitch ->
                assertEquals(
                    expected = pitch,
                    actual = metrics.yPxToPitch(y = metrics.pitchToYPx(pitch = pitch) + metrics.noteHeightPx * 0.9f),
                )
            }
        }
    }
}
