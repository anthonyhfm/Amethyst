package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientSmoothness

object GradientInterpolator {
    fun interpolate(stops: List<NoteGradientStop>, t: Float): Triple<Float, Float, Float> {
        if (stops.isEmpty()) return Triple(0f, 0f, 0f)

        val clampedT = t.coerceIn(0f, 1f)
        val sorted = stops.sortedBy { it.position }

        if (sorted.size == 1) return Triple(sorted[0].r, sorted[0].g, sorted[0].b)

        // Find segment: largest i where sorted[i].position <= clampedT
        var segmentIndex = 0
        for (i in 0 until sorted.size - 1) {
            if (clampedT >= sorted[i].position && clampedT <= sorted[i + 1].position) {
                segmentIndex = i
                break
            }
        }

        val startStop = sorted[segmentIndex]
        val endStop = sorted.getOrNull(segmentIndex + 1) ?: sorted.last()

        val segmentStart = startStop.position.toDouble()
        val segmentEnd = endStop.position.toDouble()
        val segmentDuration = segmentEnd - segmentStart

        val linearT = if (segmentDuration > 0.0001) {
            ((clampedT - segmentStart) / segmentDuration).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val easedT = when (startStop.smoothness) {
            GradientSmoothness.Linear -> linearT
            GradientSmoothness.Hold -> {
                if (linearT < 0.95) 0.0 else 1.0
            }
            GradientSmoothness.Release -> {
                if (linearT > 0.05) 1.0 else 0.0
            }
            GradientSmoothness.Fast -> kotlin.math.sqrt(linearT)
            GradientSmoothness.Slow -> 1.0 - kotlin.math.sqrt(1.0 - linearT)
            GradientSmoothness.Sharp -> {
                if (linearT < 0.5) {
                    0.5 - kotlin.math.sqrt(0.5 - linearT) / kotlin.math.sqrt(2.0)
                } else {
                    0.5 + kotlin.math.sqrt(linearT - 0.5) / kotlin.math.sqrt(2.0)
                }
            }
            GradientSmoothness.Smooth -> {
                if (linearT < 0.5) {
                    kotlin.math.sqrt(linearT / 2.0)
                } else {
                    1.0 - kotlin.math.sqrt((1.0 - linearT) / 2.0)
                }
            }
        }

        val r = (startStop.r + (endStop.r - startStop.r) * easedT.toFloat()).coerceIn(0f, 1f)
        val g = (startStop.g + (endStop.g - startStop.g) * easedT.toFloat()).coerceIn(0f, 1f)
        val b = (startStop.b + (endStop.b - startStop.b) * easedT.toFloat()).coerceIn(0f, 1f)

        return Triple(r, g, b)
    }
}
