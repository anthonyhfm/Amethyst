package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils

@Composable
internal fun Modifier.timelineGridOverlay(
    zoomLevel: Float,
    bpm: Double,
    gridType: GridUtils.GridType,
    drawBehind: Boolean = false,
    ignoreTopPx: Float = 0f,
    scrollOffsetPx: Float = 0f
): Modifier {
    val baseLineColor = Color(0xFF000000)
    val minorColor = baseLineColor.copy(alpha = 0.55f)
    val majorColor = baseLineColor.copy(alpha = 0.80f)
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
            val startTimeMsInclusive = (scrollOffsetPx / zoomLevel.toDouble()).toLong().coerceAtLeast(0L)
            val firstGridTimeMs = if (startTimeMsInclusive == 0L) 0L else ((startTimeMsInclusive / intervalMs) * intervalMs).coerceAtLeast(0L)
            val endTimeMsExclusive = ((scrollOffsetPx + viewportWidthPx) / zoomLevel.toDouble()).toLong().coerceAtLeast(firstGridTimeMs)
            var t = firstGridTimeMs
            while (t <= endTimeMsExclusive + intervalMs) {
                val x = (t.toDouble() * zoomLevel.toDouble() - scrollOffsetPx.toDouble()).toFloat()
                if (x > viewportWidthPx + 1f) break
                if (x >= -1f) {
                    val isMajor = (t % majorIntervalMs == 0L)
                    drawLine(
                        color = if (isMajor) majorColor else minorColor,
                        start = Offset(x, ignoreTopPx),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
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
