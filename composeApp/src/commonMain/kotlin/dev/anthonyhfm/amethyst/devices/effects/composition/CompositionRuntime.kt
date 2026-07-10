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
