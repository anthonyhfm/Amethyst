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
                    if (latestViewport.zoomX > 0f) {
                        val timeMs = latestViewport.screenToTimeMs(offset.x).toLong().coerceAtLeast(0L)
                        TimelineRepository.setPlayheadPosition(timeMs)
                    }
                }
            }
    ) {
        if (zoomLevel <= 0f) return@Canvas

        val dividerStrokePx = 1.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    timelinePalette.rulerHighlight,
                    timelinePalette.rulerSurface
                )
            )
        )
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

        val startTimeMsInclusive = viewport.screenToTimeMs(0f).toLong().coerceAtLeast(0L)
        val firstGridTimeMs = if (startTimeMsInclusive == 0L) 0L
            else ((startTimeMsInclusive / intervalMs) * intervalMs).coerceAtLeast(0L)
        val firstMajorTimeMs = if (startTimeMsInclusive == 0L) 0L
            else ((startTimeMsInclusive / majorIntervalMs) * majorIntervalMs).coerceAtLeast(0L)
        val endTimeMsExclusive = viewport.screenToTimeMs(viewportWidthPx).toLong()
            .coerceAtLeast(firstGridTimeMs)

        var majorSegmentTimeMs = firstMajorTimeMs
        while (majorSegmentTimeMs <= endTimeMsExclusive + majorIntervalMs) {
            val startX = viewport.timeMsToScreenX(majorSegmentTimeMs.toDouble())
            val endX = viewport.timeMsToScreenX((majorSegmentTimeMs + majorIntervalMs).toDouble())
            val clampedStartX = startX.coerceAtLeast(0f)
            val clampedEndX = endX.coerceAtMost(size.width)
            if (((majorSegmentTimeMs / majorIntervalMs) % 2L) == 0L && clampedEndX > clampedStartX) {
                drawRect(
                    color = timelinePalette.rulerAccent.copy(alpha = 0.36f),
                    topLeft = Offset(clampedStartX, 0f),
                    size = Size(clampedEndX - clampedStartX, size.height)
                )
            }
            majorSegmentTimeMs += majorIntervalMs
        }

        val beatMs = (60000.0 / bpm).toLong().coerceAtLeast(1L)
        val barMs = beatMs * 4

        var t = firstGridTimeMs
        while (t <= endTimeMsExclusive + intervalMs) {
            val x = viewport.timeMsToScreenX(t.toDouble())

            if (x > viewportWidthPx + 1f) break
            if (x >= -1f) {
                val isMajor = (t % majorIntervalMs == 0L)
                val tickHeight = if (isMajor) size.height * 0.58f else size.height * 0.28f

                drawLine(
                    color = if (isMajor) timelinePalette.tickMajor else timelinePalette.tickMinor,
                    start = Offset(x, size.height - tickHeight),
                    end = Offset(x, size.height - dividerStrokePx),
                    strokeWidth = if (isMajor) 1.5f else 1f,
                    cap = StrokeCap.Round
                )
            }

            t += intervalMs
        }

        val pixelSpacingForLabels = 40f
        val minLabelInterval = (pixelSpacingForLabels / zoomLevel).toLong().coerceAtLeast(1L)

        val showBars = barMs >= minLabelInterval
        val showBeats = beatMs >= minLabelInterval && beatMs < barMs
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

        if (showBars) {
            val firstBar = ((startTimeMsInclusive / barMs)).coerceAtLeast(0L)
            val lastBar = ((endTimeMsExclusive / barMs) + 1).coerceAtLeast(firstBar)

            for (barIndex in firstBar..lastBar) {
                val barTimeMs = barIndex * barMs
                val x = viewport.timeMsToScreenX(barTimeMs.toDouble())

                if (x >= -10f && x <= viewportWidthPx + 10f) {
                    val barNumber = barIndex + 1
                    drawLabel(
                        label = "$barNumber",
                        x = x,
                        style = TextStyle(
                            color = timelinePalette.rulerText,
                            fontSize = 11.sp
                        ),
                        backgroundAlpha = 0.72f
                    )

                    if (showBeats) {
                        for (beat in 1..3) {
                            val beatTimeMs = barTimeMs + (beat * beatMs)
                            val beatX = viewport.timeMsToScreenX(beatTimeMs.toDouble())

                            if (beatX >= -10f && beatX <= viewportWidthPx + 10f) {
                                drawLabel(
                                    label = "${beat + 1}",
                                    x = beatX,
                                    style = TextStyle(
                                        color = timelinePalette.rulerText.copy(alpha = 0.76f),
                                        fontSize = 10.sp
                                    ),
                                    backgroundAlpha = 0.4f
                                )
                            }
                        }
                    }
                }
            }
        } else if (showBeats) {
            val firstBeat = ((startTimeMsInclusive / beatMs)).coerceAtLeast(0L)
            val lastBeat = ((endTimeMsExclusive / beatMs) + 1).coerceAtLeast(firstBeat)

            for (beatIndex in firstBeat..lastBeat) {
                val beatTimeMs = beatIndex * beatMs
                val x = viewport.timeMsToScreenX(beatTimeMs.toDouble())

                if (x >= -10f && x <= viewportWidthPx + 10f) {
                    val barNumber = (beatIndex / 4) + 1
                    val beatInBar = (beatIndex % 4) + 1
                    drawLabel(
                        label = "$barNumber.$beatInBar",
                        x = x,
                        style = TextStyle(
                            color = timelinePalette.rulerText,
                            fontSize = 10.sp
                        ),
                        backgroundAlpha = 0.56f
                    )
                }
            }
        }

        val playheadX = viewport.timeMsToScreenX(playheadPositionMs.toDouble())
        if (playheadX in -20f..(viewportWidthPx + 20f)) {
            val glowWidth = 12.dp.toPx()
            val markerWidth = 11.dp.toPx()
            val markerHeight = 10.dp.toPx()
            val markerTop = 4.dp.toPx()

            drawLine(
                color = timelinePalette.playheadGlow.copy(alpha = 0.22f),
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, size.height),
                strokeWidth = glowWidth,
                cap = StrokeCap.Round
            )
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
                strokeWidth = timelineDimensions.playheadWidth.toPx() + 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
