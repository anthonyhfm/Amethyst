package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.dot
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object ScannerNode : CompositionNodeDefinition {
    override val type = "scanner"
    override val label = "Scanner"
    override val hasInput = false
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Generators

    private const val SCAN_TRAVEL_PADDING = 0.5f
    private const val SCAN_POSITION_TIE_BREAK = 0.000001f
    private const val POLYLINE_STEP = 1f

    override fun defaultState(): CompositionNodeState = ScannerNodeState()
    override fun acceptsState(state: CompositionNodeState): Boolean = state is ScannerNodeState

    override fun sourceFrames(node: CompositionNode, context: EvaluationContext): List<GeometryFrame> {
        val state = node.state as? ScannerNodeState ?: return emptyList()
        val stroke = buildScannerStroke(state, context) ?: return emptyList()
        return listOf(GeometryFrame(timeMs = 0.0, strokes = listOf(stroke)))
    }

    private fun buildScannerStroke(
        state: ScannerNodeState,
        context: EvaluationContext,
    ): GeometryStroke? {
        val radians = state.angleDegrees * kotlin.math.PI.toFloat() / 180f
        val axis = Vec2(cos(radians), sin(radians))
        val perp = Vec2(-axis.y, axis.x)
        val minX = context.bounds.first.x.toFloat()
        val minY = context.bounds.first.y.toFloat()
        val maxX = (context.bounds.first.x + context.bounds.second.width - 1).toFloat()
        val maxY = (context.bounds.first.y + context.bounds.second.height - 1).toFloat()
        val corners = listOf(
            Vec2(minX, minY),
            Vec2(minX, maxY),
            Vec2(maxX, minY),
            Vec2(maxX, maxY),
        )

        var minAxis = Float.POSITIVE_INFINITY
        var maxAxis = Float.NEGATIVE_INFINITY
        var minPerp = Float.POSITIVE_INFINITY
        var maxPerp = Float.NEGATIVE_INFINITY
        corners.forEach { corner ->
            val axisProjection = corner.dot(axis)
            val perpProjection = corner.dot(perp)
            minAxis = min(minAxis, axisProjection)
            maxAxis = max(maxAxis, axisProjection)
            minPerp = min(minPerp, perpProjection)
            maxPerp = max(maxPerp, perpProjection)
        }

        val scanStart = minAxis - SCAN_TRAVEL_PADDING
        val scanEnd = maxAxis + SCAN_TRAVEL_PADDING
        val travelRange = scanEnd - scanStart
        if (travelRange <= 0f) return null

        val scanPos = scanStart + context.progress.coerceIn(0f, 1f) * travelRange + SCAN_POSITION_TIE_BREAK
        val span = maxPerp - minPerp
        val count = max(2, ceil(span / max(POLYLINE_STEP, 0.01f)).toInt())
        val points = (0 until count).map { index ->
            val s = minPerp + (index.toFloat() / (count - 1).toFloat()) * span
            Vec2(
                x = axis.x * scanPos + perp.x * s,
                y = axis.y * scanPos + perp.y * s,
            )
        }

        return GeometryStroke(
            points = points,
            color = Color(
                red = state.red.coerceIn(0f, 1f),
                green = state.green.coerceIn(0f, 1f),
                blue = state.blue.coerceIn(0f, 1f),
            ),
            thickness = state.thickness.coerceIn(0.1f, 2f),
            origin = context.outputOrigin,
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? ScannerNodeState ?: return

    }
}
