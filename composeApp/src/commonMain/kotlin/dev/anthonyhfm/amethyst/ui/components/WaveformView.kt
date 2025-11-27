package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun WaveformView(
    signal: Signal.AudioSignal,
    modifier: Modifier = Modifier,
    waveColor: Color = Color.White,
    onSeek: ((Float) -> Unit)? = null,
    zoomLevel: Float? = null,
    fadeInMs: Float = 0f,
    fadeOutMs: Float = 0f,
) {
    val wave = waveColor
    val baseline = waveColor.copy(alpha = 0.6f)

    val bucketWidthPx = 2f
    val MAX_BUCKETS = 3000

    val samples: FloatArray = remember(signal.rawData, signal.bitDepth, signal.channels) {
        val bytes = signal.rawData ?: return@remember FloatArray(0)
        pcmToMonoFloats(bytes, signal.bitDepth, signal.channels)
    }

    // Cache für amplitude envelopes nach BucketCount
    val envelopeCache = remember { mutableMapOf<Int, FloatArray>() }

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

            // Berechne Bucket-Anzahl (abhängig von Breite und optional Zoom)
            val rawBucketCount = (w / bucketWidthPx).toInt().coerceAtLeast(1)
            val zoomFactor = zoomLevel?.coerceAtLeast(0.0001f) ?: 1f
            val adjustedBuckets = (rawBucketCount * zoomFactor).toInt().coerceAtLeast(1)
            val bucketCount = adjustedBuckets.coerceAtMost(MAX_BUCKETS)

            val amps = envelopeCache.getOrPut(bucketCount) {
                envelope(samples, bucketCount)
            }

            if (amps.size != bucketCount) return@Canvas

            val path = Path().apply {
                moveTo(0f, centerY)
                val stepX = w / (bucketCount - 1).coerceAtLeast(1)

                for (i in 0 until bucketCount) {
                    val x = i * stepX
                    val y = centerY - amps[i] * half
                    lineTo(x, y)
                }
                lineTo(w, centerY)

                for (i in bucketCount - 1 downTo 0) {
                    val x = i * stepX
                    val y = centerY + amps[i] * half
                    lineTo(x, y)
                }
                close()
            }

            drawPath(
                path = path,
                color = wave.copy(alpha = 0.6f)
            )

            // Draw fade in overlay
            if (fadeInMs > 0f) {
                val durationMs = (samples.size.toFloat() / signal.sampleRate) * 1000f
                val fadeInRatio = (fadeInMs / durationMs).coerceIn(0f, 1f)
                val fadeInWidth = w * fadeInRatio
                
                drawRect(
                    color = Color.Black.copy(alpha = 0.3f),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(fadeInWidth, h)
                )
            }

            // Draw fade out overlay
            if (fadeOutMs > 0f) {
                val durationMs = (samples.size.toFloat() / signal.sampleRate) * 1000f
                val fadeOutRatio = (fadeOutMs / durationMs).coerceIn(0f, 1f)
                val fadeOutWidth = w * fadeOutRatio
                
                drawRect(
                    color = Color.Black.copy(alpha = 0.3f),
                    topLeft = Offset(w - fadeOutWidth, 0f),
                    size = androidx.compose.ui.geometry.Size(fadeOutWidth, h)
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