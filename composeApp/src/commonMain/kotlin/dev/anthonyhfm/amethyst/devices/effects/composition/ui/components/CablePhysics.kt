package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.atan2
import kotlin.math.hypot

/** Shared visual geometry for the abstract data ports and their interaction handles. */
object DataCableGeometry {
    /** 4 dp radius plus 10 dp padding, matching the title bar's vertical port padding. */
    const val PORT_CENTER_INSET_DP = 14f
    const val PORT_RADIUS_DP = 4f
    const val END_HANDLE_DIAMETER_DP = 44f

    val DATA_COLOR = Color(0xFF54D6FF)
    val DRAG_COLOR = Color(0xFFFFD166)
    val IDLE_PORT_COLOR = Color(0xFF7C8594)
}

/** A desired cable connection in world-space dp. */
data class CableTarget(
    val id: String,
    val start: Offset,
    val end: Offset,
    val color: Color,
)

/** The physically settled cable state for the current frame, in world-space dp. */
data class CableCurve(
    val start: Offset,
    val mid: Offset,
    val end: Offset,
    val color: Color,
)

/** One ordinary quadratic cable path controlled by the simulated midpoint. */
data class DataCablePath(
    val start: Offset,
    val control: Offset,
    val end: Offset,
) {
    fun pointAt(progress: Float): Offset {
        val t = progress.coerceIn(0f, 1f)
        return quadraticPoint(start, control, end, t)
    }

    fun tangentAt(progress: Float): Offset {
        val t = progress.coerceIn(0f, 1f)
        return quadraticDerivative(start, control, end, t)
    }
}

private const val STIFFNESS = 130f
private const val DAMPING = 13f
private const val SAG_FACTOR = 0.22f
private const val MAX_SAG = 70f
private const val MIN_SAG = 4f
private const val REST_EPSILON = 0.15f
private const val VELOCITY_EPSILON = 1f

/** The original damped midpoint simulation for cable sag and follow-through. */
class CableSimulator {
    private class Body(var pos: Offset, var vel: Offset)

    private val bodies = mutableMapOf<String, Body>()

    var settled: Boolean = true
        private set

    fun step(targets: List<CableTarget>, dtSeconds: Float): List<CableCurve> {
        val dt = dtSeconds.coerceIn(0f, 1f / 30f)
        val liveIds = HashSet<String>(targets.size)
        var anyMotion = false

        val curves = targets.map { target ->
            liveIds += target.id
            val rest = restMidpoint(target.start, target.end)
            val body = bodies.getOrPut(target.id) { Body(rest, Offset.Zero) }

            if (dt > 0f) {
                val toRest = rest - body.pos
                val acceleration = toRest * STIFFNESS - body.vel * DAMPING
                body.vel += acceleration * dt
                body.pos += body.vel * dt
            }

            if (magnitude(body.vel) > VELOCITY_EPSILON ||
                magnitude(rest - body.pos) > REST_EPSILON
            ) {
                anyMotion = true
            }

            CableCurve(
                start = target.start,
                mid = body.pos,
                end = target.end,
                color = target.color,
            )
        }

        if (bodies.keys.retainAll(liveIds)) anyMotion = true
        settled = !anyMotion
        return curves
    }

    private fun restMidpoint(start: Offset, end: Offset): Offset {
        val base = (start + end) / 2f
        val distance = magnitude(end - start)
        val sag = (distance * SAG_FACTOR).coerceIn(MIN_SAG, MAX_SAG)
        return Offset(base.x, base.y + sag)
    }

    private fun magnitude(offset: Offset): Float = hypot(offset.x, offset.y)
}

/**
 * Builds the regular one-piece quadratic cable used by the original physics renderer. The control
 * is chosen so the curve passes through the simulated midpoint at t=0.5, with no smoothing layer.
 */
fun buildDataCablePath(curve: CableCurve): DataCablePath =
    DataCablePath(
        start = curve.start,
        control = Offset(
            x = 2f * curve.mid.x - (curve.start.x + curve.end.x) / 2f,
            y = 2f * curve.mid.y - (curve.start.y + curve.end.y) / 2f,
        ),
        end = curve.end,
    )

/** Draws one restrained data cable and a fixed flow chevron near its target. */
fun DrawScope.drawDataCable(
    curve: CableCurve,
    thicknessPx: Float = 2.25f,
    dpToPx: Float = thicknessPx / 2.25f,
) {
    val cablePath = buildDataCablePath(curve)
    val path = cablePath.toPath()

    drawPath(
        path = path,
        color = curve.color.darken(0.58f),
        style = Stroke(
            width = thicknessPx + dpToPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    drawPath(
        path = path,
        color = curve.color,
        style = Stroke(width = thicknessPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    drawFlowChevron(
        center = cablePath.pointAt(0.78f),
        tangent = cablePath.tangentAt(0.78f),
        color = curve.color,
        dpToPx = dpToPx,
    )
}

private fun DataCablePath.toPath(): Path = Path().apply {
    moveTo(start.x, start.y)
    quadraticTo(control.x, control.y, end.x, end.y)
}

private fun DrawScope.drawFlowChevron(
    center: Offset,
    tangent: Offset,
    color: Color,
    dpToPx: Float,
) {
    val direction = tangent.normalizedOrHorizontal()
    val angle = atan2(direction.y, direction.x)
    val length = 4.5f * dpToPx
    val halfHeight = 3f * dpToPx
    val marker = Path().apply {
        moveTo(-length / 2f, -halfHeight)
        lineTo(length / 2f, 0f)
        lineTo(-length / 2f, halfHeight)
    }

    translate(center.x, center.y) {
        rotateRad(angle, pivot = Offset.Zero) {
            drawPath(
                path = marker,
                color = color.darken(0.65f),
                style = Stroke(width = 2.8f * dpToPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = marker,
                color = color,
                style = Stroke(width = 1.35f * dpToPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private fun quadraticPoint(start: Offset, control: Offset, end: Offset, t: Float): Offset {
    val inverse = 1f - t
    return start * (inverse * inverse) + control * (2f * inverse * t) + end * (t * t)
}

private fun quadraticDerivative(start: Offset, control: Offset, end: Offset, t: Float): Offset =
    (control - start) * (2f * (1f - t)) + (end - control) * (2f * t)

private fun Offset.normalizedOrHorizontal(): Offset {
    val length = getDistance()
    return if (length > 0.001f) this / length else Offset(1f, 0f)
}

private fun Color.darken(fraction: Float): Color = Color(
    red = red * (1f - fraction),
    green = green * (1f - fraction),
    blue = blue * (1f - fraction),
    alpha = alpha,
)
