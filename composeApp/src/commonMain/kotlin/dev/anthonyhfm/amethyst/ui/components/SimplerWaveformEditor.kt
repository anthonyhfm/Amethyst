package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragTarget {
    None,
    StartFlag,
    EndFlag,
    Body,
    FadeInNode,
    FadeOutNode,
    PanView
}

/**
 * Modern, Ableton Live-styled interactive waveform editor tailored for Amethyst.
 *
 * Features:
 * - Clean Light Slate-Gray (`#CBD5E1`) solid filled waveform display
 * - Ableton Slate Teal (`#5BB5DA`) for interactive handles, trimming lines, and fade controls
 * - Top node caps centered at y = 0, overflowing 50% above the top boundary
 * - Maximized vertical space without minimap or time ruler
 */
@Composable
fun SimplerWaveformEditor(
    rawData: ByteArray?,
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
    onStartPositionFinishChange: (() -> Unit)? = null,
    onEndPositionFinishChange: (() -> Unit)? = null,
    onFadeInChange: (Float) -> Unit,
    onFadeOutChange: (Float) -> Unit,
    onFadeInFinishChange: (() -> Unit)? = null,
    onFadeOutFinishChange: (() -> Unit)? = null,
    playheadPosition: Float? = null,
    modifier: Modifier = Modifier
) {
    val resolvedChannels = if (channels > 0) channels else 2
    val resolvedBitDepth = if (bitDepth in listOf(8, 16, 24, 32)) bitDepth else 16

    // Decode mono PCM floats once per rawData change
    val samples: FloatArray = remember(rawData, resolvedBitDepth, resolvedChannels) {
        val bytes = rawData ?: return@remember FloatArray(0)
        pcmToMonoFloats(bytes, resolvedBitDepth, resolvedChannels)
    }

    // Viewport state for zooming & panning (0.0f .. 1.0f)
    var viewStart by remember { mutableStateOf(0.0f) }
    var viewEnd by remember { mutableStateOf(1.0f) }

    val currentViewSpan = (viewEnd - viewStart).coerceIn(0.005f, 1.0f)

    // Current state values captured via state holders for gesture callbacks
    val currentStart by rememberUpdatedState(startPosition)
    val currentEnd by rememberUpdatedState(endPosition)
    val currentFadeInMs by rememberUpdatedState(fadeInMs)
    val currentFadeOutMs by rememberUpdatedState(fadeOutMs)
    val currentDurationMs by rememberUpdatedState(totalDurationMs)

    // Drag interaction states & initial values
    var activeDragTarget by remember { mutableStateOf(DragTarget.None) }
    var dragTooltipText by remember { mutableStateOf<String?>(null) }

    var initialStartFrac by remember { mutableStateOf(0f) }
    var initialEndFrac by remember { mutableStateOf(0f) }
    var initialFadeInMs by remember { mutableStateOf(0f) }
    var initialFadeOutMs by remember { mutableStateOf(0f) }
    var initialViewStartFrac by remember { mutableStateOf(0f) }
    var accumulatedDragPx by remember { mutableStateOf(0f) }

    var canvasWidthPx by remember { mutableStateOf(0f) }
    var canvasHeightPx by remember { mutableStateOf(0f) }

    // Theme color palette references
    val colors = Theme[colors]
    val darkSlateBg = Color(0xFF141619) // Authentic Dark Slate Surface
    val borderColor = colors[border]
    val mutedForegroundColor = colors[mutedForeground]
    val lightGrayWaveform = Color(0xFFCBD5E1) // Clean Light Slate-Gray
    val popoverColor = colors[popover]
    val popoverForegroundColor = colors[popoverForeground]

    // Ableton Slate Teal Accent for Interactive Controls & Handles
    val abletonTeal = Color(0xFF5BB5DA)
    val handleCoreColor = Color(0xFF141619)

    // Compute high-res envelope for visible zoomed viewport
    val visibleStartSample = (samples.size * viewStart).toLong().coerceIn(0L, samples.size.toLong())
    val visibleEndSample = (samples.size * viewEnd).toLong().coerceIn(visibleStartSample, samples.size.toLong())
    val visibleAmps: FloatArray = remember(
        samples,
        visibleStartSample,
        visibleEndSample,
        canvasWidthPx
    ) {
        if (samples.isEmpty() || visibleEndSample <= visibleStartSample) FloatArray(0)
        else computeWaveformEnvelope(
            samples = samples,
            startSample = visibleStartSample,
            endSample = visibleEndSample,
            zoomLevel = 1f,
            sampleRate = sampleRate,
            widthPx = canvasWidthPx.roundToInt().coerceAtLeast(100)
        )
    }

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
            .pointerInput(viewStart, viewEnd) {
                // Pan via scroll wheel. Plain vertical scrolling is left untouched;
                // waveform zooming is temporarily disabled.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val isCmdOrCtrl = event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed
                            val change = event.changes.firstOrNull() ?: continue
                            val deltaY = change.scrollDelta.y
                            val deltaX = change.scrollDelta.x

                            val w = size.width.toFloat()
                            if (w <= 0f) continue

                            if (isCmdOrCtrl || deltaX != 0f) {
                                // Pan mode
                                val panStepFrac = (if (deltaX != 0f) deltaX else deltaY) * 0.05f * currentViewSpan
                                val newStart = (viewStart + panStepFrac).coerceIn(0f, 1f - currentViewSpan)
                                viewStart = newStart
                                viewEnd = newStart + currentViewSpan
                                change.consume()
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f) return@detectDragGestures

                        initialStartFrac = currentStart
                        initialEndFrac = currentEnd
                        initialFadeInMs = currentFadeInMs
                        initialFadeOutMs = currentFadeOutMs
                        initialViewStartFrac = viewStart
                        accumulatedDragPx = 0f

                        val sX = ((currentStart - viewStart) / currentViewSpan) * w
                        val eX = ((currentEnd - viewStart) / currentViewSpan) * w

                        val activeDurMs = (currentDurationMs * (currentEnd - currentStart)).coerceAtLeast(1f)
                        val fadeInRatio = (currentFadeInMs / activeDurMs).coerceIn(0f, 1f)
                        val fadeOutRatio = (currentFadeOutMs / activeDurMs).coerceIn(0f, 1f)

                        val fadeInX = sX + (eX - sX) * fadeInRatio
                        val fadeOutX = eX - (eX - sX) * fadeOutRatio

                        val isCmdOrCtrl = ModifierKeysState.isMetaPressed || ModifierKeysState.isCtrlPressed
                        val hitSlopPx = 28f

                        val isTopZone = offset.y <= 28f
                        val isNearFadeIn = abs(offset.x - fadeInX) <= hitSlopPx
                        val isNearFadeOut = abs(offset.x - fadeOutX) <= hitSlopPx
                        val isNearStart = abs(offset.x - sX) <= hitSlopPx
                        val isNearEnd = abs(offset.x - eX) <= hitSlopPx

                        when {
                            isCmdOrCtrl -> activeDragTarget = DragTarget.PanView
                            // Top zone priority for Fade nodes
                            isTopZone && isNearFadeIn -> {
                                activeDragTarget = DragTarget.FadeInNode
                                dragTooltipText = "Fade In: ${currentFadeInMs.roundToInt()} ms"
                            }
                            isTopZone && isNearFadeOut -> {
                                activeDragTarget = DragTarget.FadeOutNode
                                dragTooltipText = "Fade Out: ${currentFadeOutMs.roundToInt()} ms"
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
                            else -> activeDragTarget = DragTarget.None
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val w = size.width.toFloat()
                        if (w <= 0f || activeDragTarget == DragTarget.None) return@detectDragGestures

                        accumulatedDragPx += dragAmount.x
                        val totalDeltaFrac = (accumulatedDragPx / w) * currentViewSpan
                        val activeDurMs = (currentDurationMs * (currentEnd - currentStart)).coerceAtLeast(1f)

                        when (activeDragTarget) {
                            DragTarget.StartFlag -> {
                                val newStart = (initialStartFrac + totalDeltaFrac).coerceIn(0f, currentEnd - 0.001f)
                                onStartPositionChange(newStart)
                                dragTooltipText = "Start: ${formatRulerTime(totalDurationMs * newStart)}"
                            }
                            DragTarget.EndFlag -> {
                                val newEnd = (initialEndFrac + totalDeltaFrac).coerceIn(currentStart + 0.001f, 1f)
                                onEndPositionChange(newEnd)
                                dragTooltipText = "End: ${formatRulerTime(totalDurationMs * newEnd)}"
                            }
                            DragTarget.Body -> {
                                val span = initialEndFrac - initialStartFrac
                                val newStart = (initialStartFrac + totalDeltaFrac).coerceIn(0f, 1f - span)
                                val newEnd = newStart + span
                                onStartPositionChange(newStart)
                                onEndPositionChange(newEnd)
                                dragTooltipText = "Range: ${formatRulerTime(totalDurationMs * newStart)} - ${formatRulerTime(totalDurationMs * newEnd)}"
                            }
                            DragTarget.FadeInNode -> {
                                val sX = ((currentStart - viewStart) / currentViewSpan) * w
                                val eX = ((currentEnd - viewStart) / currentViewSpan) * w
                                val activeWidthPx = (eX - sX).coerceAtLeast(1f)
                                val deltaActiveRatio = accumulatedDragPx / activeWidthPx
                                val newFadeIn = (initialFadeInMs + deltaActiveRatio * activeDurMs).coerceIn(0f, activeDurMs)
                                onFadeInChange(newFadeIn)
                                dragTooltipText = "Fade In: ${newFadeIn.roundToInt()} ms"
                            }
                            DragTarget.FadeOutNode -> {
                                val sX = ((currentStart - viewStart) / currentViewSpan) * w
                                val eX = ((currentEnd - viewStart) / currentViewSpan) * w
                                val activeWidthPx = (eX - sX).coerceAtLeast(1f)
                                val deltaActiveRatio = -accumulatedDragPx / activeWidthPx
                                val newFadeOut = (initialFadeOutMs + deltaActiveRatio * activeDurMs).coerceIn(0f, activeDurMs)
                                onFadeOutChange(newFadeOut)
                                dragTooltipText = "Fade Out: ${newFadeOut.roundToInt()} ms"
                            }
                            DragTarget.PanView -> {
                                val panDelta = -totalDeltaFrac
                                val newStart = (initialViewStartFrac + panDelta).coerceIn(0f, 1f - currentViewSpan)
                                viewStart = newStart
                                viewEnd = newStart + currentViewSpan
                            }
                            DragTarget.None -> {}
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        when (activeDragTarget) {
                            DragTarget.StartFlag, DragTarget.Body -> onStartPositionFinishChange?.invoke()
                            DragTarget.EndFlag -> onEndPositionFinishChange?.invoke()
                            DragTarget.FadeInNode -> onFadeInFinishChange?.invoke()
                            DragTarget.FadeOutNode -> onFadeOutFinishChange?.invoke()
                            else -> {}
                        }
                        activeDragTarget = DragTarget.None
                        dragTooltipText = null
                    },
                    onDragCancel = {
                        activeDragTarget = DragTarget.None
                        dragTooltipText = null
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        viewStart = 0f
                        viewEnd = 1f
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

            fun fracToX(frac: Float): Float = ((frac - viewStart) / currentViewSpan) * w

            val sX = fracToX(startPosition)
            val eX = fracToX(endPosition)

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
                ?.takeIf { it.isFinite() && it in viewStart..viewEnd }
                ?.let { position ->
                    val playheadX = fracToX(position)
                    drawLine(
                        color = Color(0xFFFFC857),
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, h),
                        strokeWidth = 2.dp.toPx(),
                    )
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

private fun pcmToMonoFloats(raw: ByteArray, bitDepth: Int, channels: Int): FloatArray {
    val ch = if (channels > 0) channels else 2
    val bd = if (bitDepth in listOf(8, 16, 24, 32)) bitDepth else 16
    val bps = bd / 8
    val frameSize = bps * ch
    if (raw.size < frameSize) return FloatArray(0)
    val frames = raw.size / frameSize
    val out = FloatArray(frames)
    var frameIdx = 0
    var byteIndex = 0
    while (frameIdx < frames) {
        var sum = 0f
        var c = 0
        while (c < ch) {
            val off = byteIndex + c * bps
            val sample = when (bd) {
                8 -> { val u = raw[off].toInt() and 0xFF; ((u - 128) / 128f).coerceIn(-1f, 1f) }
                16 -> { val lo = raw[off].toInt() and 0xFF; val hi = raw[off + 1].toInt() shl 8; val s = (lo or hi).toShort().toInt(); (s / 32768f).coerceIn(-1f, 1f) }
                24 -> { val b0 = raw[off].toInt() and 0xFF; val b1 = raw[off + 1].toInt() and 0xFF; val b2 = raw[off + 2].toInt(); var v = b0 or (b1 shl 8) or (b2 shl 16); if ((v and 0x800000) != 0) v = v or -0x1000000; (v / 8388608f).coerceIn(-1f, 1f) }
                32 -> { val b0 = raw[off].toInt() and 0xFF; val b1 = raw[off + 1].toInt() and 0xFF; val b2 = raw[off + 2].toInt() and 0xFF; val b3 = raw[off + 3].toInt(); val v = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24); if (v == Int.MIN_VALUE) -1f else (v / 2147483648f).coerceIn(-1f, 1f) }
                else -> { val lo = raw[off].toInt() and 0xFF; val hi = raw[off + 1].toInt() shl 8; val s = (lo or hi).toShort().toInt(); (s / 32768f).coerceIn(-1f, 1f) }
            }
            sum += sample
            c++
        }
        out[frameIdx] = sum / ch
        frameIdx++
        byteIndex += frameSize
    }
    return out
}
