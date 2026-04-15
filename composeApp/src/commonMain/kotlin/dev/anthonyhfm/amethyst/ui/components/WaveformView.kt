package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

internal fun computeWaveformEnvelope(
    samples: FloatArray,
    startSample: Long,
    endSample: Long,
    zoomLevel: Float,
    sampleRate: Int,
    timelineStartUs: Long = 0L,
    widthPx: Int? = null,
    maxBuckets: Int = 20_000
): FloatArray {
    if (samples.isEmpty()) return FloatArray(0)
    val sStart = startSample.toInt().coerceIn(0, samples.size)
    val sEnd = endSample.toInt().coerceIn(sStart, samples.size)
    val subsetLen = sEnd - sStart
    if (subsetLen <= 0) return FloatArray(0)
    if (subsetLen == 1) {
        val amp = kotlin.math.abs(samples[sStart]).coerceIn(0f, 1f)
        return floatArrayOf(amp, amp)
    }

    val safeZoom = zoomLevel.coerceAtLeast(0.0001f)
    val clipDurationUs = (subsetLen.toDouble() * 1_000_000.0) / sampleRate.toDouble()
    val exactStartPx = (timelineStartUs.toDouble() / 1000.0) * safeZoom.toDouble()
    val exactEndPx = ((timelineStartUs.toDouble() + clipDurationUs) / 1000.0) * safeZoom.toDouble()
    // Round to the nearest pixel bucket — matching the rounding used by timeUsToPx()
    // (roundToInt) when positioning the clip's Box.  Using floor() instead would place
    // bucket 0 up to one full bucket *before* the clip's visual left edge, causing the
    // rendered waveform to appear shifted earlier than the timeline ruler at low zoom
    // levels (e.g. ≈ 40 ms offset at zoom = 0.025 px/ms).
    val firstBucketIndex = (exactStartPx + 0.5).toLong()
    val resolvedWidthPx = widthPx ?: (
        ceil(exactEndPx).toInt() - firstBucketIndex.toInt()
    ).coerceAtLeast(1)
    val bucketCount = resolvedWidthPx.coerceIn(2, maxBuckets)
    val bucketDurationUs = 1000.0 / safeZoom.toDouble()

    return FloatArray(bucketCount) { bucketIndex ->
        val absoluteBucketStartUs = (firstBucketIndex + bucketIndex).toDouble() * bucketDurationUs
        val absoluteBucketEndUs = absoluteBucketStartUs + bucketDurationUs
        val clippedBucketStartUs = maxOf(absoluteBucketStartUs, timelineStartUs.toDouble())
        val clippedBucketEndUs = min(absoluteBucketEndUs, timelineStartUs.toDouble() + clipDurationUs)
        if (clippedBucketEndUs <= clippedBucketStartUs) {
            return@FloatArray 0f
        }

        val relativeStartUs = clippedBucketStartUs - timelineStartUs.toDouble()
        val relativeEndUs = clippedBucketEndUs - timelineStartUs.toDouble()
        val localStart = floor((relativeStartUs * sampleRate.toDouble()) / 1_000_000.0)
            .toInt()
            .coerceIn(0, subsetLen - 1)
        val localEnd = ceil((relativeEndUs * sampleRate.toDouble()) / 1_000_000.0)
            .toInt()
            .coerceIn(localStart + 1, subsetLen)
        var maxAmp = 0f
        for (sampleIndex in (sStart + localStart) until (sStart + localEnd)) {
            val value = kotlin.math.abs(samples[sampleIndex])
            if (value > maxAmp) maxAmp = value
        }
        maxAmp.coerceIn(0f, 1f)
    }
}

/**
 * Renders a PCM audio waveform.
 *
 * The visible range is defined by [startSample]..[endSample] — direct indices into the
 * decoded sample array. No ms→sample conversion happens here; callers that have
 * sample-accurate in/out points (e.g. [AudioEntry]) pass them directly.
 *
 * [zoomLevel] (px per ms) is used to decide bucket density so the visual resolution
 * matches the zoom level rather than the drawn pixel width.
 */
