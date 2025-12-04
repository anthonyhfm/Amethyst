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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun WaveformView(
    signal: Signal.AudioSignal,
    totalDurationMs: Long,
    startMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    waveColor: Color = Color.White,
    onSeek: ((Float) -> Unit)? = null,
    zoomLevel: Float, // px per ms
    fadeInMs: Float = 0f,
    fadeOutMs: Float = 0f,
    startPosition: Float = 0f,
    endPosition: Float = 1f,
    onStartPositionChange: ((Float) -> Unit)? = null,
    onEndPositionChange: ((Float) -> Unit)? = null,
) {
    val wave = waveColor
    val baseline = waveColor.copy(alpha = 0.6f)

    val bucketWidthPx = 2f
    val MAX_BUCKETS = 20000 // mehr Detail für starken Zoom

    val samples: FloatArray = remember(signal.rawData, signal.bitDepth, signal.channels) {
        val bytes = signal.rawData ?: return@remember FloatArray(0)
        pcmToMonoFloats(bytes, signal.bitDepth, signal.channels)
    }

    // Cache für amplitude envelopes: key enthält BucketCount und Samples-Länge
    val envelopeCache = remember { mutableMapOf<Pair<Int, Int>, FloatArray>() }

    // State for drag handle interaction
    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (onSeek != null)
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val p = (offset.x / size.width).coerceIn(0f, 1f)
                                onSeek(p)
                            }
                        }
                    else Modifier
                )
                .then(
                    if (onStartPositionChange != null || onEndPositionChange != null)
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val x = offset.x
                                    val w = size.width
                                    val startX = w * startPosition
                                    val endX = w * endPosition
                                    val handleSize = 20f

                                    when {
                                        abs(x - startX) < handleSize -> isDraggingStart = true
                                        abs(x - endX) < handleSize -> isDraggingEnd = true
                                    }
                                },
                                onDrag = { _, dragAmount ->
                                    val w = size.width
                                    val delta = dragAmount.x / w

                                    when {
                                        isDraggingStart -> onStartPositionChange?.invoke((startPosition + delta).coerceIn(0f, endPosition - 0.01f))
                                        isDraggingEnd -> onEndPositionChange?.invoke((endPosition + delta).coerceIn(startPosition + 0.01f, 1f))
                                    }
                                },
                                onDragEnd = {
                                    isDraggingStart = false
                                    isDraggingEnd = false
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

            drawLine(
                color = baseline,
                start = Offset(0f, centerY),
                end = Offset(w, centerY),
                strokeWidth = 1f
            )

            if (samples.isEmpty() || w <= 1f) return@Canvas

            // Gesamtbreite in Pixel abhängig vom aktuellen Zoom
            val totalWidthPx = (totalDurationMs.toFloat() * zoomLevel).coerceAtLeast(w)
            val bucketCountTotal = (totalWidthPx / bucketWidthPx).toInt().coerceIn(1, MAX_BUCKETS)

            val amps = envelopeCache.getOrPut(bucketCountTotal to samples.size) {
                envelope(samples, bucketCountTotal)
            }
            if (amps.isEmpty()) return@Canvas

            // Zeit -> Bucket Mapping
            val msPerBucket = totalDurationMs.toFloat() / bucketCountTotal
            val startBucket = (startMs.toFloat() / msPerBucket).toInt().coerceIn(0, bucketCountTotal - 1)
            val endBucketIncl = ((startMs + durationMs).toFloat() / msPerBucket).toInt().coerceIn(startBucket + 1, bucketCountTotal)
            val visibleBuckets = (endBucketIncl - startBucket).coerceAtLeast(1)

            val path = Path().apply {
                moveTo(0f, centerY)
                val stepX = w / visibleBuckets

                var i = 0
                while (i < visibleBuckets) {
                    val bucketIndex = startBucket + i
                    if (bucketIndex in amps.indices) {
                        val x = i * stepX
                        val y = centerY - amps[bucketIndex] * half
                        lineTo(x, y)
                    }
                    i++
                }
                lineTo(w, centerY)

                i = visibleBuckets - 1
                while (i >= 0) {
                    val bucketIndex = startBucket + i
                    if (bucketIndex in amps.indices) {
                        val x = i * stepX
                        val y = centerY + amps[bucketIndex] * half
                        lineTo(x, y)
                    }
                    i--
                }
                close()
            }

            drawPath(
                path = path,
                color = wave.copy(alpha = 0.6f)
            )

            // Aktive Region basierend auf startPosition/endPosition
            val startX = w * startPosition
            val endX = w * endPosition
            val activeWidth = endX - startX

            // Bereiche außerhalb abdunkeln
            if (startPosition > 0f) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(startX, h)
                )
            }

            if (endPosition < 1f) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(endX, 0f),
                    size = androidx.compose.ui.geometry.Size(w - endX, h)
                )
            }

            // Fades zeichnen innerhalb der aktiven Region
            if (fadeInMs > 0f && activeWidth > 0f) {
                val signalDurationMs = (samples.size.toFloat() / signal.sampleRate) * 1000f
                val activeDurationMs = signalDurationMs * (endPosition - startPosition)
                val fadeInRatio = (fadeInMs / activeDurationMs).coerceIn(0f, 1f)
                val fadeInWidth = activeWidth * fadeInRatio

                val fadeInPath = Path().apply {
                    moveTo(startX, 0f)
                    lineTo(startX + fadeInWidth, 0f)
                    lineTo(startX, h)
                    close()
                }

                drawPath(
                    path = fadeInPath,
                    brush = SolidColor(Color.Black.copy(alpha = 0.6f))
                )
            }

            if (fadeOutMs > 0f && activeWidth > 0f) {
                val signalDurationMs = (samples.size.toFloat() / signal.sampleRate) * 1000f
                val activeDurationMs = signalDurationMs * (endPosition - startPosition)
                val fadeOutRatio = (fadeOutMs / activeDurationMs).coerceIn(0f, 1f)
                val fadeOutWidth = activeWidth * fadeOutRatio

                val fadeOutPath = Path().apply {
                    moveTo(endX - fadeOutWidth, 0f)
                    lineTo(endX, 0f)
                    lineTo(endX, h)
                    close()
                }

                drawPath(
                    path = fadeOutPath,
                    brush = SolidColor(Color.Black.copy(alpha = 0.6f))
                )
            }

            if (onStartPositionChange != null) {
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(startX, 0f),
                    end = Offset(startX, h),
                    strokeWidth = 3f
                )

                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(startX, h / 2f)
                )
            }

            if (onEndPositionChange != null) {
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(endX, 0f),
                    end = Offset(endX, h),
                    strokeWidth = 3f
                )

                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(endX, h / 2f)
                )
            }
        }
    }
}

