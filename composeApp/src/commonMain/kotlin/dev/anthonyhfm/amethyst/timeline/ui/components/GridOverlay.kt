package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme

@Composable
internal fun Modifier.timelineGridOverlay(
    viewport: EditorViewportState,
    bpm: Double,
    gridType: GridUtils.GridType,
    drawBehind: Boolean = false,
    ignoreTopPx: Float = 0f,
): Modifier {
    val zoomLevel = viewport.zoomX
    val scrollOffsetPx = viewport.scrollX
    val timelinePalette = TimelineTheme.palette
    return this.drawWithContent {
        if (zoomLevel <= 0f) {
            drawContent(); return@drawWithContent
        }
        val intervals = GridUtils.computeWithGridType(zoomLevel, bpm, gridType)
        val intervalMs = intervals.intervalMs
        val majorIntervalMs = intervals.majorIntervalMs
        if (intervalMs <= 0L) { drawContent(); return@drawWithContent }
        val pxPerGrid = intervalMs * zoomLevel
        if (pxPerGrid < 4f) { drawContent(); return@drawWithContent }
        fun drawGridLines() {
            val viewportWidthPx = size.width
            val startTimeMs = viewport.screenToTimeMs(screenX = 0f).coerceAtLeast(0.0)
            val endTimeMs = viewport.screenToTimeMs(screenX = viewportWidthPx)
            var majorIndex = intervals.majorIndexAt(timeMs = startTimeMs)
            while (intervals.majorTimeAt(index = majorIndex) <= endTimeMs + intervals.exactMajorIntervalMs) {
                val startX = viewport.timeMsToScreenX(timeMs = intervals.majorTimeAt(index = majorIndex).toDouble())
                val endX = viewport.timeMsToScreenX(timeMs = intervals.majorTimeAt(index = majorIndex + 1).toDouble())
                val clampedStartX = startX.coerceAtLeast(0f)
                val clampedEndX = endX.coerceAtMost(viewportWidthPx)
                if (majorIndex % 2L == 0L && clampedEndX > clampedStartX) {
                    drawRect(
                        color = timelinePalette.gridMajor.copy(alpha = 0.08f),
                        topLeft = Offset(x = clampedStartX, y = ignoreTopPx),
                        size = Size(width = clampedEndX - clampedStartX, height = size.height - ignoreTopPx),
                    )
                }
                majorIndex++
            }
            var index = intervals.indexAt(timeMs = startTimeMs)
            while (intervals.timeAt(index = index) <= endTimeMs + intervals.exactIntervalMs) {
                val x = viewport.timeMsToScreenX(timeMs = intervals.timeAt(index = index).toDouble())
                if (x > viewportWidthPx + 1f) {
                    break
                }
                if (x >= -1f) {
                    drawLine(
                        color = if (intervals.isMajor(index = index)) timelinePalette.gridMajor else timelinePalette.gridMinor,
                        start = Offset(x = x, y = ignoreTopPx),
                        end = Offset(x = x, y = size.height),
                        strokeWidth = 1f,
                    )
                }
                index++
            }
        }
        if (drawBehind) {
            drawGridLines(); drawContent()
        } else {
            drawContent(); drawGridLines()
        }
    }
}
