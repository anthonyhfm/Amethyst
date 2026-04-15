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
            val startTimeMsInclusive = viewport.screenToTimeMs(0f).toLong().coerceAtLeast(0L)
            val firstGridTimeMs = if (startTimeMsInclusive == 0L) 0L else ((startTimeMsInclusive / intervalMs) * intervalMs).coerceAtLeast(0L)
            val firstMajorTimeMs = if (startTimeMsInclusive == 0L) 0L else ((startTimeMsInclusive / majorIntervalMs) * majorIntervalMs).coerceAtLeast(0L)
            val endTimeMsExclusive = viewport.screenToTimeMs(viewportWidthPx).toLong().coerceAtLeast(firstGridTimeMs)
            var majorTimeMs = firstMajorTimeMs
            while (majorTimeMs <= endTimeMsExclusive + majorIntervalMs) {
                val startX = viewport.timeMsToScreenX(majorTimeMs.toDouble())
                val endX = viewport.timeMsToScreenX((majorTimeMs + majorIntervalMs).toDouble())
                val clampedStartX = startX.coerceAtLeast(0f)
                val clampedEndX = endX.coerceAtMost(viewportWidthPx)
                if (((majorTimeMs / majorIntervalMs) % 2L) == 0L && clampedEndX > clampedStartX) {
                    drawRect(
                        color = timelinePalette.gridMajor.copy(alpha = 0.08f),
                        topLeft = Offset(clampedStartX, ignoreTopPx),
                        size = Size(clampedEndX - clampedStartX, size.height - ignoreTopPx)
                    )
                }
                majorTimeMs += majorIntervalMs
            }
            var t = firstGridTimeMs
            while (t <= endTimeMsExclusive + intervalMs) {
                val x = viewport.timeMsToScreenX(t.toDouble())
                if (x > viewportWidthPx + 1f) break
                if (x >= -1f) {
                    val isMajor = (t % majorIntervalMs == 0L)
                    drawLine(
                        color = if (isMajor) timelinePalette.gridMajor else timelinePalette.gridMinor,
                        start = Offset(x, ignoreTopPx),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                }
                t += intervalMs
            }
        }
        if (drawBehind) {
            drawGridLines(); drawContent()
        } else {
            drawContent(); drawGridLines()
        }
    }
}
