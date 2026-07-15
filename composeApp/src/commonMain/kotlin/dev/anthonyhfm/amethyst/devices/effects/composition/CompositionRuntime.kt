package dev.anthonyhfm.amethyst.devices.effects.composition

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

data class EvaluationContext(
    val bounds: Pair<IntOffset, IntSize>,
    val outputOrigin: Any?,
    val progress: Float,
)

data class Vec2(val x: Float, val y: Float)

data class GeometryStroke(
    val points: List<Vec2>,
    val color: Color,
    val thickness: Float,
    val origin: Any?,
    /** Kept alongside [color] for source compatibility; new nodes should transform this paint. */
    val paint: GeometryPaint = GeometryPaint.Solid(color),
)

data class GeometryFrame(
    val timeMs: Double,
    val strokes: List<GeometryStroke>,
)

fun Vec2.dot(other: Vec2): Float = x * other.x + y * other.y

fun Vec2.distanceSquared(other: Vec2): Float {
    val dx = x - other.x
    val dy = y - other.y
    return dx * dx + dy * dy
}

/** A paint is evaluated only after geometry has been rasterized to a workspace LED. */
sealed interface GeometryPaint {
    fun colorAt(point: Vec2, bounds: Pair<IntOffset, IntSize>): Color
    fun opacityAt(point: Vec2, bounds: Pair<IntOffset, IntSize>): Float = 1f

    data class Solid(val color: Color) : GeometryPaint {
        override fun colorAt(point: Vec2, bounds: Pair<IntOffset, IntSize>) = color
    }

    data class LinearGradient(
        val stops: List<PaintStop>,
        val angleDegrees: Float,
        /** Width of the paint field relative to the workspace's shorter side. */
        val length: Float = 1f,
        val reverse: Boolean = false,
        val steps: Int? = null,
    ) : GeometryPaint {
        override fun colorAt(point: Vec2, bounds: Pair<IntOffset, IntSize>): Color {
            val radians = angleDegrees * kotlin.math.PI.toFloat() / 180f
            val dx = kotlin.math.cos(radians)
            val dy = kotlin.math.sin(radians)
            val centerX = bounds.first.x + (bounds.second.width - 1).coerceAtLeast(0) / 2f
            val centerY = bounds.first.y + (bounds.second.height - 1).coerceAtLeast(0) / 2f
            val fieldWidth = minOf(bounds.second.width, bounds.second.height).coerceAtLeast(1) * length.coerceIn(.01f, 1f)
            var t = (((point.x - centerX) * dx + (point.y - centerY) * dy) / fieldWidth + .5f).coerceIn(0f, 1f)
            if (reverse) t = 1f - t
            steps?.coerceIn(2, 16)?.let { count -> t = kotlin.math.floor(t * (count - 1)).toFloat() / (count - 1) }
            return interpolatePaintStops(stops, t)
        }
    }

    data class ColorShift(val source: GeometryPaint, val hueDegrees: Float, val saturationDelta: Float, val lightnessDelta: Float) : GeometryPaint {
        override fun colorAt(point: Vec2, bounds: Pair<IntOffset, IntSize>): Color =
            shiftHsl(source.colorAt(point, bounds), hueDegrees, saturationDelta, lightnessDelta)
        override fun opacityAt(point: Vec2, bounds: Pair<IntOffset, IntSize>) = source.opacityAt(point, bounds)
    }

    data class Opacity(val source: GeometryPaint, val predicate: (Vec2, Pair<IntOffset, IntSize>) -> Float) : GeometryPaint {
        override fun colorAt(point: Vec2, bounds: Pair<IntOffset, IntSize>) = source.colorAt(point, bounds)
        override fun opacityAt(point: Vec2, bounds: Pair<IntOffset, IntSize>) =
            (source.opacityAt(point, bounds) * predicate(point, bounds)).coerceIn(0f, 1f)
    }
}

data class PaintStop(val position: Float, val color: Color, val smoothness: String = "Linear")

fun interpolatePaintStops(rawStops: List<PaintStop>, t: Float): Color {
    val stops = rawStops.sortedBy { it.position.coerceIn(0f, 1f) }
    if (stops.isEmpty()) return Color.Transparent
    if (stops.size == 1 || t <= stops.first().position) return stops.first().color
    if (t >= stops.last().position) return stops.last().color
    val index = stops.indexOfLast { it.position <= t }.coerceIn(0, stops.lastIndex - 1)
    val a = stops[index]
    val b = stops[index + 1]
    val linear = ((t - a.position) / (b.position - a.position).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
    val eased = when (a.smoothness) {
        "Smooth" -> if (linear < .5f) kotlin.math.sqrt(linear / 2f) else 1f - kotlin.math.sqrt((1f - linear) / 2f)
        "Sharp" -> if (linear < .5f) .5f - kotlin.math.sqrt(.5f - linear) / kotlin.math.sqrt(2f) else .5f + kotlin.math.sqrt(linear - .5f) / kotlin.math.sqrt(2f)
        "Fast" -> kotlin.math.sqrt(linear)
        "Slow" -> 1f - kotlin.math.sqrt(1f - linear)
        "Hold" -> if (linear < .95f) 0f else 1f
        "Release" -> if (linear > .05f) 1f else 0f
        else -> linear
    }
    return Color(
        red = a.color.red + (b.color.red - a.color.red) * eased,
        green = a.color.green + (b.color.green - a.color.green) * eased,
        blue = a.color.blue + (b.color.blue - a.color.blue) * eased,
        alpha = a.color.alpha + (b.color.alpha - a.color.alpha) * eased,
    )
}

private fun shiftHsl(color: Color, hueDegrees: Float, saturationDelta: Float, lightnessDelta: Float): Color {
    val max = maxOf(color.red, color.green, color.blue); val min = minOf(color.red, color.green, color.blue)
    val delta = max - min; var hue = 0f
    if (delta > .00001f) hue = when (max) { color.red -> 60f * (((color.green - color.blue) / delta) % 6f); color.green -> 60f * ((color.blue - color.red) / delta + 2f); else -> 60f * ((color.red - color.green) / delta + 4f) }
    hue = (hue + hueDegrees + 360f) % 360f
    val lightness = ((max + min) / 2f + lightnessDelta).coerceIn(0f, 1f)
    val saturation = if (delta < .00001f) 0f else (delta / (1f - kotlin.math.abs(2f * ((max + min) / 2f) - 1f))).let { (it + saturationDelta).coerceIn(0f, 1f) }
    val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation; val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f)); val m = lightness - c / 2f
    val (r, g, b) = when (hue) { in 0f..<60f -> Triple(c,x,0f); in 60f..<120f -> Triple(x,c,0f); in 120f..<180f -> Triple(0f,c,x); in 180f..<240f -> Triple(0f,x,c); in 240f..<300f -> Triple(x,0f,c); else -> Triple(c,0f,x) }
    return Color((r + m).coerceIn(0f,1f), (g + m).coerceIn(0f,1f), (b + m).coerceIn(0f,1f), color.alpha)
}
