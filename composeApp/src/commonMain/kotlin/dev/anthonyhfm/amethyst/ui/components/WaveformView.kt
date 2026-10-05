package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import dev.anthonyhfm.amethyst.core.engine.audio.source.PcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.AudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.ByteArrayPcmAudioSource
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
import androidx.compose.ui.graphics.drawscope.clipRect
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
    return computeWaveformEnvelope(
        sampleCount = samples.size,
        sampleAt = { samples[it] },
        startSample = startSample,
        endSample = endSample,
        zoomLevel = zoomLevel,
        sampleRate = sampleRate,
        timelineStartUs = timelineStartUs,
        widthPx = widthPx,
        maxBuckets = maxBuckets,
    )
}

internal fun computeWaveformEnvelope(
    source: AudioSource,
    startSample: Long,
    endSample: Long,
    zoomLevel: Float,
    sampleRate: Int,
    timelineStartUs: Long = 0L,
    widthPx: Int? = null,
    maxBuckets: Int = 20_000,
    checkActive: () -> Unit = {},
): FloatArray = computeWaveformEnvelope(
    sampleCount = source.frameCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    sampleAt = { frame ->
        var sum = 0f
        var channel = 0
        while (channel < source.channels) {
            sum += source.sample(frameIndex = frame.toLong(), channel = channel).coerceIn(-1f, 1f)
            channel++
        }
        sum / source.channels
    },
    startSample = startSample,
    endSample = endSample,
    zoomLevel = zoomLevel,
    sampleRate = sampleRate,
    timelineStartUs = timelineStartUs,
    widthPx = widthPx,
    maxBuckets = maxBuckets,
    checkActive = checkActive,
)

private fun computeWaveformEnvelope(
    sampleCount: Int,
    sampleAt: (Int) -> Float,
    startSample: Long,
    endSample: Long,
    zoomLevel: Float,
    sampleRate: Int,
    timelineStartUs: Long,
    widthPx: Int?,
    maxBuckets: Int,
    checkActive: () -> Unit = {},
): FloatArray {
    if (sampleCount <= 0) {
        return FloatArray(0)
    }
    val sStart = startSample.toInt().coerceIn(0, sampleCount)
    val sEnd = endSample.toInt().coerceIn(sStart, sampleCount)
    val subsetLen = sEnd - sStart
    if (subsetLen <= 0) {
        return FloatArray(0)
    }
    if (subsetLen == 1) {
        val amp = kotlin.math.abs(sampleAt(sStart)).coerceIn(0f, 1f)
        return floatArrayOf(amp, amp)
    }

    // Editors such as SampleChainDevice already know their concrete render width.
    // Derive buckets directly from sample indices in that case. The timeline-based
    // calculation below is useful for timeline positioning, but can round every
    // bucket outside a clip when a short/fixed editor width is supplied.
    widthPx?.takeIf { it > 0 }?.let { width ->
        val bucketCount = width.coerceIn(2, maxBuckets)
        return FloatArray(bucketCount) { bucketIndex ->
            checkActive()
            val localStart = ((bucketIndex.toLong() * subsetLen) / bucketCount)
                .toInt()
                .coerceIn(0, subsetLen - 1)
            val localEnd = (((bucketIndex + 1L) * subsetLen + bucketCount - 1) / bucketCount)
                .toInt()
                .coerceIn(localStart + 1, subsetLen)
            var maxAmp = 0f
            for (sampleIndex in (sStart + localStart) until (sStart + localEnd)) {
                val value = kotlin.math.abs(sampleAt(sampleIndex))
                if (value > maxAmp) {
                    maxAmp = value
                }
            }
            maxAmp.coerceIn(0f, 1f)
        }
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
        checkActive()
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
            val value = kotlin.math.abs(sampleAt(sampleIndex))
            if (value > maxAmp) {
                maxAmp = value
            }
        }
        maxAmp.coerceIn(0f, 1f)
    }
}