private fun envelope(samples: FloatArray, bucketCount: Int): FloatArray {
    if (samples.isEmpty() || bucketCount <= 0) return FloatArray(0)
    val out = FloatArray(bucketCount)
    val step = ceil(samples.size.toFloat() / bucketCount).toInt().coerceAtLeast(1)

    var src = 0
    for (b in 0 until bucketCount) {
        var maxAmp = 0f
        val end = min(samples.size, src + step)
        var i = src
        while (i < end) {
            val v = abs(samples[i])
            if (v > maxAmp) maxAmp = v
            i++
        }
        out[b] = maxAmp.coerceIn(0f, 1f)
        src += step
    }
    return out
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
                8 -> {
                    val u = raw[off].toInt() and 0xFF
                    ((u - 128) / 128f).coerceIn(-1f, 1f)
                }
                16 -> {
                    val lo = raw[off].toInt() and 0xFF
                    val hi = raw[off + 1].toInt() shl 8
                    val s = (lo or hi).toShort().toInt()
                    (s / 32768f).coerceIn(-1f, 1f)
                }
                24 -> {
                    val b0 = raw[off].toInt() and 0xFF
                    val b1 = raw[off + 1].toInt() and 0xFF
                    val b2 = raw[off + 2].toInt()
                    var v = b0 or (b1 shl 8) or (b2 shl 16)
                    if ((v and 0x800000) != 0) v = v or -0x1000000
                    (v / 8388608f).coerceIn(-1f, 1f)
                }
                32 -> {
                    val b0 = raw[off].toInt() and 0xFF
                    val b1 = raw[off + 1].toInt() and 0xFF
                    val b2 = raw[off + 2].toInt() and 0xFF
                    val b3 = raw[off + 3].toInt()
                    val v = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                    if (v == Int.MIN_VALUE) -1f else (v / 2147483648f).coerceIn(-1f, 1f)
                }
                else -> {
                    val lo = raw[off].toInt() and 0xFF
                    val hi = raw[off + 1].toInt() shl 8
                    val s = (lo or hi).toShort().toInt()
                    (s / 32768f).coerceIn(-1f, 1f)
                }
            }
            sum += sample
            c++
        }
        out[frameIdx] = (sum / ch)
        frameIdx++
        byteIndex += frameSize
    }
    return out
}