package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.timeline.contract.TimelineEditorTool
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.isGradient
import dev.anthonyhfm.amethyst.timeline.data.isOutOfBounds
import dev.anthonyhfm.amethyst.ui.modifier.ResizeLeft
import dev.anthonyhfm.amethyst.ui.modifier.ResizeRight
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.timelineColorTokens
import dev.anthonyhfm.amethyst.ui.theme.timelineSelectionCursor
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
internal fun PianoRollHeader(
    clipBeats: Float,
    metrics: PianoRollMetrics,
    beatsPerBar: Int,
    viewport: EditorViewportState,
    onTap: (Offset) -> Unit
) {
    val latestOnTap by rememberUpdatedState(onTap)
    val palette = TimelineTheme.palette

    Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(40.dp)
                .background(palette.rulerSurface)
        )

        // Viewport clip — the ruler content is drawn in screen space
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset -> latestOnTap(offset) }
                    }
            ) {
                TimelineRuler(
                    clipBeats = clipBeats,
                    metrics = metrics,
                    beatsPerBar = beatsPerBar,
                    viewport = viewport
                )
            }
        }
    }
}

internal data class PianoRollGridColors(
    val canvasColor: Color,
    val rowColor: Color,
    val pitchSeparatorColor: Color,
    val quarterCellColor: Color,
    val beatLineColor: Color,
    val barLineColor: Color,
)

internal fun Modifier.pianoRollGridBackground(
    devicePitchRange: IntRange,
    clipBeats: Float,
    metrics: PianoRollMetrics,
    beatsPerBar: Int,
    gridResolution: GridResolution,
    colors: PianoRollGridColors,
    viewport: EditorViewportState
): Modifier = drawBehind {
    val widthPx = size.width
    val heightPx = size.height

    // Pitch 0 is at the bottom (matches pitchToYPx: y = (last - pitch) * noteHeight)
    for (pitch in devicePitchRange) {
        val y = (devicePitchRange.last - pitch) * metrics.noteHeightPx
        val isWhiteKey = pitch % 12 in listOf(0, 2, 4, 5, 7, 9, 11)
        drawRect(
            color = if (isWhiteKey) colors.canvasColor else colors.rowColor,
            topLeft = Offset(0f, y),
            size = Size(widthPx, metrics.noteHeightPx)
        )
    }

    for (pitch in devicePitchRange) {
        val y = (devicePitchRange.last - pitch) * metrics.noteHeightPx
        drawLine(colors.pitchSeparatorColor, Offset(0f, y), Offset(widthPx, y), 1f)
    }

    // Clip start (t=0) and end (t=durationMs) screen x positions, accounting for OOB overhang
    val clipStartX = viewport.contentToScreenX(metrics.timeMsToXPx(0))
    val clipEndX = clipStartX + clipBeats * metrics.pixelsPerBeatPx

    // Shade OOB regions (before clip start and after clip end)
    val oobOverlay = Color(0f, 0f, 0f, 0.15f)
    if (clipStartX > 0f) {
        drawRect(color = oobOverlay, topLeft = Offset(0f, 0f), size = Size(clipStartX, heightPx))
    }
    if (clipEndX < widthPx) {
        drawRect(color = oobOverlay, topLeft = Offset(clipEndX, 0f), size = Size(widthPx - clipEndX, heightPx))
    }

    val quarterSubdivisions = (clipBeats * 4).roundToInt()
    for (quarterIndex in 0..quarterSubdivisions) {
        val beatIndex = quarterIndex.toFloat() / 4
        val x = clipStartX + beatIndex * metrics.pixelsPerBeatPx
        if (x > widthPx) break
        drawLine(
            color = colors.quarterCellColor,
            start = Offset(x, 0f),
            end = Offset(x, heightPx),
            strokeWidth = 0.5f
        )
    }

    val subdivisionsPerBeat = gridResolution.subBeatsPerBeat
    val totalSubdivisions = (clipBeats * subdivisionsPerBeat).roundToInt()
    for (subIndex in 0..totalSubdivisions) {
        val beatIndex = subIndex.toFloat() / subdivisionsPerBeat
        val x = clipStartX + beatIndex * metrics.pixelsPerBeatPx
        if (x > widthPx) break
        val isBarLine = (beatIndex % beatsPerBar) == 0f
        drawLine(
            color = if (isBarLine) colors.barLineColor else colors.beatLineColor,
            start = Offset(x, 0f),
            end = Offset(x, heightPx),
            strokeWidth = if (isBarLine) 2f else 1f
        )
    }
}

