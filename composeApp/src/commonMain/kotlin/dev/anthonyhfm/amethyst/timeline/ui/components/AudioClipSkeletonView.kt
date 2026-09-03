package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Clean, timing-accurate neutral waveform placeholder.
 *
 * Peaks and transients align directly with the timeline's [bpm] and [zoomLevel], ensuring
 * that audio transients match grid subdivisions instead of stretching across the canvas.
 * Renders without distracting shaders or glow, matching professional DAW styling.
 */
@Composable
fun AudioClipSkeletonView(
    modifier: Modifier = Modifier,
    progress: Float = 1.0f, // 0.0f .. 1.0f (1.0f for static hover preview)
    color: Color = Color.White,
    zoomLevel: Float = 0.05f,
    bpm: Double = 120.0,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        val centerY = h / 2f
        val half = centerY * 0.88f // 12% vertical headroom matching WaveformView.kt
        val clampedProgress = progress.coerceIn(0f, 1f)
        val decodedWidth = w * clampedProgress

        // 1. Center baseline - identical to WaveformView.kt
        drawLine(
            color = color.copy(alpha = 0.45f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1f
        )

        // 2. Compute timing-accurate amplitudes based on real pixel positions (step = 2.0px)
        val stepPx = 2f
        val safeZoom = zoomLevel.coerceAtLeast(0.0001f)
        val safeBpm = bpm.coerceAtLeast(10.0)
        val beatMs = 60_000.0 / safeBpm
        val barMs = beatMs * 4.0

        // Timing-accurate procedural amplitude generator
        fun computeAmpAt(px: Float): Float {
            val timeMs = px / safeZoom
            val barPhase = ((timeMs % barMs) / barMs).coerceIn(0.0, 1.0)
            val beatPhase = ((timeMs % beatMs) / beatMs).coerceIn(0.0, 1.0)

            // Bar transient (kick/downbeat) with decay
            val barTransient = exp(-barPhase * 9.0) * 0.42
            // Quarter beat transient (snare/accent)
            val beatTransient = exp(-beatPhase * 12.0) * 0.26
            // Rich multi-frequency harmonic texture (hi-hats, tone, texture)
            val texture = abs(sin(timeMs * 0.08) * 0.14 + sin(timeMs * 0.31) * 0.10 + sin(timeMs * 1.17) * 0.06)

            return (barTransient + beatTransient + texture + 0.06).toFloat().coerceIn(0.05f, 0.95f)
        }

        // 3. Render decoded section (left of decodedWidth)
        if (decodedWidth > 1f) {
            val decodedPoints = (decodedWidth / stepPx).toInt().coerceAtLeast(2)
            val decodedPath = Path().apply {
                moveTo(0f, centerY)
                // Top contour
                for (i in 0 until decodedPoints) {
                    val x = (i * stepPx).coerceAtMost(decodedWidth)
                    val amp = computeAmpAt(x)
                    lineTo(x, centerY - amp * half)
                }
                lineTo(decodedWidth, centerY)
                // Bottom contour
                for (i in (decodedPoints - 1) downTo 0) {
                    val x = (i * stepPx).coerceAtMost(decodedWidth)
                    val amp = computeAmpAt(x)
                    lineTo(x, centerY + amp * half)
                }
                close()
            }
            // Neutral solid waveform fill, identical to WaveformView.kt
            drawPath(path = decodedPath, color = color.copy(alpha = 0.60f))
        }

        // 4. Render pending section (right of decodedWidth) if decoding in progress
        if (decodedWidth < w - 1f) {
            val pendingPoints = ((w - decodedWidth) / stepPx).toInt().coerceAtLeast(2)
            val pendingPath = Path().apply {
                moveTo(decodedWidth, centerY)
                // Top contour
                for (i in 0 until pendingPoints) {
                    val x = (decodedWidth + i * stepPx).coerceAtMost(w)
                    val amp = computeAmpAt(x)
                    lineTo(x, centerY - amp * half)
                }
                lineTo(w, centerY)
                // Bottom contour
                for (i in (pendingPoints - 1) downTo 0) {
                    val x = (decodedWidth + i * stepPx).coerceAtMost(w)
                    val amp = computeAmpAt(x)
                    lineTo(x, centerY + amp * half)
                }
                close()
            }
            // Neutral muted silhouette without any flashy glow or shaders
            drawPath(path = pendingPath, color = color.copy(alpha = 0.18f))

            // Clean 1px boundary line at progress point
            drawLine(
                color = color.copy(alpha = 0.55f),
                start = Offset(decodedWidth, centerY - half * 0.9f),
                end = Offset(decodedWidth, centerY + half * 0.9f),
                strokeWidth = 1f
            )
        }
    }
}
