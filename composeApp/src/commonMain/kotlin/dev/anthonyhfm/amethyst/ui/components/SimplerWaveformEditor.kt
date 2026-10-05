package dev.anthonyhfm.amethyst.ui.components

import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.core.engine.audio.source.AudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.ByteArrayPcmAudioSource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import dev.anthonyhfm.amethyst.timeline.viewport.wheelZoomScaleFactor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.theme.Theme
import com.composeunstyled.Text
import dev.anthonyhfm.amethyst.ui.modifier.scaleGestureZoom
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.ui.theme.chart2
import dev.anthonyhfm.amethyst.ui.theme.chart4
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

internal object WaveformKeyboardNavigation {
    var onKeyEvent: ((KeyEvent) -> Boolean)? = null
}

private enum class DragTarget {
    None,
    StartFlag,
    EndFlag,
    Body,
    FadeInNode,
    FadeOutNode,
    LoopStart,
    LoopEnd,
    PanView
}

/**
 * Theme-aware interactive waveform editor tailored for Amethyst.
 *
 * Features:
 * - Semantic theme colors for waveform, selection, loops, and playhead
 * - Top node caps centered at y = 0, overflowing 50% above the top boundary
 * - Maximized vertical space without minimap or time ruler
 */
@Composable
fun SimplerWaveformEditor(
    rawData: ByteArray? = null,
    pcmSource: AudioSource? = null,
    sampleRate: Int,
    channels: Int,
    bitDepth: Int,
    totalDurationMs: Long,
    startPosition: Float,
    endPosition: Float,
    fadeInMs: Float,
    fadeOutMs: Float,
    onStartPositionChange: (Float) -> Unit,
    onEndPositionChange: (Float) -> Unit,
    onRangePositionChange: ((Float, Float) -> Unit)? = null,
    onStartPositionFinishChange: (() -> Unit)? = null,
    onEndPositionFinishChange: (() -> Unit)? = null,
    onFadeInChange: (Float) -> Unit,
    onFadeOutChange: (Float) -> Unit,
    onFadeInFinishChange: (() -> Unit)? = null,
    onFadeOutFinishChange: (() -> Unit)? = null,
    loopStartPosition: Float? = null,
    loopEndPosition: Float? = null,
    onLoopStartPositionChange: ((Float) -> Unit)? = null,
    onLoopEndPositionChange: ((Float) -> Unit)? = null,
    onLoopStartPositionFinishChange: (() -> Unit)? = null,
    onLoopEndPositionFinishChange: (() -> Unit)? = null,
    playheadPosition: Float? = null,
    onInteractionStart: (() -> Unit)? = null,
    onInteractionCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val windowInfo = LocalWindowInfo.current
    val resolvedChannels = if (channels > 0) channels else 2
    val resolvedBitDepth = if (bitDepth in listOf(8, 16, 24, 32)) bitDepth else 16

    val source = remember(rawData, pcmSource, resolvedBitDepth, resolvedChannels, sampleRate) {
        pcmSource ?: rawData?.takeIf { it.isNotEmpty() }?.let {
            ByteArrayPcmAudioSource(
                id = "sample-waveform",
                sampleRate = sampleRate,
                channels = resolvedChannels,
                bitDepth = resolvedBitDepth,
                rawData = it,
            )
        }
    }
    val sampleCount = source?.frameCount ?: 0L

    val focusRequester = remember { FocusRequester() }
    val navigationPaddingPx = with(LocalDensity.current) { 16.dp.toPx() }
    val minimumViewSpan = (2.0 / sampleCount.coerceAtLeast(2L)).coerceAtLeast(0.0000001)
    var viewport by remember(source) { mutableStateOf(WaveformViewport()) }
    var fitSelection by remember(source) { mutableStateOf(true) }
    var pointerX by remember { mutableStateOf<Float?>(null) }

    val viewStart = viewport.start
    val viewEnd = viewport.end

    // Current state values captured via state holders for gesture callbacks
    val currentStart by rememberUpdatedState(startPosition)
    val currentEnd by rememberUpdatedState(endPosition)
    val currentFadeInMs by rememberUpdatedState(fadeInMs)
    val currentFadeOutMs by rememberUpdatedState(fadeOutMs)
    val currentDurationMs by rememberUpdatedState(totalDurationMs)
    val currentLoopStart by rememberUpdatedState(loopStartPosition)
    val currentLoopEnd by rememberUpdatedState(loopEndPosition)
    val latestMinimumViewSpan by rememberUpdatedState(minimumViewSpan)
    val latestNavigationPaddingPx by rememberUpdatedState(navigationPaddingPx)
    val latestOnRangePositionChange by rememberUpdatedState(onRangePositionChange)
    val latestOnStartPositionChange by rememberUpdatedState(onStartPositionChange)
    val latestOnEndPositionChange by rememberUpdatedState(onEndPositionChange)
    val latestOnStartPositionFinishChange by rememberUpdatedState(onStartPositionFinishChange)
    val latestOnEndPositionFinishChange by rememberUpdatedState(onEndPositionFinishChange)
    val latestOnFadeInChange by rememberUpdatedState(onFadeInChange)
    val latestOnFadeOutChange by rememberUpdatedState(onFadeOutChange)
    val latestOnFadeInFinishChange by rememberUpdatedState(onFadeInFinishChange)
    val latestOnFadeOutFinishChange by rememberUpdatedState(onFadeOutFinishChange)
    val latestOnLoopStartPositionChange by rememberUpdatedState(onLoopStartPositionChange)
    val latestOnLoopEndPositionChange by rememberUpdatedState(onLoopEndPositionChange)
    val latestOnLoopStartPositionFinishChange by rememberUpdatedState(onLoopStartPositionFinishChange)
    val latestOnLoopEndPositionFinishChange by rememberUpdatedState(onLoopEndPositionFinishChange)
    val latestOnInteractionStart by rememberUpdatedState(onInteractionStart)
    val latestOnInteractionCancel by rememberUpdatedState(onInteractionCancel)

    // Drag interaction states & initial values
    var activeDragTarget by remember { mutableStateOf(DragTarget.None) }
    var dragTooltipText by remember { mutableStateOf<String?>(null) }

    var initialStartFrac by remember { mutableStateOf(0f) }
    var initialEndFrac by remember { mutableStateOf(0f) }
    var initialFadeInMs by remember { mutableStateOf(0f) }
    var initialFadeOutMs by remember { mutableStateOf(0f) }
    var initialLoopStartFrac by remember { mutableStateOf(0f) }
    var initialLoopEndFrac by remember { mutableStateOf(1f) }
    var initialViewStartFrac by remember { mutableStateOf(0.0) }
    var initialViewSpan by remember { mutableStateOf(1.0) }
    var dragPointerX by remember { mutableStateOf(0f) }
    var accumulatedDragPx by remember { mutableStateOf(0f) }

    var canvasWidthPx by remember { mutableStateOf(0f) }
    var canvasHeightPx by remember { mutableStateOf(0f) }

    fun applyRangeDrag() {
        val totalDelta = (
            accumulatedDragPx / canvasWidthPx.coerceAtLeast(1f) * initialViewSpan +
                viewport.start - initialViewStartFrac
        ).toFloat()

        when (activeDragTarget) {
            DragTarget.StartFlag -> {
                val value = (initialStartFrac + totalDelta).coerceIn(0f, currentEnd - 0.001f)
                latestOnStartPositionChange(value)
                dragTooltipText = "Start: ${formatRulerTime(currentDurationMs * value)}"
            }
            DragTarget.EndFlag -> {
                val value = (initialEndFrac + totalDelta).coerceIn(currentStart + 0.001f, 1f)
                latestOnEndPositionChange(value)
                dragTooltipText = "End: ${formatRulerTime(currentDurationMs * value)}"
            }
            DragTarget.Body -> {
                val span = initialEndFrac - initialStartFrac
                val newStart = (initialStartFrac + totalDelta).coerceIn(0f, 1f - span)
                val newEnd = newStart + span
                val rangeChange = latestOnRangePositionChange
                if (rangeChange != null) {
                    rangeChange(newStart, newEnd)
                } else if (newStart > currentStart) {
                    latestOnEndPositionChange(newEnd)
                    latestOnStartPositionChange(newStart)
                } else {
                    latestOnStartPositionChange(newStart)
                    latestOnEndPositionChange(newEnd)
                }
                dragTooltipText = "Range: ${formatRulerTime(currentDurationMs * newStart)} - ${formatRulerTime(currentDurationMs * newEnd)}"
            }
            DragTarget.LoopStart -> {
                val upper = (currentLoopEnd ?: currentEnd) - 0.001f
                val value = (initialLoopStartFrac + totalDelta).coerceIn(currentStart, upper)
                latestOnLoopStartPositionChange?.invoke(value)
                dragTooltipText = "Loop Start: ${formatRulerTime(currentDurationMs * value)}"
            }
            DragTarget.LoopEnd -> {
                val lower = (currentLoopStart ?: currentStart) + 0.001f
                val value = (initialLoopEndFrac + totalDelta).coerceIn(lower, currentEnd)
                latestOnLoopEndPositionChange?.invoke(value)
                dragTooltipText = "Loop End: ${formatRulerTime(currentDurationMs * value)}"
            }
            else -> {}
        }
    }

    fun fittedViewport(): WaveformViewport = WaveformViewport.fitSelection(
        selectionStart = currentStart,
        selectionEnd = currentEnd,
        width = canvasWidthPx,
        padding = latestNavigationPaddingPx,
        minimumSpan = latestMinimumViewSpan,
    )

    fun zoomViewport(scale: Float, anchorX: Float) {
        fitSelection = false
        viewport = viewport.zoom(
            scale = scale,
            anchorFraction = (anchorX / canvasWidthPx.coerceAtLeast(1f)).toDouble(),
            minimumSpan = latestMinimumViewSpan,
        )
    }

    val latestKeyHandler by rememberUpdatedState<(KeyEvent) -> Boolean> { event ->
        if (event.type != KeyEventType.KeyDown || canvasWidthPx <= 0f) {
            false
        } else {
            val zoomModifier = event.isMetaPressed || event.isCtrlPressed || event.isAltPressed
            val zoomIn = event.key == Key.Plus || event.key == Key.Equals || event.key == Key.NumPadAdd
            val zoomOut = event.key == Key.Minus || event.key == Key.NumPadSubtract

            when {
                zoomModifier && (zoomIn || zoomOut) -> {
                    zoomViewport(
                        scale = if (zoomIn) 1.25f else 1f / 1.25f,
                        anchorX = pointerX ?: canvasWidthPx / 2f,
                    )
                    true
                }
                event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> {
                    fitSelection = false
                    val direction = if (event.key == Key.DirectionLeft) -1 else 1
                    viewport = viewport.pan(delta = direction * viewport.span * 0.1)
                    true
                }
                event.key == Key.MoveHome -> {
                    fitSelection = false
                    viewport = viewport.copy(start = 0.0)
                    true
                }
                event.key == Key.MoveEnd -> {
                    fitSelection = false
                    viewport = viewport.copy(start = 1.0 - viewport.span)
                    true
                }
                else -> false
            }
        }
    }
    val keyHandler = remember { { event: KeyEvent -> latestKeyHandler(event) } }

    DisposableEffect(keyHandler) {
        onDispose {
            if (WaveformKeyboardNavigation.onKeyEvent === keyHandler) {
                WaveformKeyboardNavigation.onKeyEvent = null
            }
        }
    }

    LaunchedEffect(source, canvasWidthPx, startPosition, endPosition, navigationPaddingPx, activeDragTarget) {
        if (fitSelection && canvasWidthPx > 0f && activeDragTarget == DragTarget.None) {
            viewport = fittedViewport()
        }
    }

    LaunchedEffect(rawData, activeDragTarget) {
        val followsDrag = activeDragTarget in setOf(
            DragTarget.StartFlag,
            DragTarget.EndFlag,
            DragTarget.Body,
            DragTarget.LoopStart,
            DragTarget.LoopEnd,
        )
        if (!followsDrag) {
            return@LaunchedEffect
        }

        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val elapsedSeconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
            previousFrame = frame
            val nextViewport = viewport.pan(
                delta = waveformEdgePanDelta(
                    pointerX = dragPointerX,
                    width = canvasWidthPx,
                    padding = navigationPaddingPx,
                    span = viewport.span,
                    elapsedSeconds = elapsedSeconds,
                ),
            )
            if (nextViewport != viewport) {
                viewport = nextViewport
                applyRangeDrag()
            }
        }
    }

    // Theme color palette references
    val palette = Theme[colors]
    val darkSlateBg = palette[background]
    val borderColor = palette[border]
    val mutedForegroundColor = palette[mutedForeground]
    val lightGrayWaveform = palette[mutedForeground]
    val popoverColor = palette[popover]
    val popoverForegroundColor = palette[popoverForeground]

    // Ableton Slate Teal Accent for Interactive Controls & Handles
    val abletonTeal = palette[selectionSurface]
    val handleCoreColor = palette[background]
    val loopColor = palette[chart2]
    val playheadColor = palette[chart4]

    // Compute high-res envelope for visible zoomed viewport
    val visibleStartSample = (sampleCount * viewStart).toLong().coerceIn(0L, sampleCount)
    val visibleEndSample = (sampleCount * viewEnd).toLong().coerceIn(visibleStartSample, sampleCount)
    val visibleAmps = rememberWaveformEnvelope(
        source = source,
        startSample = visibleStartSample,
        endSample = visibleEndSample,
        zoomLevel = 1f,
        sampleRate = sampleRate,
        widthPx = canvasWidthPx.roundToInt().coerceAtLeast(100),
    )

    val textMeasurer = rememberTextMeasurer()

    // Primary Waveform Canvas & Interaction Layer (No Minimap, Max Vertical Space)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(darkSlateBg, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .onSizeChanged {
                canvasWidthPx = it.width.toFloat()
                canvasHeightPx = it.height.toFloat()
            }
            .focusRequester(focusRequester = focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    WaveformKeyboardNavigation.onKeyEvent = keyHandler
                } else if (WaveformKeyboardNavigation.onKeyEvent === keyHandler) {
                    WaveformKeyboardNavigation.onKeyEvent = null
                }
            }
            .onKeyEvent(onKeyEvent = keyHandler)
            .focusable()
            .scaleGestureZoom { scaleFactor, position ->
                if (canvasWidthPx > 0f) {
                    focusRequester.requestFocus()
                    zoomViewport(
                        scale = scaleFactor,
                        anchorX = position.x,
                    )
                }
            }
            .pointerInput(rawData) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        if (event.type == PointerEventType.Exit) {
                            pointerX = null
                        } else {
                            pointerX = change.position.x
                        }
                        if (event.type == PointerEventType.Press) {
                            focusRequester.requestFocus()
                        }
                        if (event.type != PointerEventType.Scroll || change.isConsumed) {
                            continue
                        }

                        val isZoomModifier = event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed
                        val delta = change.scrollDelta
                        val w = size.width.toFloat()
                        if (w <= 0f) {
                            continue
                        }

                        if (isZoomModifier && delta.y != 0f) {
                            focusRequester.requestFocus()
                            zoomViewport(
                                scale = wheelZoomScaleFactor(scrollDelta = -delta.y),
                                anchorX = change.position.x,
                            )
                            event.changes.forEach { it.consume() }
                        } else if (!isZoomModifier) {
                            val deltaX = if (event.keyboardModifiers.isShiftPressed && delta.x == 0f) {
                                delta.y
                            } else {
                                delta.x
                            }
                            if (deltaX != 0f) {
                                focusRequester.requestFocus()
                                fitSelection = false
                                viewport = viewport.pan(delta = deltaX * 40.0 / w * viewport.span)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .pointerInput(rawData) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        if (event.changes.count { it.pressed && it.previousPressed } < 2) {
                            continue
                        }
                        val centroid = event.calculateCentroid(useCurrent = false)
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f || pan.x != 0f) {
                            fitSelection = false
                            val zoomedViewport = viewport.zoom(
                                scale = zoom,
                                anchorFraction = (centroid.x / size.width.coerceAtLeast(1)).toDouble(),
                                minimumSpan = latestMinimumViewSpan,
                            )
                            viewport = zoomedViewport.pan(
                                delta = -pan.x / size.width.coerceAtLeast(1) * zoomedViewport.span,
                            )
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(rawData) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f) {
                            return@detectDragGestures
                        }

                        dragPointerX = offset.x
                        initialViewSpan = viewport.span
                        initialStartFrac = currentStart
                        initialEndFrac = currentEnd
                        initialFadeInMs = currentFadeInMs
                        initialFadeOutMs = currentFadeOutMs
                        initialLoopStartFrac = currentLoopStart ?: currentStart
                        initialLoopEndFrac = currentLoopEnd ?: currentEnd
                        initialViewStartFrac = viewport.start
                        accumulatedDragPx = 0f

                        val sX = viewport.screenX(position = currentStart.toDouble(), width = w)
                        val eX = viewport.screenX(position = currentEnd.toDouble(), width = w)

                        val activeDurMs = (currentDurationMs * (currentEnd - currentStart)).coerceAtLeast(1f)
                        val fadeInRatio = (currentFadeInMs / activeDurMs).coerceIn(0f, 1f)
                        val fadeOutRatio = (currentFadeOutMs / activeDurMs).coerceIn(0f, 1f)

                        val fadeInX = sX + (eX - sX) * fadeInRatio
                        val fadeOutX = eX - (eX - sX) * fadeOutRatio

                        val modifiers = windowInfo.keyboardModifiers
                        val isCmdOrCtrl = modifiers.isMetaPressed || modifiers.isCtrlPressed
                        val hitSlopPx = 28f

                        val isTopZone = offset.y <= 28f
                        val isNearFadeIn = abs(offset.x - fadeInX) <= hitSlopPx
                        val isNearFadeOut = abs(offset.x - fadeOutX) <= hitSlopPx
                        val isNearStart = abs(offset.x - sX) <= hitSlopPx
                        val isNearEnd = abs(offset.x - eX) <= hitSlopPx
                        val loopStartX = currentLoopStart?.let {
                            viewport.screenX(position = it.toDouble(), width = w)
                        }
                        val loopEndX = currentLoopEnd?.let {
                            viewport.screenX(position = it.toDouble(), width = w)
                        }
                        val isBottomZone = offset.y >= h - 36f

                        when {
                            isCmdOrCtrl -> activeDragTarget = DragTarget.PanView
                            isBottomZone && loopStartX != null && abs(offset.x - loopStartX) <= hitSlopPx -> {
                                activeDragTarget = DragTarget.LoopStart
                                dragTooltipText = "Loop Start: ${formatRulerTime(totalDurationMs * currentLoopStart!!)}"
                            }
                            isBottomZone && loopEndX != null && abs(offset.x - loopEndX) <= hitSlopPx -> {
                                activeDragTarget = DragTarget.LoopEnd
                                dragTooltipText = "Loop End: ${formatRulerTime(totalDurationMs * currentLoopEnd!!)}"
                            }
                            // Top zone priority for Fade nodes
                            isTopZone && isNearFadeIn -> {
                                activeDragTarget = DragTarget.FadeInNode
                                dragTooltipText = "Fade In: ${currentFadeInMs.roundToInt()} ms"
                            }
                            isTopZone && isNearFadeOut -> {
                                activeDragTarget = DragTarget.FadeOutNode
                                dragTooltipText = "Fade Out: ${currentFadeOutMs.roundToInt()} ms"
                            }
                            (offset.x < sX - 8.dp.toPx() || offset.x > eX + 8.dp.toPx()) -> {
                                activeDragTarget = DragTarget.PanView
                            }
                            // Range Start Handle
                            isNearStart -> {
                                activeDragTarget = DragTarget.StartFlag
                                dragTooltipText = "Start: ${formatRulerTime(totalDurationMs * currentStart)}"
                            }
                            // Range End Handle
                            isNearEnd -> {
                                activeDragTarget = DragTarget.EndFlag
                                dragTooltipText = "End: ${formatRulerTime(totalDurationMs * currentEnd)}"
                            }
                            // Fallback Fade node check if dragged slightly below top zone
                            isNearFadeIn && offset.y <= 40f -> {
                                activeDragTarget = DragTarget.FadeInNode
                                dragTooltipText = "Fade In: ${currentFadeInMs.roundToInt()} ms"
                            }
                            isNearFadeOut && offset.y <= 40f -> {
                                activeDragTarget = DragTarget.FadeOutNode
                                dragTooltipText = "Fade Out: ${currentFadeOutMs.roundToInt()} ms"
                            }
                            offset.x in sX..eX -> {
                                activeDragTarget = DragTarget.Body
                                dragTooltipText = "Length: ${formatRulerTime(activeDurMs)}"
                            }
                            else -> activeDragTarget = DragTarget.PanView
                        }
                        if (activeDragTarget == DragTarget.PanView) {
                            fitSelection = false
                        } else {
                            latestOnInteractionStart?.invoke()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val w = size.width.toFloat()
                        if (w <= 0f || activeDragTarget == DragTarget.None) {
                            return@detectDragGestures
                        }

                        dragPointerX = change.position.x
                        accumulatedDragPx += dragAmount.x
                        val activeDurMs = (currentDurationMs * (currentEnd - currentStart)).coerceAtLeast(1f)

                        when (activeDragTarget) {
                            DragTarget.StartFlag, DragTarget.EndFlag, DragTarget.Body,
                            DragTarget.LoopStart, DragTarget.LoopEnd -> applyRangeDrag()
                            DragTarget.FadeInNode -> {
                                val sX = viewport.screenX(position = currentStart.toDouble(), width = w)
                                val eX = viewport.screenX(position = currentEnd.toDouble(), width = w)
                                val activeWidthPx = (eX - sX).coerceAtLeast(1f)
                                val deltaActiveRatio = accumulatedDragPx / activeWidthPx
                                val newFadeIn = (initialFadeInMs + deltaActiveRatio * activeDurMs).coerceIn(0f, activeDurMs)
                                latestOnFadeInChange(newFadeIn)
                                dragTooltipText = "Fade In: ${newFadeIn.roundToInt()} ms"
                            }
                            DragTarget.FadeOutNode -> {
                                val sX = viewport.screenX(position = currentStart.toDouble(), width = w)
                                val eX = viewport.screenX(position = currentEnd.toDouble(), width = w)
                                val activeWidthPx = (eX - sX).coerceAtLeast(1f)
                                val deltaActiveRatio = -accumulatedDragPx / activeWidthPx
                                val newFadeOut = (initialFadeOutMs + deltaActiveRatio * activeDurMs).coerceIn(0f, activeDurMs)
                                latestOnFadeOutChange(newFadeOut)
                                dragTooltipText = "Fade Out: ${newFadeOut.roundToInt()} ms"
                            }
                            DragTarget.PanView -> {
                                viewport = viewport.copy(
                                    start = (initialViewStartFrac - accumulatedDragPx / w * initialViewSpan).coerceIn(0.0, 1.0 - viewport.span),
                                )
                            }
                            DragTarget.None -> {}
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        when (activeDragTarget) {
                            DragTarget.StartFlag, DragTarget.Body -> latestOnStartPositionFinishChange?.invoke()
                            DragTarget.EndFlag -> latestOnEndPositionFinishChange?.invoke()
                            DragTarget.FadeInNode -> latestOnFadeInFinishChange?.invoke()
                            DragTarget.FadeOutNode -> latestOnFadeOutFinishChange?.invoke()
                            DragTarget.LoopStart -> latestOnLoopStartPositionFinishChange?.invoke()
                            DragTarget.LoopEnd -> latestOnLoopEndPositionFinishChange?.invoke()
                            else -> {}
                        }
                        activeDragTarget = DragTarget.None
                        dragTooltipText = null
                    },
                    onDragCancel = {
                        if (activeDragTarget != DragTarget.PanView && activeDragTarget != DragTarget.None) {
                            latestOnInteractionCancel?.invoke()
                        }
                        activeDragTarget = DragTarget.None
                        dragTooltipText = null
                    }
                )
            }
            .pointerInput(rawData) {
                detectTapGestures(
                    onDoubleTap = {
                        fitSelection = true
                        viewport = fittedViewport()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val waveformCenterY = h / 2f
            val halfH = h / 2f - 4.dp.toPx()

            // Base Canvas Fill
            drawRect(color = darkSlateBg)

            // Center baseline
            drawLine(
                color = borderColor.copy(alpha = 0.3f),
                start = Offset(0f, waveformCenterY),
                end = Offset(w, waveformCenterY),
                strokeWidth = 1f
            )

            if (w <= 0f) return@Canvas

            fun fracToX(frac: Float): Float = viewport.screenX(position = frac.toDouble(), width = w)

            val sX = fracToX(startPosition)
            val eX = fracToX(endPosition)
            val loopStartX = loopStartPosition?.let(::fracToX)
            val loopEndX = loopEndPosition?.let(::fracToX)

            // Render High-Res Waveform in Clean Light Slate-Gray
            if (visibleAmps.isNotEmpty()) {
                val count = visibleAmps.size

                val fullPath = Path().apply {
                    moveTo(0f, waveformCenterY)
                    for (i in 0 until count) {
                        val x = (i.toFloat() / (count - 1).coerceAtLeast(1)) * w
                        val amp = visibleAmps[i] * halfH
                        lineTo(x, waveformCenterY - amp)
                    }
                    lineTo(w, waveformCenterY)
                    for (i in count - 1 downTo 0) {
                        val x = (i.toFloat() / (count - 1).coerceAtLeast(1)) * w
                        val amp = visibleAmps[i] * halfH
                        lineTo(x, waveformCenterY + amp)
                    }
                    close()
                }

                // Solid smooth fill without outline stroke
                drawPath(path = fullPath, color = lightGrayWaveform.copy(alpha = 0.85f))

                // Dim inactive waveform audio (left of Start and right of End)
                if (sX > 0f) {
                    val leftDimPath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(sX, 0f)
                        lineTo(sX, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(leftDimPath, darkSlateBg.copy(alpha = 0.65f))
                }
                if (eX < w) {
                    val rightDimPath = Path().apply {
                        moveTo(eX, 0f)
                        lineTo(w, 0f)
                        lineTo(w, h)
                        lineTo(eX, h)
                        close()
                    }
                    drawPath(rightDimPath, darkSlateBg.copy(alpha = 0.65f))
                }
            }

            playheadPosition
                ?.takeIf { it.isFinite() && it.toDouble() in viewStart..viewEnd }
                ?.let { position ->
                    val playheadX = fracToX(position)
                    drawLine(
                        color = playheadColor,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, h),
                        strokeWidth = 2.dp.toPx(),
                    )
                }

            if (loopStartX != null && loopEndX != null && loopEndX > loopStartX) {
                drawRect(
                    color = loopColor.copy(alpha = 0.10f),
                    topLeft = Offset(loopStartX, 0f),
                    size = Size(loopEndX - loopStartX, h),
                )
                drawLine(
                    color = loopColor,
                    start = Offset(loopStartX, 0f),
                    end = Offset(loopStartX, h),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
                drawLine(
                    color = loopColor,
                    start = Offset(loopEndX, 0f),
                    end = Offset(loopEndX, h),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
                val loopHandleRadius = 7.dp.toPx()
                drawCircle(
                    color = loopColor,
                    radius = if (activeDragTarget == DragTarget.LoopStart) loopHandleRadius + 1.5.dp.toPx() else loopHandleRadius,
                    center = Offset(loopStartX, h),
                )
                drawCircle(color = handleCoreColor, radius = 2.5.dp.toPx(), center = Offset(loopStartX, h))
                drawCircle(
                    color = loopColor,
                    radius = if (activeDragTarget == DragTarget.LoopEnd) loopHandleRadius + 1.5.dp.toPx() else loopHandleRadius,
                    center = Offset(loopEndX, h),
                )
                drawCircle(color = handleCoreColor, radius = 2.5.dp.toPx(), center = Offset(loopEndX, h))
            }

            // Fade Calculations
            val activeSpanMs = (totalDurationMs * (endPosition - startPosition)).coerceAtLeast(1f)
            val fadeInRatio = (fadeInMs / activeSpanMs).coerceIn(0f, 1f)
            val fadeOutRatio = (fadeOutMs / activeSpanMs).coerceIn(0f, 1f)

            val activeW = eX - sX
            val fadeInX = sX + activeW * fadeInRatio
            val fadeOutX = eX - activeW * fadeOutRatio

            // Top Node Caps sit directly on the top border (y = 0), overflowing 50% above the top boundary
            val fadeNodeCenterY = 0f

            // Fade In Envelope Line & Shading in Ableton Slate Teal
            if (fadeInMs > 0f && activeW > 0f) {
                val fadeInPath = Path().apply {
                    moveTo(sX, h)
                    lineTo(sX, fadeNodeCenterY)
                    lineTo(fadeInX, fadeNodeCenterY)
                    close()
                }
                drawPath(fadeInPath, abletonTeal.copy(alpha = 0.12f))
                drawLine(
                    color = abletonTeal,
                    start = Offset(sX, h),
                    end = Offset(fadeInX, fadeNodeCenterY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
            }

            // Fade Out Envelope Line & Shading in Ableton Slate Teal
            if (fadeOutMs > 0f && activeW > 0f) {
                val fadeOutPath = Path().apply {
                    moveTo(fadeOutX, fadeNodeCenterY)
                    lineTo(eX, fadeNodeCenterY)
                    lineTo(eX, h)
                    close()
                }
                drawPath(fadeOutPath, abletonTeal.copy(alpha = 0.12f))
                drawLine(
                    color = abletonTeal,
                    start = Offset(fadeOutX, fadeNodeCenterY),
                    end = Offset(eX, h),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
            }

            // --- Uniform Handle Radius (7.dp radius / 14.dp diameter) ---
            val nodeRadiusPx = 7.dp.toPx()

            // Top Fade In Node Grip Handle in Ableton Teal (Centered at y = 0, overflowing 50% top)
            if (fadeInX in -10f..w + 10f) {
                val isDraggingFadeIn = activeDragTarget == DragTarget.FadeInNode
                val radius = if (isDraggingFadeIn) nodeRadiusPx + 1.5.dp.toPx() else nodeRadiusPx
                
                drawCircle(
                    color = abletonTeal,
                    radius = radius,
                    center = Offset(fadeInX, fadeNodeCenterY),
                    style = Stroke(2.dp.toPx())
                )
                drawCircle(
                    color = abletonTeal.copy(alpha = 0.35f),
                    radius = radius,
                    center = Offset(fadeInX, fadeNodeCenterY)
                )
                drawCircle(
                    color = handleCoreColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(fadeInX, fadeNodeCenterY)
                )
            }

            // Top Fade Out Node Grip Handle in Ableton Teal (Centered at y = 0, overflowing 50% top)
            if (fadeOutX in -10f..w + 10f) {
                val isDraggingFadeOut = activeDragTarget == DragTarget.FadeOutNode
                val radius = if (isDraggingFadeOut) nodeRadiusPx + 1.5.dp.toPx() else nodeRadiusPx

                drawCircle(
                    color = abletonTeal,
                    radius = radius,
                    center = Offset(fadeOutX, fadeNodeCenterY),
                    style = Stroke(2.dp.toPx())
                )
                drawCircle(
                    color = abletonTeal.copy(alpha = 0.35f),
                    radius = radius,
                    center = Offset(fadeOutX, fadeNodeCenterY)
                )
                drawCircle(
                    color = handleCoreColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(fadeOutX, fadeNodeCenterY)
                )
            }

            // --- Range Trimming Handles (Start & End) in Ableton Teal ---
            // Start Range Handle at sX
            if (sX in -20f..w + 20f) {
                val isHoverOrDrag = activeDragTarget == DragTarget.StartFlag || activeDragTarget == DragTarget.Body

                drawLine(
                    color = abletonTeal,
                    start = Offset(sX, 0f),
                    end = Offset(sX, h),
                    strokeWidth = if (isHoverOrDrag) 3.dp.toPx() else 2.dp.toPx()
                )

                // Top Node Cap (Centered at y = 0, 50% top overflow)
                val topRadius = if (isHoverOrDrag) nodeRadiusPx + 1.5.dp.toPx() else nodeRadiusPx
                drawCircle(
                    color = abletonTeal,
                    radius = topRadius,
                    center = Offset(sX, fadeNodeCenterY)
                )
                drawCircle(
                    color = handleCoreColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(sX, fadeNodeCenterY)
                )

                // Bottom Node Cap (Centered at y = h, 50% bottom overflow)
                drawCircle(
                    color = abletonTeal,
                    radius = topRadius,
                    center = Offset(sX, h)
                )
                drawCircle(
                    color = handleCoreColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(sX, h)
                )
            }

            // End Range Handle at eX
            if (eX in -20f..w + 20f) {
                val isHoverOrDrag = activeDragTarget == DragTarget.EndFlag || activeDragTarget == DragTarget.Body

                drawLine(
                    color = abletonTeal,
                    start = Offset(eX, 0f),
                    end = Offset(eX, h),
                    strokeWidth = if (isHoverOrDrag) 3.dp.toPx() else 2.dp.toPx()
                )

                // Top Node Cap (Centered at y = 0, 50% top overflow)
                val topRadius = if (isHoverOrDrag) nodeRadiusPx + 1.5.dp.toPx() else nodeRadiusPx
                drawCircle(
                    color = abletonTeal,
                    radius = topRadius,
                    center = Offset(eX, fadeNodeCenterY)
                )
                drawCircle(
                    color = handleCoreColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(eX, fadeNodeCenterY)
                )

                // Bottom Node Cap (Centered at y = h, 50% bottom overflow)
                drawCircle(
                    color = abletonTeal,
                    radius = topRadius,
                    center = Offset(eX, h)
                )
                drawCircle(
                    color = handleCoreColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(eX, h)
                )
            }

            // --- Shadcn Live Drag Tooltip Badge ---
            dragTooltipText?.let { tooltip ->
                val textLayout = textMeasurer.measure(
                    text = tooltip,
                    style = TextStyle(
                        color = popoverForegroundColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                val bgW = textLayout.size.width + 16f
                val bgH = textLayout.size.height + 8f
                val tooltipX = (w - bgW) / 2f
                val tooltipY = 16.dp.toPx()

                drawRoundRect(
                    color = popoverColor,
                    topLeft = Offset(tooltipX, tooltipY),
                    size = Size(bgW, bgH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(tooltipX, tooltipY),
                    size = Size(bgW, bgH),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(1f)
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(tooltipX + 8f, tooltipY + 4f)
                )
            }
        }
    }
}

private fun formatRulerTime(timeMs: Float): String {
    val totalSec = timeMs / 1000f
    return if (totalSec < 10f) {
        val hundredths = ((timeMs % 1000f) / 10f).toInt().toString().padStart(2, '0')
        "${totalSec.toInt()}.$hundredths s"
    } else {
        val tenths = ((timeMs % 1000f) / 100f).toInt()
        "${totalSec.toInt()}.$tenths s"
    }
}