@Composable
internal fun PianoRollMarqueeOverlay(
    marqueeStart: Offset?,
    marqueeCurrent: Offset?
) {
    val start = marqueeStart ?: return
    val current = marqueeCurrent ?: return
    val left = min(start.x, current.x)
    val top = min(start.y, current.y)
    val width = abs(current.x - start.x)
    val height = abs(current.y - start.y)
    val primaryColor = Theme[colors][primary]

    with(LocalDensity.current) {
        Box(
            modifier = Modifier
                .offset(x = left.toDp(), y = top.toDp())
                .size(width.toDp(), height.toDp())
                .border(1.dp, primaryColor)
                .background(primaryColor.copy(alpha = 0.15f))
        )
    }
}

@Composable
internal fun PianoRollSelectedTimeCursor(
    selectedTimeMs: Long?,
    viewport: EditorViewportState,
    oobOverhangMs: Long,
    rowHeight: Dp
) {
    selectedTimeMs ?: return

    val screenX by remember(selectedTimeMs, viewport.zoomX, viewport.scrollX, oobOverhangMs) {
        androidx.compose.runtime.derivedStateOf {
            val contentX = viewport.clipTimeMsToContentX(selectedTimeMs.toDouble(), oobOverhangMs)
            viewport.contentToScreenX(contentX)
        }
    }
    val density = LocalDensity.current
    val cursorColor = Theme[timelineColorTokens][timelineSelectionCursor]

    Box(
        modifier = Modifier
            .offset { IntOffset((screenX - with(density) { 1.5.dp.toPx() }).toInt(), 0) }
            .width(3.dp)
            .height(rowHeight)
            .background(cursorColor)
    )
}

