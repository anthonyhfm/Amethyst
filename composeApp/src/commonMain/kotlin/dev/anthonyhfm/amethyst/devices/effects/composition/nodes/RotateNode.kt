package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import kotlin.math.cos
import kotlin.math.sin

object RotateNode : CompositionNodeDefinition {
    override val type = "rotate"
    override val label = "Rotate"
    override val hasInput = true
    override val hasOutput = true

    override fun defaultState(): CompositionNodeState = RotateNodeState()
    override fun acceptsState(state: CompositionNodeState): Boolean = state is RotateNodeState

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? RotateNodeState ?: return inputFrames
        return inputFrames.map { frame ->
            frame.copy(strokes = frame.strokes.map { rotateStroke(it, state.angleDegrees, context) })
        }
    }

    private fun rotateStroke(
        stroke: GeometryStroke,
        angleDegrees: Float,
        context: EvaluationContext,
    ): GeometryStroke {
        val center = Vec2(
            x = context.bounds.first.x + (context.bounds.second.width - 1) / 2f,
            y = context.bounds.first.y + (context.bounds.second.height - 1) / 2f,
        )
        val radians = angleDegrees * kotlin.math.PI.toFloat() / 180f
        val cos = cos(radians)
        val sin = sin(radians)

        return stroke.copy(
            points = stroke.points.map { point ->
                val dx = point.x - center.x
                val dy = point.y - center.y
                Vec2(
                    x = center.x + dx * cos - dy * sin,
                    y = center.y + dx * sin + dy * cos,
                )
            }
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? RotateNodeState ?: return

        
    }
}
