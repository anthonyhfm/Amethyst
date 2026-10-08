package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalFocusManager
import kotlin.math.floor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme

@Composable
fun TimelineRuler(
    viewport: EditorViewportState,
    bpm: Double,
    gridType: GridUtils.GridType,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val zoomLevel = viewport.zoomX
    val scrollOffsetPx = viewport.scrollX
    val textMeasurer = rememberTextMeasurer()
    val timelinePalette = TimelineTheme.palette
    val timelineDimensions = TimelineTheme.dimensions
    val playheadPositionMs by TimelineRepository.playheadPositionMs.collectAsState()
    // Always-fresh reference so the pointer-input coroutine (keyed on Unit) never
    // sees a stale scrollX when the viewport scrolls without a zoom change.
    val latestViewport by rememberUpdatedState(viewport)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(timelineDimensions.rulerHeight)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    focusManager.clearFocus()
                    if (latestViewport.zoomX > 0f) {
                        val timeMs = latestViewport.screenToTimeMs(offset.x).toLong().coerceAtLeast(0L)
                        TimelineRepository.setPlayheadPosition(timeMs)
                    }
                }
            }
    ) {
        if (zoomLevel <= 0f) return@Canvas

        val dividerStrokePx = 1.dp.toPx()

        drawLine(
            color = timelinePalette.rulerHighlight.copy(alpha = 0.9f),
            start = Offset(0f, dividerStrokePx / 2f),
            end = Offset(size.width, dividerStrokePx / 2f),
            strokeWidth = dividerStrokePx
        )
        drawLine(
            color = timelinePalette.shellBorder,
            start = Offset(0f, size.height - dividerStrokePx / 2f),
            end = Offset(size.width, size.height - dividerStrokePx / 2f),
            strokeWidth = dividerStrokePx
        )

        val intervals = GridUtils.computeWithGridType(zoomLevel, bpm, gridType)
        val intervalMs = intervals.intervalMs
        val majorIntervalMs = intervals.majorIntervalMs

        if (intervalMs <= 0L) return@Canvas

        val viewportWidthPx = size.width

        val startTimeMs = viewport.screenToTimeMs(screenX = 0f).coerceAtLeast(0.0)
        val endTimeMs = viewport.screenToTimeMs(screenX = viewportWidthPx)
        var majorIndex = intervals.majorIndexAt(timeMs = startTimeMs)
        while (intervals.majorTimeAt(index = majorIndex) <= endTimeMs + intervals.exactMajorIntervalMs) {
            val startX = viewport.timeMsToScreenX(timeMs = intervals.majorTimeAt(index = majorIndex).toDouble())
            val endX = viewport.timeMsToScreenX(timeMs = intervals.majorTimeAt(index = majorIndex + 1).toDouble())
            val clampedStartX = startX.coerceAtLeast(0f)
            val clampedEndX = endX.coerceAtMost(size.width)
            if (majorIndex % 2L == 0L && clampedEndX > clampedStartX) {
                drawRect(
                    color = timelinePalette.rulerAccent.copy(alpha = 0.36f),
                    topLeft = Offset(x = clampedStartX, y = 0f),
                    size = Size(width = clampedEndX - clampedStartX, height = size.height),
                )
            }
            majorIndex++
        }

        val beatMs = GridUtils.beatDurationMs(bpm = bpm)
        val barMs = beatMs * 4.0
        var index = intervals.indexAt(timeMs = startTimeMs)
        while (intervals.timeAt(index = index) <= endTimeMs + intervals.exactIntervalMs) {
            val x = viewport.timeMsToScreenX(timeMs = intervals.timeAt(index = index).toDouble())
            if (x > viewportWidthPx + 1f) {
                break
            }
            if (x >= -1f) {
                val isMajor = intervals.isMajor(index = index)
                val tickHeight = if (isMajor) size.height * 0.58f else size.height * 0.28f
                drawLine(
                    color = if (isMajor) timelinePalette.tickMajor else timelinePalette.tickMinor,
                    start = Offset(x = x, y = size.height - tickHeight),
                    end = Offset(x = x, y = size.height - dividerStrokePx),
                    strokeWidth = if (isMajor) 1.5f else 1f,
                    cap = StrokeCap.Round,
                )
            }
            index++
        }

        val pixelSpacingForLabels = 40f
        val minLabelInterval = (pixelSpacingForLabels / zoomLevel).toDouble().coerceAtLeast(1.0)

        val showBeats = beatMs >= minLabelInterval
        var lastLabelRight = -Float.MAX_VALUE

        fun drawLabel(label: String, x: Float, style: TextStyle, backgroundAlpha: Float) {
            val labelLayout = textMeasurer.measure(
                text = AnnotatedString(label),
                style = style
            )
            val paddingX = 6.dp.toPx()
            val paddingY = 2.dp.toPx()
            val labelTop = 4.dp.toPx()
            val labelWidth = labelLayout.size.width.toFloat() + paddingX * 2f
            val labelHeight = labelLayout.size.height.toFloat() + paddingY * 2f
            val minLabelLeft = 4.dp.toPx()
            val maxLabelLeft = (size.width - labelWidth - minLabelLeft).coerceAtLeast(minLabelLeft)
            val labelLeft = (x + minLabelLeft).coerceIn(minLabelLeft, maxLabelLeft)

            if (labelLeft <= lastLabelRight + 4.dp.toPx()) return

            drawRoundRect(
                color = timelinePalette.rulerAccent.copy(alpha = backgroundAlpha),
                topLeft = Offset(labelLeft, labelTop),
                size = Size(labelWidth, labelHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(labelLeft + paddingX, labelTop + paddingY)
            )
            lastLabelRight = labelLeft + labelWidth
        }

        if (beatMs * zoomLevel > viewportWidthPx) {
            var labelIndex = intervals.indexAt(timeMs = startTimeMs)
            while (intervals.timeAt(index = labelIndex) <= endTimeMs + intervals.exactIntervalMs) {
                val labelTimeMs = intervals.timeAt(index = labelIndex)
                val x = viewport.timeMsToScreenX(timeMs = labelTimeMs.toDouble())
                if (x >= -10f && x <= viewportWidthPx + 10f) {
                    drawLabel(
                        label = "$labelTimeMs ms",
                        x = x,
                        style = TextStyle(color = timelinePalette.rulerText, fontSize = 10.sp),
                        backgroundAlpha = 0.56f,
                    )
                }
                labelIndex++
            }
        } else {
            val labelStepBeats = if (showBeats) {
                1L
            } else {
                var bars = 1L
                while (barMs * bars < minLabelInterval) {
                    bars *= 2L
                }
                bars * 4L
            }
            val firstBeat = floor(startTimeMs / beatMs / labelStepBeats).toLong() * labelStepBeats
            val lastBeat = floor(endTimeMs / beatMs).toLong() + labelStepBeats
            var beatIndex = firstBeat
            while (beatIndex <= lastBeat) {
                val beatTimeMs = GridUtils.beatTimeMs(beatIndex = beatIndex, bpm = bpm)
                val x = viewport.timeMsToScreenX(timeMs = beatTimeMs.toDouble())
                if (x >= -10f && x <= viewportWidthPx + 10f) {
                    val isBar = beatIndex % 4L == 0L
                    val label = if (isBar) "${beatIndex / 4L + 1L}" else "${beatIndex % 4L + 1L}"
                    drawLabel(
                        label = label,
                        x = x,
                        style = TextStyle(
                            color = timelinePalette.rulerText.copy(alpha = if (isBar) 1f else 0.76f),
                            fontSize = if (isBar) 11.sp else 10.sp,
                        ),
                        backgroundAlpha = if (isBar) 0.72f else 0.4f,
                    )
                }
                beatIndex += labelStepBeats
            }
        }

        val playheadX = viewport.timeMsToScreenX(playheadPositionMs.toDouble())
        if (playheadX in -20f..(viewportWidthPx + 20f)) {
            val markerWidth = 9.dp.toPx()
            val markerHeight = 8.dp.toPx()
            val markerTop = 4.dp.toPx()

            drawRoundRect(
                color = timelinePalette.playhead,
                topLeft = Offset(playheadX - markerWidth / 2f, markerTop),
                size = Size(markerWidth, markerHeight),
                cornerRadius = CornerRadius(markerWidth / 2f, markerWidth / 2f)
            )
            drawLine(
                color = timelinePalette.rulerHighlight.copy(alpha = 0.65f),
                start = Offset(playheadX - markerWidth * 0.2f, markerTop + 2.dp.toPx()),
                end = Offset(playheadX + markerWidth * 0.2f, markerTop + 2.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = timelinePalette.playhead,
                start = Offset(playheadX, markerTop + markerHeight),
                end = Offset(playheadX, size.height),
                strokeWidth = timelineDimensions.playheadWidth.toPx().coerceAtLeast(1f),
                cap = StrokeCap.Round
            )
        }
    }
}