@Composable
internal fun NoteBox(
    note: MidiNote,
    metrics: PianoRollMetrics,
    viewport: EditorViewportState,
    isSelected: Boolean,
    activeTool: TimelineEditorTool,
    clipDurationMs: Long = Long.MAX_VALUE,
    onSelect: () -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeLeft: (resizeDelta: Float) -> Unit,
    onResizeLeftEnd: () -> Unit,
    onResizeRight: (resizeDelta: Float) -> Unit,
    onResizeRightEnd: () -> Unit,
    dragOffset: Offset,
    resizeLeftDelta: Float,
    resizeRightDelta: Float
) {
    val density = LocalDensity.current
    val directEditEnabled = activeTool != TimelineEditorTool.ERASE
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestOnDrag by rememberUpdatedState(onDrag)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnResizeLeft by rememberUpdatedState(onResizeLeft)
    val latestOnResizeLeftEnd by rememberUpdatedState(onResizeLeftEnd)
    val latestOnResizeRight by rememberUpdatedState(onResizeRight)
    val latestOnResizeRightEnd by rememberUpdatedState(onResizeRightEnd)
    val baseY = metrics.pitchToYPx(note.pitch)
    val baseX = metrics.timeMsToXPx(note.startTimeMs)
    val screenX = viewport.contentToScreenX(baseX)
    val baseWidthPx = metrics.durationMsToWidthPx(note.durationMs)
    val snappedDragOffsetY = if (dragOffset.y != 0f) {
        val pitchSteps = round(dragOffset.y / metrics.noteHeightPx).toInt()
        pitchSteps * metrics.noteHeightPx
    } else {
        0f
    }

    val currentX = screenX + dragOffset.x + resizeLeftDelta
    val currentY = baseY + snappedDragOffsetY
    val currentWidthPx = (baseWidthPx - resizeLeftDelta + resizeRightDelta).coerceAtLeast(20f)

    val isOutOfBounds = note.isOutOfBounds(clipDurationMs)
    val normalBorderColor = Theme[colors][foreground].copy(alpha = if (isSelected) 1f else 0.4f)
    val outerBorderColor = if (isOutOfBounds) normalBorderColor.copy(alpha = 0.5f) else normalBorderColor
    val innerBorderColor = Theme[colors][border]

    BoxWithConstraints(
        modifier = Modifier
            .offset(
                x = with(density) { currentX.toDp() },
                y = with(density) { currentY.toDp() }
            )
            .size(
                width = with(density) { currentWidthPx.toDp() },
                height = 22.dp
            )
            .alpha(if (isOutOfBounds) 0.4f else 1f)
            .let {
                if (!note.isGradient) it.background(Color(note.led.red, note.led.green, note.led.blue))
                else it
            }
            .border(2.dp, outerBorderColor)
            .padding(1.dp)
            .then(
                if (isOutOfBounds) {
                    Modifier.drawBehind {
                        val dash = 4.dp.toPx()
                        drawRect(
                            color = innerBorderColor,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
                            )
                        )
                    }
                } else {
                    Modifier.border(1.dp, innerBorderColor)
                }
            )
            .clickable(enabled = directEditEnabled) { latestOnSelect() }
            .then(
                if (directEditEnabled) {
                    Modifier.pointerInput(note, activeTool) {
                        detectDragGestures(
                            onDragStart = { latestOnSelect() },
                            onDragEnd = { latestOnDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                latestOnDrag(dragAmount)
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        if (note.isGradient) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val steps = 100
                val stepWidth = size.width / steps
                val gradient = note.led.gradient!!
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val (r, g, b) = GradientInterpolator.interpolate(gradient, t)
                    drawRect(
                        color = Color(r, g, b),
                        topLeft = Offset(i * stepWidth, 0f),
                        size = Size(stepWidth + 1f, size.height)
                    )
                }
            }
        }

        if (note.isGradient && isSelected) {
            note.led.gradient!!.forEach { stop ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .offset(x = (stop.position * maxWidth.value - 4).dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(Color(stop.r, stop.g, stop.b))
                        .border(1.dp, Theme[colors][foreground], CircleShape)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(6.dp)
                .fillMaxHeight()
                .then(
                    if (directEditEnabled) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.ResizeLeft)
                            .pointerInput(note, activeTool) {
                                detectDragGestures(
                                    onDragStart = { latestOnSelect() },
                                    onDragEnd = { latestOnResizeLeftEnd() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        latestOnResizeLeft(dragAmount.x)
                                    }
                                )
                            }
                    } else {
                        Modifier
                    }
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(6.dp)
                .fillMaxHeight()
                .then(
                    if (directEditEnabled) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.ResizeRight)
                            .pointerInput(note, activeTool) {
                                detectDragGestures(
                                    onDragStart = { latestOnSelect() },
                                    onDragEnd = { latestOnResizeRightEnd() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        latestOnResizeRight(dragAmount.x)
                                    }
                                )
                            }
                    } else {
                        Modifier
                    }
                )
        )
    }
}

@Composable
internal fun DraftNoteBox(
    note: MidiNote,
    metrics: PianoRollMetrics,
    viewport: EditorViewportState,
) {
    val density = LocalDensity.current
    val y = metrics.pitchToYPx(note.pitch)
    val x = viewport.contentToScreenX(metrics.timeMsToXPx(note.startTimeMs))
    val widthPx = metrics.durationMsToWidthPx(note.durationMs)

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { x.toDp() },
                y = with(density) { y.toDp() }
            )
            .size(
                width = with(density) { widthPx.toDp() },
                height = with(density) { metrics.noteHeightPx.toDp() }
            )
            .alpha(0.7f)
            .let {
                if (!note.isGradient) it.background(Color(note.led.red, note.led.green, note.led.blue))
                else it
            }
            .border(2.dp, Theme[colors][primary])
    ) {
        if (note.isGradient) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val steps = 100
                val stepWidth = size.width / steps
                val gradient = note.led.gradient!!
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val (r, g, b) = GradientInterpolator.interpolate(gradient, t)
                    drawRect(
                        color = Color(r, g, b),
                        topLeft = Offset(i * stepWidth, 0f),
                        size = Size(stepWidth + 1f, size.height)
                    )
                }
            }
        }
    }
}

