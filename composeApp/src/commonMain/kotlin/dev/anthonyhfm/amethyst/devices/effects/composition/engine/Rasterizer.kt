package dev.anthonyhfm.amethyst.devices.effects.composition.engine

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal

object Rasterizer {

    /**
     * Calculates the shortest distance from point [c] to the line segment [a]-[b].
     */
    fun distanceToSegment(c: VectorPoint, a: VectorPoint, b: VectorPoint): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val acx = c.x - a.x
        val acy = c.y - a.y

        val abLenSq = abx * abx + aby * aby
        if (abLenSq < 1e-6f) {
            // A and B are virtually the same point
            return kotlin.math.sqrt(acx * acx + acy * acy)
        }

        // Project C onto AB
        val t = (acx * abx + acy * aby) / abLenSq
        val tClamped = t.coerceIn(0f, 1f)

        val closestX = a.x + tClamped * abx
        val closestY = a.y + tClamped * aby

        val dx = c.x - closestX
        val dy = c.y - closestY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Maps the list of continuous float-based polylines onto discrete launchpad grid dimensions.
     * Returns a list of active [Signal.LED] objects representing the lit LEDs.
     *
     * @param strokes The list of vector strokes to rasterize.
     * @param width The grid width (typically 10).
     * @param height The grid height (typically 10).
     * @param origin The origin reference to set for the generated LED signals.
     */
    fun rasterize(
        strokes: List<VectorStroke>,
        width: Int = 10,
        height: Int = 10,
        origin: Any? = null
    ): List<Signal.LED> {
        val leds = mutableListOf<Signal.LED>()

        for (gx in 0 until width) {
            for (gy in 0 until height) {
                // Determine center coordinate of the cell in [0, 1] range
                val cx = (gx + 0.5f) / width
                val cy = (gy + 0.5f) / height
                val cellPoint = VectorPoint(cx, cy)

                var blendedR = 0f
                var blendedG = 0f
                var blendedB = 0f
                var blendedA = 0f

                for (stroke in strokes) {
                    if (stroke.points.isEmpty()) continue

                    // Find shortest distance from cell to this stroke
                    var minDist = Float.MAX_VALUE

                    if (stroke.points.size == 1) {
                        val p = stroke.points[0]
                        val dx = cellPoint.x - p.x
                        val dy = cellPoint.y - p.y
                        minDist = kotlin.math.sqrt(dx * dx + dy * dy)
                    } else {
                        for (i in 0 until stroke.points.size - 1) {
                            val dist = distanceToSegment(cellPoint, stroke.points[i], stroke.points[i + 1])
                            if (dist < minDist) {
                                minDist = dist
                            }
                        }
                    }

                    val thickness = stroke.thickness.coerceAtLeast(0.0001f)
                    if (minDist <= thickness) {
                        // Intensity falls off linearly with distance
                        val intensity = (1f - minDist / thickness).coerceIn(0f, 1f)
                        
                        // Additive blending of color channels
                        blendedR = (blendedR + stroke.r * intensity).coerceIn(0f, 1f)
                        blendedG = (blendedG + stroke.g * intensity).coerceIn(0f, 1f)
                        blendedB = (blendedB + stroke.b * intensity).coerceIn(0f, 1f)
                        blendedA = (blendedA + stroke.a * intensity).coerceIn(0f, 1f)
                    }
                }

                // If the resulting pixel is visible, add it
                if (blendedA > 0.005f && (blendedR > 0.005f || blendedG > 0.005f || blendedB > 0.005f)) {
                    leds.add(
                        Signal.LED(
                            origin = origin,
                            x = gx,
                            y = gy,
                            color = Color(blendedR, blendedG, blendedB, blendedA)
                        )
                    )
                }
            }
        }

        return leds
    }
}