@Composable
internal fun rememberWaveformEnvelope(
    source: AudioSource?,
    startSample: Long,
    endSample: Long,
    zoomLevel: Float,
    sampleRate: Int,
    timelineStartUs: Long = 0L,
    widthPx: Int? = null,
): FloatArray {
    val range = listOf(startSample, endSample, zoomLevel, sampleRate, timelineStartUs, widthPx)
    var envelope by remember(source, range) { mutableStateOf(value = FloatArray(size = 0)) }
    LaunchedEffect(source, range) {
        envelope = withContext(context = Dispatchers.Default) {
            if (source == null) {
                FloatArray(size = 0)
            } else {
                val context = coroutineContext
                try {
                    computeWaveformEnvelope(
                        source = source,
                        startSample = startSample,
                        endSample = endSample,
                        zoomLevel = zoomLevel,
                        sampleRate = sampleRate,
                        timelineStartUs = timelineStartUs,
                        widthPx = widthPx,
                        checkActive = { context.ensureActive() },
                    )
                } finally {
                    (source as? PcmAudioSource)?.pcmBytes?.releaseCachedPages()
                }
            }
        }
    }
    return envelope
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
    rawData: ByteArray? = null,
    pcmSource: AudioSource? = null,
    sampleRate: Int,
    channels: Int,
    bitDepth: Int,
    timelineStartUs: Long,
    startSample: Long,
    endSample: Long,
    renderWidthPx: Int? = null,
    modifier: Modifier = Modifier,
    waveColor: Color = Color.White,
    playedProgress: Float = 0f,
    playedWaveColor: Color? = null,
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

    val resolvedChannels = if (channels > 0) channels else 2
    val resolvedBitDepth = if (bitDepth in listOf(8, 16, 24, 32)) bitDepth else 16
    val resolvedSampleRate = if (sampleRate > 0) sampleRate else 44100

    val source = remember(rawData, pcmSource, resolvedBitDepth, resolvedChannels, resolvedSampleRate) {
        pcmSource ?: rawData?.takeIf { it.isNotEmpty() }?.let {
            ByteArrayPcmAudioSource(
                id = "waveform",
                sampleRate = resolvedSampleRate,
                channels = resolvedChannels,
                bitDepth = resolvedBitDepth,
                rawData = it,
            )
        }
    }
    val sampleCount = source?.frameCount ?: 0L

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

    val amps = rememberWaveformEnvelope(
        source = source,
        startSample = startSample,
        endSample = endSample,
        zoomLevel = zoomLevel,
        sampleRate = resolvedSampleRate,
        timelineStartUs = timelineStartUs,
        widthPx = effectiveWidthPx.takeIf { it > 0 },
    )

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
                                    // The trim boundary is the primary editing action. Give it a
                                    // larger hit target than fades so boundaries stay draggable.
                                    val edgeHandleSize = 32f

                                    val durationMs = if (sampleRate > 0) (sampleCount.toFloat() / sampleRate) * 1000f else 0f
                                    val activeDurationMs = durationMs * (currentEndPosition - currentStartPosition)
                                    val fadeInRatio = if (activeDurationMs > 0f) (currentFadeInMs / activeDurationMs).coerceIn(0f, 1f) else 0f
                                    val fadeOutRatio = if (activeDurationMs > 0f) (currentFadeOutMs / activeDurationMs).coerceIn(0f, 1f) else 0f
                                    val fadeInX = startX + activeWidth * fadeInRatio
                                    val fadeOutX = endX - activeWidth * fadeOutRatio

                                    when {
                                        onStartPositionChange != null && x in (startX - edgeHandleSize)..(startX + edgeHandleSize) -> isDraggingStart = true
                                        onEndPositionChange != null && x in (endX - edgeHandleSize)..(endX + edgeHandleSize) -> isDraggingEnd = true
                                        onFadeInChange != null && kotlin.math.abs(x - fadeInX) < 18f && fadeInX > startX + edgeHandleSize -> isDraggingFadeIn = true
                                        onFadeOutChange != null && kotlin.math.abs(x - fadeOutX) < 18f && fadeOutX < endX - edgeHandleSize -> isDraggingFadeOut = true
                                        onStartPositionChange != null && onEndPositionChange != null && x in startX..endX -> isDraggingBody = true
                                    }
                                },
                                onDrag = { _, dragAmount ->
                                    val w = size.width.toFloat()
                                    val delta = dragAmount.x / w
                                    val durationMs = if (sampleRate > 0) (sampleCount.toFloat() / sampleRate) * 1000f else 0f
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
            playedWaveColor?.let { playedColor ->
                val playedWidth = w * playedProgress.coerceIn(0f, 1f)
                if (playedWidth > 0f) {
                    clipRect(right = playedWidth) {
                        drawPath(path = path, color = playedColor.copy(alpha = 0.9f))
                    }
                }
            }

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

            val signalDurationMs = if (sampleRate > 0) (sampleCount.toFloat() / sampleRate) * 1000f else 0f
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
            val tabW = 16f; val tabH = 24f
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
