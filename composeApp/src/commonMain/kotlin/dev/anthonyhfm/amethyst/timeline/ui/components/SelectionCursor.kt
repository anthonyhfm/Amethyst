package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import kotlin.math.roundToInt

@Composable
fun SelectionCursor(
    selectedTimeMs: Long?,
    viewport: EditorViewportState,
    laneHeight: Dp = 120.dp,
) {
    if (selectedTimeMs == null) return
    val timelinePalette = TimelineTheme.palette
    val timelineDimensions = TimelineTheme.dimensions
    val cursorWidth = timelineDimensions.selectionCursorWidth
    // SelectionCursor is placed directly in the lane Box (no content-offset wrapper),
    // so we use screen-space X via timeMsToScreenX.
    val cursorXPositionPx by remember(selectedTimeMs, viewport.zoomX, viewport.scrollX) {
        derivedStateOf { viewport.timeMsToScreenX(selectedTimeMs.toDouble()) }
    }
    Box(
        modifier = Modifier
            .offset(x = -(cursorWidth / 2))
            .offset { IntOffset(cursorXPositionPx.roundToInt(), 0) }
            .width(cursorWidth)
            .height(laneHeight)
            .zIndex(1.5f)
            .background(timelinePalette.selectionCursor)
    )
}
