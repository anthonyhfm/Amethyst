package dev.anthonyhfm.amethyst.devices.effects.composition.engine

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
data class VectorPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f
)

@Serializable
data class VectorStroke(
    val points: List<VectorPoint> = emptyList(),
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val a: Float = 1f,
    val thickness: Float = 1f
) {
    val color: Color
        get() = Color(r, g, b, a)
}

@Serializable
data class VectorTimeline(
    val frames: Map<Int, List<VectorStroke>> = emptyMap(),
    val durationFrames: Int = 100
)

// --- 2D Affine Transformations ---

fun VectorPoint.translate(dx: Float, dy: Float): VectorPoint {
    return VectorPoint(x + dx, y + dy, pressure)
}

fun VectorPoint.scale(sx: Float, sy: Float, px: Float = 0.5f, py: Float = 0.5f): VectorPoint {
    return VectorPoint(
        (x - px) * sx + px,
        (y - py) * sy + py,
        pressure
    )
}

fun VectorPoint.rotate(angleRad: Float, px: Float = 0.5f, py: Float = 0.5f): VectorPoint {
    val cos = kotlin.math.cos(angleRad)
    val sin = kotlin.math.sin(angleRad)
    val dx = x - px
    val dy = y - py
    return VectorPoint(
        dx * cos - dy * sin + px,
        dx * sin + dy * cos + py,
        pressure
    )
}

fun VectorStroke.translate(dx: Float, dy: Float): VectorStroke {
    return copy(points = points.map { it.translate(dx, dy) })
}

fun VectorStroke.scale(sx: Float, sy: Float, px: Float = 0.5f, py: Float = 0.5f): VectorStroke {
    return copy(points = points.map { it.scale(sx, sy, px, py) })
}

fun VectorStroke.rotate(angleRad: Float, px: Float = 0.5f, py: Float = 0.5f): VectorStroke {
    return copy(points = points.map { it.rotate(angleRad, px, py) })
}
