package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import kotlin.math.roundToInt

@Composable
fun PlayheadCursor(
    positionMs: Long,
    viewport: EditorViewportState,
) {
    val timelinePalette = TimelineTheme.palette
    val timelineDimensions = TimelineTheme.dimensions
    val cursorContainerWidth = 18.dp
    val centerLineWidth = timelineDimensions.playheadWidth

    Box(
        modifier = Modifier
            .offset {
                val cursorX = viewport.timeMsToScreenX(positionMs.toDouble()).roundToInt()
                IntOffset(cursorX - (cursorContainerWidth / 2).roundToPx(), 0)
            }
            .width(cursorContainerWidth)
            .fillMaxHeight()
            .zIndex(3f)
            .drawBehind {
                val centerX = size.width / 2f
                val glowWidth = 12.dp.toPx()
                val haloWidth = 4.dp.toPx()
                val capWidth = 12.dp.toPx()
                val capHeight = 18.dp.toPx()
                val capTop = 8.dp.toPx()
                val capLeft = centerX - capWidth / 2f

                drawLine(
                    color = timelinePalette.playheadGlow.copy(alpha = 0.2f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = glowWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = timelinePalette.playhead.copy(alpha = 0.3f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = haloWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = timelinePalette.playhead,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = centerLineWidth.toPx(),
                    cap = StrokeCap.Round
                )
                drawRoundRect(
                    color = timelinePalette.playhead,
                    topLeft = Offset(capLeft, capTop),
                    size = Size(capWidth, capHeight),
                    cornerRadius = CornerRadius(capWidth / 2f, capWidth / 2f)
                )
                drawLine(
                    color = timelinePalette.rulerHighlight.copy(alpha = 0.55f),
                    start = Offset(centerX - capWidth * 0.2f, capTop + 3.dp.toPx()),
                    end = Offset(centerX + capWidth * 0.2f, capTop + 3.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
    )
}
