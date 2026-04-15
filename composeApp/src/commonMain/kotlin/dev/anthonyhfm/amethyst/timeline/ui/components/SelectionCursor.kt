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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
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
    val cursorContainerWidth = 10.dp
    // SelectionCursor is placed directly in the lane Box (no content-offset wrapper),
    // so we use screen-space X via timeMsToScreenX.
    val cursorXPositionPx by remember(selectedTimeMs, viewport.zoomX, viewport.scrollX) {
        derivedStateOf { viewport.timeMsToScreenX(selectedTimeMs.toDouble()) }
    }
    Box(
        modifier = Modifier
            .offset(x = -(cursorContainerWidth / 2))
            .offset { IntOffset(cursorXPositionPx.roundToInt(), 0) }
            .width(cursorContainerWidth)
            .height(laneHeight)
            .zIndex(1.5f)
            .drawBehind {
                val centerX = size.width / 2f
                val topMarkerWidth = 8.dp.toPx()
                val topMarkerHeight = 12.dp.toPx()
                val markerTop = 4.dp.toPx()

                drawLine(
                    color = timelinePalette.selectionCursor.copy(alpha = 0.18f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = timelinePalette.selectionCursor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = timelineDimensions.selectionCursorWidth.toPx(),
                    cap = StrokeCap.Round
                )
                drawRoundRect(
                    color = timelinePalette.selectionCursor.copy(alpha = 0.32f),
                    topLeft = Offset(centerX - topMarkerWidth / 2f, markerTop),
                    size = Size(topMarkerWidth, topMarkerHeight),
                    cornerRadius = CornerRadius(topMarkerWidth / 2f, topMarkerWidth / 2f)
                )
            }
            .background(color = timelinePalette.selectionCursor.copy(alpha = 0f))
    )
}