@Composable
fun WaveformView(
    rawData: ByteArray?,
    sampleRate: Int,
    channels: Int,
    bitDepth: Int,
    timelineStartUs: Long,
    startSample: Long,
    endSample: Long,
    renderWidthPx: Int? = null,
    modifier: Modifier = Modifier,
    waveColor: Color = Color.White,
    onSeek: ((Float) -> Unit)? = null,
    zoomLevel: Float,
    fadeInMs: Float = 0f,
    fadeOutMs: Float = 0f,
    startPosition: Float = 0f,
    endPosition: Float = 1f,
    onStartPositionChange: ((Float) -> Unit)? = null,
    onEndPositionChange: ((Float) -> Unit)? = null,
    onFadeInChange: ((Float) -> Unit)? = null,
    onFadeOutChange: ((Float) -> Unit)? = null,
    maxFadeMs: Float = 1000f,
) {
    val wave = waveColor
    val baseline = waveColor.copy(alpha = 0.6f)
    val MAX_BUCKETS = 20_000

    // Decode PCM → mono floats once per rawData identity change.
    val samples: FloatArray = remember(rawData, bitDepth, channels) {
        val bytes = rawData ?: return@remember FloatArray(0)
        pcmToMonoFloats(bytes, bitDepth, channels)
    }

    val currentStartPosition by rememberUpdatedState(startPosition)
    val currentEndPosition by rememberUpdatedState(endPosition)
    val currentFadeInMs by rememberUpdatedState(fadeInMs)
    val currentFadeOutMs by rememberUpdatedState(fadeOutMs)
    val currentMaxFadeMs by rememberUpdatedState(maxFadeMs)

    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }
    var isDraggingBody by remember { mutableStateOf(false) }
    var isDraggingFadeIn by remember { mutableStateOf(false) }
    var isDraggingFadeOut by remember { mutableStateOf(false) }
    var measuredWidthPx by remember { mutableStateOf(0) }
    val effectiveWidthPx = renderWidthPx?.takeIf { it > 0 } ?: measuredWidthPx

    // Compute envelope once per (samples, startSample, endSample, zoomLevel, sampleRate) — NOT
    // inside the Canvas block which redraws every frame. Moving here cuts CPU from O(N) every
    // frame to O(N) only when parameters change.
    val amps: FloatArray = remember(samples, startSample, endSample, timelineStartUs, zoomLevel, sampleRate, effectiveWidthPx) {
        computeWaveformEnvelope(
            samples = samples,
            startSample = startSample,
            endSample = endSample,
            zoomLevel = zoomLevel,
            sampleRate = sampleRate,
            timelineStartUs = timelineStartUs,
            widthPx = effectiveWidthPx.takeIf { it > 0 },
            maxBuckets = MAX_BUCKETS
        )
    }

    Box(modifier = modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { measuredWidthPx = it.width }
                .then(
                    if (onSeek != null)
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { offset ->
                                onSeek((offset.x / size.width).coerceIn(0f, 1f))
                            }
                        }
                    else Modifier
                )
                .then(
                    if (onStartPositionChange != null || onEndPositionChange != null || onFadeInChange != null || onFadeOutChange != null)
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val x = offset.x
                                    val w = size.width.toFloat()
                                    val startX = w * currentStartPosition
                                    val endX = w * currentEndPosition
                                    val activeWidth = endX - startX
                                    val edgeHandleSize = 24f

                                    val durationMs = if (sampleRate > 0) (samples.size.toFloat() / sampleRate) * 1000f else 0f
                                    val activeDurationMs = durationMs * (currentEndPosition - currentStartPosition)
                                    val fadeInRatio = if (activeDurationMs > 0f) (currentFadeInMs / activeDurationMs).coerceIn(0f, 1f) else 0f
                                    val fadeOutRatio = if (activeDurationMs > 0f) (currentFadeOutMs / activeDurationMs).coerceIn(0f, 1f) else 0f
                                    val fadeInX = startX + activeWidth * fadeInRatio
                                    val fadeOutX = endX - activeWidth * fadeOutRatio

                                    when {
                                        onFadeInChange != null && kotlin.math.abs(x - fadeInX) < 16f && fadeInX > startX -> isDraggingFadeIn = true
                                        onFadeOutChange != null && kotlin.math.abs(x - fadeOutX) < 16f && fadeOutX < endX -> isDraggingFadeOut = true
                                        onStartPositionChange != null && x in (startX - edgeHandleSize)..(startX + edgeHandleSize) -> isDraggingStart = true
                                        onEndPositionChange != null && x in (endX - edgeHandleSize)..(endX + edgeHandleSize) -> isDraggingEnd = true
                                        onStartPositionChange != null && onEndPositionChange != null && x in startX..endX -> isDraggingBody = true
                                    }
                                },
                                onDrag = { _, dragAmount ->
                                    val w = size.width.toFloat()
                                    val delta = dragAmount.x / w
                                    val durationMs = if (sampleRate > 0) (samples.size.toFloat() / sampleRate) * 1000f else 0f
                                    val activeDurationMs = durationMs * (currentEndPosition - currentStartPosition)

                                    when {
                                        isDraggingStart -> onStartPositionChange?.invoke((currentStartPosition + delta).coerceIn(0f, currentEndPosition - 0.01f))
                                        isDraggingEnd -> onEndPositionChange?.invoke((currentEndPosition + delta).coerceIn(currentStartPosition + 0.01f, 1f))
                                        isDraggingBody -> {
                                            val span = currentEndPosition - currentStartPosition
                                            val newStart = (currentStartPosition + delta).coerceIn(0f, 1f - span)
                                            onStartPositionChange?.invoke(newStart)
                                            onEndPositionChange?.invoke(newStart + span)
                                        }
                                        isDraggingFadeIn -> {
                                            val msDelta = delta * activeDurationMs
                                            onFadeInChange?.invoke((currentFadeInMs + msDelta).coerceIn(0f, currentMaxFadeMs))
                                        }
                                        isDraggingFadeOut -> {
                                            val msDelta = -delta * activeDurationMs
                                            onFadeOutChange?.invoke((currentFadeOutMs + msDelta).coerceIn(0f, currentMaxFadeMs))
                                        }
                                    }
                                },
                                onDragEnd = {
                                    isDraggingStart = false; isDraggingEnd = false
                                    isDraggingBody = false; isDraggingFadeIn = false; isDraggingFadeOut = false
                                }
                            )
                        }
                    else Modifier
                )
        ) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            val half = centerY

            drawLine(color = baseline, start = Offset(0f, centerY), end = Offset(w, centerY), strokeWidth = 1f)

            if (amps.isEmpty() || w <= 1f) return@Canvas
            val bucketCount = amps.size

            val path = Path().apply {
                moveTo(0f, centerY)
                var i = 0
                while (i < bucketCount) {
                    val x = (i.toFloat() / (bucketCount - 1).toFloat()) * w
                    lineTo(x, centerY - amps[i] * half)
                    i++
                }
                lineTo(w, centerY - amps.last() * half)
                lineTo(w, centerY)
                i = bucketCount - 1
                while (i >= 0) {
                    val x = (i.toFloat() / (bucketCount - 1).toFloat()) * w
                    lineTo(x, centerY + amps[i] * half)
                    i--
                }
                lineTo(0f, centerY + amps.first() * half)
                lineTo(0f, centerY)
                close()
            }

            drawPath(path = path, color = wave.copy(alpha = 0.6f))

            // Active region overlays
            val startX = w * startPosition
            val endX = w * endPosition
            val activeWidth = endX - startX

            if (startPosition > 0f) {
                drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(startX, h))
            }
            if (endPosition < 1f) {
                drawRect(color = Color.Black.copy(alpha = 0.5f), topLeft = Offset(endX, 0f),
                    size = androidx.compose.ui.geometry.Size(w - endX, h))
            }

            val signalDurationMs = if (sampleRate > 0) (samples.size.toFloat() / sampleRate) * 1000f else 0f
            val activeDurationMs = signalDurationMs * (endPosition - startPosition)

            if (fadeInMs > 0f && activeWidth > 0f && activeDurationMs > 0f) {
                val fadeInRatio = (fadeInMs / activeDurationMs).coerceIn(0f, 1f)
                val fadeInWidth = activeWidth * fadeInRatio
                drawPath(Path().apply {
                    moveTo(startX, 0f); lineTo(startX + fadeInWidth, 0f); lineTo(startX, h); close()
                }, SolidColor(Color.Black.copy(alpha = 0.6f)))
            }
            if (fadeOutMs > 0f && activeWidth > 0f && activeDurationMs > 0f) {
                val fadeOutRatio = (fadeOutMs / activeDurationMs).coerceIn(0f, 1f)
                val fadeOutWidth = activeWidth * fadeOutRatio
                drawPath(Path().apply {
                    moveTo(endX - fadeOutWidth, h); lineTo(endX, 0f); lineTo(endX, h); close()
                }, SolidColor(Color.Black.copy(alpha = 0.6f)))
            }

            // Trim handles
            val tabW = 14f; val tabH = 18f
            if (onStartPositionChange != null) {
                val lineColor = if (isDraggingStart || isDraggingBody) Color.White else Color.White.copy(alpha = 0.9f)
                drawLine(lineColor, Offset(startX, 0f), Offset(startX, h), 3f)
                drawRect(lineColor, Offset(startX, 0f), androidx.compose.ui.geometry.Size(tabW, tabH))
                drawRect(lineColor, Offset(startX, h - tabH), androidx.compose.ui.geometry.Size(tabW, tabH))
            }
            if (onEndPositionChange != null) {
                val lineColor = if (isDraggingEnd || isDraggingBody) Color.White else Color.White.copy(alpha = 0.9f)
                drawLine(lineColor, Offset(endX, 0f), Offset(endX, h), 3f)
                drawRect(lineColor, Offset(endX - tabW, 0f), androidx.compose.ui.geometry.Size(tabW, tabH))
                drawRect(lineColor, Offset(endX - tabW, h - tabH), androidx.compose.ui.geometry.Size(tabW, tabH))
            }

            // Fade handles
            if (onFadeInChange != null && fadeInMs > 0f && activeWidth > 0f && activeDurationMs > 0f) {
                val fadeInRatio = (fadeInMs / activeDurationMs).coerceIn(0f, 1f)
                val fadeInX = startX + activeWidth * fadeInRatio
                val fadeColor = if (isDraggingFadeIn) Color(0xFFFFD54F) else Color(0xFFFFB347)
                drawLine(fadeColor, Offset(fadeInX, 0f), Offset(fadeInX, h), 2f)
                drawPath(Path().apply {
                    moveTo(fadeInX, 2f); lineTo(fadeInX + 5f, 8f); lineTo(fadeInX, 14f); lineTo(fadeInX - 5f, 8f); close()
                }, fadeColor)
            }
            if (onFadeOutChange != null && fadeOutMs > 0f && activeWidth > 0f && activeDurationMs > 0f) {
                val fadeOutRatio = (fadeOutMs / activeDurationMs).coerceIn(0f, 1f)
                val fadeOutX = endX - activeWidth * fadeOutRatio
                val fadeColor = if (isDraggingFadeOut) Color(0xFF80DEEA) else Color(0xFF64B5F6)
                drawLine(fadeColor, Offset(fadeOutX, 0f), Offset(fadeOutX, h), 2f)
                drawPath(Path().apply {
                    moveTo(fadeOutX, 2f); lineTo(fadeOutX + 5f, 8f); lineTo(fadeOutX, 14f); lineTo(fadeOutX - 5f, 8f); close()
                }, fadeColor)
            }
        }
    }
}


private fun pcmToMonoFloats(raw: ByteArray, bitDepth: Int, channels: Int): FloatArray {
    val ch = channels.coerceAtLeast(1)
    val bps = (bitDepth / 8).coerceAtLeast(1)
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
            val sample = when (bitDepth) {
                8  -> { val u = raw[off].toInt() and 0xFF; ((u - 128) / 128f).coerceIn(-1f, 1f) }
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
