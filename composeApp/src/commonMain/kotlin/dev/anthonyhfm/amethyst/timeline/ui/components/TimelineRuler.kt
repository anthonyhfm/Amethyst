package dev.anthonyhfm.amethyst.timeline.ui.components

import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.TimelineLocator
import dev.anthonyhfm.amethyst.timeline.locators.TimelineLocatorRepository
import dev.anthonyhfm.amethyst.timeline.ui.TimelineContextMenuAction
import dev.anthonyhfm.amethyst.timeline.ui.timelineClipGestures
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuLabel
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.math.roundToLong

private data class RulerLocatorMarker(
    val locator: TimelineLocator,
    val bounds: Rect,
    val label: TextLayoutResult?,
)

@Composable
fun TimelineRuler(
    viewport: EditorViewportState,
    bpm: Double,
    gridType: GridUtils.GridType,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val zoomLevel = viewport.zoomX
    val textMeasurer = rememberTextMeasurer()
    val timelinePalette = TimelineTheme.palette
    val timelineDimensions = TimelineTheme.dimensions
    val playheadPositionMs by TimelineRepository.playheadPositionMs.collectAsState()
    val locators by TimelineLocatorRepository.locators.collectAsState()
    var editingId by remember { mutableStateOf<String?>(value = null) }
    var contextLocatorId by remember { mutableStateOf<String?>(value = null) }
    var contextTimeMs by remember { mutableLongStateOf(value = 0L) }
    var draggingId by remember { mutableStateOf<String?>(value = null) }
    var dragTimeMs by remember { mutableLongStateOf(value = 0L) }
    var dragOffsetX by remember { mutableFloatStateOf(value = 0f) }
    var pressedPosition by remember { mutableStateOf(value = Offset.Zero) }
    var rulerSize by remember { mutableStateOf(value = IntSize.Zero) }
    val defaultName = stringResource(resource = Res.string.locators_default_name)
    val addLabel = stringResource(resource = Res.string.locators_add)
    val editLabel = stringResource(resource = Res.string.locators_edit)
    val jumpLabel = stringResource(resource = Res.string.locators_jump)
    val deleteLabel = stringResource(resource = Res.string.locators_delete)
    val density = LocalDensity.current
    val markerHeightPx = with(receiver = density) { 12.dp.toPx() }
    val markerPaddingPx = with(receiver = density) { 4.dp.toPx() }
    val flagWidthPx = with(receiver = density) { 8.dp.toPx() }
    val maxMarkerWidthPx = with(receiver = density) { 160.dp.toPx() }
    val markers = remember(locators, viewport, bpm, draggingId, dragTimeMs, rulerSize, density, timelinePalette) {
        val positioned = locators.map { locator ->
            val timeMs = if (locator.id == draggingId) dragTimeMs else locator.timeMs(bpm = bpm)
            locator to viewport.timeMsToScreenX(timeMs = timeMs.toDouble())
        }.sortedBy { it.second }
        positioned.mapIndexedNotNull { index, (locator, x) ->
            if (x < 0f || x >= rulerSize.width) {
                return@mapIndexedNotNull null
            }
            val nextX = positioned.getOrNull(index = index + 1)?.second ?: rulerSize.width.toFloat()
            val availableWidth = (nextX - x - markerPaddingPx).coerceIn(
                minimumValue = 0f,
                maximumValue = maxMarkerWidthPx,
            )
            val labelWidth = (availableWidth - flagWidthPx - markerPaddingPx).toInt()
            val label = if (labelWidth > flagWidthPx) {
                textMeasurer.measure(
                    text = AnnotatedString(text = locator.name),
                    style = TextStyle(color = timelinePalette.rulerText, fontSize = 9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = labelWidth),
                )
            } else {
                null
            }
            RulerLocatorMarker(
                locator = locator,
                bounds = Rect(
                    left = x,
                    top = rulerSize.height - markerHeightPx,
                    right = x + flagWidthPx + (label?.size?.width ?: 0) + markerPaddingPx,
                    bottom = rulerSize.height.toFloat(),
                ),
                label = label,
            )
        }
    }

    fun locatorAt(position: Offset): TimelineLocator? = markers
        .lastOrNull { it.bounds.contains(offset = position) }
        ?.locator

    fun timeAt(x: Float): Long = GridUtils.snapToGrid(
        timeMs = viewport.screenToTimeMs(screenX = x).roundToLong().coerceAtLeast(minimumValue = 0L),
        zoomLevel = viewport.zoomX,
        bpm = bpm,
        gridType = gridType,
    )

    fun create(timeMs: Long) {
        editingId = TimelineLocatorRepository.controller.create(
            name = "$defaultName ${locators.size + 1}",
            timeMs = timeMs,
            bpm = bpm,
        )?.id
    }

    ContextMenu(
        modifier = modifier
            .fillMaxWidth()
            .height(height = timelineDimensions.rulerHeight),
        onRightClick = { position ->
            focusManager.clearFocus()
            contextTimeMs = timeAt(x = position.x)
            contextLocatorId = locatorAt(position = position)?.id
        },
        trigger = {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { rulerSize = it }
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(label = addLabel) {
                                create(timeMs = TimelineRepository.playheadPositionMs.value)
                                true
                            },
                        ) + locators.flatMap { locator ->
                            listOf(
                                CustomAccessibilityAction(label = "$jumpLabel: ${locator.name}") {
                                    TimelineLocatorRepository.jump(id = locator.id)
                                    true
                                },
                                CustomAccessibilityAction(label = "$editLabel: ${locator.name}") {
                                    editingId = locator.id
                                    true
                                },
                            )
                        }
                    }
                    .timelineClipGestures(
                        gestureKey = Unit,
                        onPress = { position, _ ->
                            focusManager.clearFocus()
                            pressedPosition = position
                            val locator = locatorAt(position = position)
                            if (locator != null) {
                                TimelineLocatorRepository.jump(id = locator.id)
                            } else if (viewport.zoomX > 0f) {
                                TimelineRepository.setPlayheadPosition(
                                    positionMs = viewport.screenToTimeMs(screenX = position.x)
                                        .toLong()
                                        .coerceAtLeast(minimumValue = 0L),
                                )
                            }
                        },
                        onDoubleClick = {
                            val locator = locatorAt(position = pressedPosition)
                            if (locator != null) {
                                editingId = locator.id
                            } else if (viewport.zoomX > 0f) {
                                create(timeMs = timeAt(x = pressedPosition.x))
                            }
                        },
                        onDragStart = { position ->
                            val locator = locatorAt(position = position)
                            draggingId = locator?.id
                            if (locator != null) {
                                dragTimeMs = locator.timeMs(bpm = bpm)
                                dragOffsetX = position.x - viewport.timeMsToScreenX(timeMs = dragTimeMs.toDouble())
                            }
                        },
                        onDrag = { change, _ ->
                            if (draggingId != null) {
                                dragTimeMs = timeAt(x = change.position.x - dragOffsetX)
                            }
                        },
                        onDragEnd = {
                            draggingId?.let { id ->
                                TimelineLocatorRepository.controller.move(id = id, timeMs = dragTimeMs, bpm = bpm)
                            }
                            draggingId = null
                        },
                        onDragCancel = { draggingId = null },
                    )
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
                    val labelTop = 2.dp.toPx()
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

                markers.forEach { marker ->
                    val bounds = marker.bounds
                    drawRoundRect(
                        color = timelinePalette.rulerAccent,
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        cornerRadius = CornerRadius(x = 2.dp.toPx(), y = 2.dp.toPx()),
                    )
                    drawLine(
                        color = timelinePalette.playhead,
                        start = bounds.topLeft,
                        end = Offset(x = bounds.left, y = bounds.bottom),
                        strokeWidth = 2.dp.toPx(),
                    )
                    val flag = Path().apply {
                        moveTo(x = bounds.left, y = bounds.top)
                        lineTo(x = bounds.left + flagWidthPx, y = bounds.top)
                        lineTo(x = bounds.left + flagWidthPx - 3.dp.toPx(), y = bounds.top + 5.dp.toPx())
                        lineTo(x = bounds.left, y = bounds.top + 5.dp.toPx())
                        close()
                    }
                    drawPath(path = flag, color = timelinePalette.playhead)
                    marker.label?.let { label ->
                        drawText(
                            textLayoutResult = label,
                            topLeft = Offset(
                                x = bounds.left + flagWidthPx,
                                y = bounds.top + (markerHeightPx - label.size.height) / 2f,
                            ),
                        )
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
        },
    ) {
        val locator = locators.firstOrNull { it.id == contextLocatorId }
        if (locator != null) {
            ContextMenuLabel {
                Text(text = locator.name)
            }
            TimelineContextMenuAction(
                label = jumpLabel,
                onClick = { TimelineLocatorRepository.jump(id = locator.id) },
            )
            TimelineContextMenuAction(
                label = editLabel,
                onClick = { editingId = locator.id },
            )
            TimelineContextMenuAction(
                label = deleteLabel,
                onClick = { TimelineLocatorRepository.controller.delete(id = locator.id) },
                destructive = true,
            )
            ContextMenuSeparator()
        }
        TimelineContextMenuAction(
            label = addLabel,
            onClick = { create(timeMs = contextTimeMs) },
            enabled = viewport.zoomX > 0f,
        )
    }

    locators.firstOrNull { it.id == editingId }?.let { locator ->
        TimelineLocatorDialog(
            locator = locator,
            onDismiss = { editingId = null },
        )
    }
}