@Composable
internal fun PianoKeysColumn(
    totalPitches: Int,
    noteHeight: Dp,
    verticalScroll: ScrollState?,
    deviceIndex: Int,
    pressedPitches: Set<Int>
) {
    val palette = TimelineTheme.palette
    val blackKeyColor = Theme[colors][muted]
    val whiteKeyColor = Theme[colors][foreground]

    Box(
        modifier = Modifier
            .width(100.dp)
            .fillMaxHeight()
            .background(palette.rulerSurface)
            .let { if (verticalScroll != null) it.verticalScroll(verticalScroll, enabled = false) else it }
    ) {
        Column(
            modifier = Modifier
                .width(120.dp)
                .height(noteHeight * totalPitches)
        ) {
            for (pitch in (totalPitches - 1) downTo 0) {
                val noteInOctave = pitch % 12
                val isBlackKey = noteInOctave in listOf(1, 3, 6, 8, 10)
                val isPressed = pressedPitches.contains(pitch)

                val keyColor = when {
                    isPressed -> Color(0xFFFF0000)
                    isBlackKey -> blackKeyColor
                    else -> whiteKeyColor
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(noteHeight)
                        .background(keyColor)
                        .drawBehind {
                            drawLine(
                                color = palette.canvas,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1f
                            )
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {}
            }
        }
    }
}

@Composable
internal fun TimelineRuler(
    clipBeats: Float,
    metrics: PianoRollMetrics,
    beatsPerBar: Int,
    viewport: EditorViewportState
) {
    val textMeasurer = rememberTextMeasurer()
    val palette = TimelineTheme.palette
    val backgroundColor = palette.rulerSurface
    val textColor = palette.rulerText
    val majorTickColor = palette.tickMajor
    val minorTickColor = palette.tickMinor

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        val heightPx = size.height
        val totalBeats = clipBeats.toInt() + 1
        val clipStartX = viewport.contentToScreenX(metrics.timeMsToXPx(0))

        for (beatIndex in 0 until totalBeats) {
            val x = clipStartX + beatIndex * metrics.pixelsPerBeatPx
            if (x > size.width) break

            val isBar = (beatIndex % beatsPerBar) == 0
            val tickHeight = if (isBar) heightPx * 0.6f else heightPx * 0.3f

            drawLine(
                color = if (isBar) majorTickColor else minorTickColor,
                start = Offset(x, heightPx - tickHeight),
                end = Offset(x, heightPx),
                strokeWidth = 1.dp.toPx()
            )
        }

        for (beatIndex in 0 until totalBeats) {
            val x = clipStartX + beatIndex * metrics.pixelsPerBeatPx
            if (x > size.width) break

            if ((beatIndex % beatsPerBar) == 0) {
                val barNumber = (beatIndex / beatsPerBar) + 1

                drawText(
                    textMeasurer = textMeasurer,
                    text = "$barNumber",
                    topLeft = Offset(x + 4.dp.toPx(), 4.dp.toPx()),
                    style = TextStyle(
                        color = textColor,
                        fontSize = 11.sp
                    )
                )

                for (beat in 1 until beatsPerBar) {
                    val beatX = clipStartX + (beatIndex + beat) * metrics.pixelsPerBeatPx
                    if (beatX > size.width) break

                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${beat + 1}",
                        topLeft = Offset(beatX + 4.dp.toPx(), 4.dp.toPx()),
                        style = TextStyle(
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}
