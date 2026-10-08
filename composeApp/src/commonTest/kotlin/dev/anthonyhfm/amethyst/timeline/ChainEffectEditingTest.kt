package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.data.ChainEffectEntry
import dev.anthonyhfm.amethyst.timeline.utils.ChainEffectEditMode
import dev.anthonyhfm.amethyst.timeline.utils.ChainEffectSpan
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.utils.resolveChainEffectSpan
import kotlin.test.Test
import kotlin.test.assertEquals

class ChainEffectEditingTest {
    private val entry = ChainEffectEntry(startTimeMs = 1000L, durationMs = 3000L)

    @Test
    fun leftResizeKeepsRightEdgeWhenMinimumLengthIsReached() {
        val span = resolveChainEffectSpan(
            entry = entry,
            mode = ChainEffectEditMode.LEFT_EDGE,
            deltaMs = 1500L,
            blockers = emptyList(),
            minDurationMs = 2000L,
            maxDurationMs = Long.MAX_VALUE,
            snapTime = { it },
        )
        assertEquals(ChainEffectSpan(startMs = 2000L, durationMs = 2000L), span)
        assertEquals(entry.endTimeMs, span.endMs)
    }

    @Test
    fun rightResizeHonorsNaturalLengthAndNextClipInThePreview() {
        val span = resolveChainEffectSpan(
            entry = entry,
            mode = ChainEffectEditMode.RIGHT_EDGE,
            deltaMs = 5000L,
            blockers = listOf(ChainEffectEntry(startTimeMs = 5000L, durationMs = 2000L)),
            minDurationMs = 2000L,
            maxDurationMs = 3500L,
            snapTime = { it },
        )
        assertEquals(ChainEffectSpan(startMs = 1000L, durationMs = 3500L), span)
    }

    @Test
    fun leftResizeCannotCrossPreviousClip() {
        val span = resolveChainEffectSpan(
            entry = entry,
            mode = ChainEffectEditMode.LEFT_EDGE,
            deltaMs = -1000L,
            blockers = listOf(ChainEffectEntry(startTimeMs = 0L, durationMs = 750L)),
            minDurationMs = 2000L,
            maxDurationMs = Long.MAX_VALUE,
            snapTime = { it },
        )
        assertEquals(750L, span.startMs)
        assertEquals(entry.endTimeMs, span.endMs)
    }

    @Test
    fun moveUsesMusicalSnapBeforeDrawingAndAvoidsOccupiedStarts() {
        val span = resolveChainEffectSpan(
            entry = entry,
            mode = ChainEffectEditMode.MOVE,
            deltaMs = 260L,
            blockers = emptyList(),
            minDurationMs = 2000L,
            maxDurationMs = Long.MAX_VALUE,
            snapTime = { timeMs ->
                GridUtils.snapToGrid(
                    timeMs = timeMs,
                    zoomLevel = 1f,
                    bpm = 120.0,
                    gridType = GridUtils.GridType.Fixed._1_4,
                )
            },
        )
        assertEquals(1500L, span.startMs)
        val blocked = resolveChainEffectSpan(
            entry = entry,
            mode = ChainEffectEditMode.MOVE,
            deltaMs = 3100L,
            blockers = listOf(ChainEffectEntry(startTimeMs = 4000L, durationMs = 2000L)),
            minDurationMs = 2000L,
            maxDurationMs = Long.MAX_VALUE,
            snapTime = { it },
        )
        assertEquals(ChainEffectSpan(startMs = entry.startTimeMs, durationMs = entry.durationMs), blocked)
    }

    @Test
    fun fittedCompositionCanExtendAndTrimToSubBarLength() {
        val span = resolveChainEffectSpan(
            entry = entry,
            mode = ChainEffectEditMode.RIGHT_EDGE,
            deltaMs = 8000L,
            blockers = emptyList(),
            minDurationMs = 1L,
            maxDurationMs = Long.MAX_VALUE,
            snapTime = { it },
        )
        assertEquals(11000L, span.durationMs)
        val trimmed = resolveChainEffectSpan(
            entry = entry,
            mode = ChainEffectEditMode.RIGHT_EDGE,
            deltaMs = -2800L,
            blockers = emptyList(),
            minDurationMs = 1L,
            maxDurationMs = Long.MAX_VALUE,
            snapTime = { it },
        )
        assertEquals(200L, trimmed.durationMs)
    }
}
