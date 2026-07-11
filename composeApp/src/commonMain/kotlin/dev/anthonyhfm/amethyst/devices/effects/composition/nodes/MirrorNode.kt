package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import kotlin.math.cos
import kotlin.math.sin

object MirrorNode : CompositionNodeDefinition {
    override val type = "mirror"
    override val label = "Mirror"
    override val hasInput = true
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Transform

    override fun defaultState(): CompositionNodeState = MirrorNodeState()
    override fun acceptsState(state: CompositionNodeState): Boolean = state is MirrorNodeState

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? MirrorNodeState ?: return inputFrames
        return inputFrames.map { frame ->
            frame.copy(strokes = frame.strokes.map { mirrorStroke(it, state.angleDegrees, context) })
        }
    }

    private fun mirrorStroke(
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

        val a = 2 * cos * cos - 1
        val b = 2 * cos * sin
        val c = b
        val d = 2 * sin * sin - 1

        return stroke.copy(
            points = stroke.points.map { point ->
                val dx = point.x - center.x
                val dy = point.y - center.y
                Vec2(
                    x = center.x + dx * a + dy * b,
                    y = center.y + dx * c + dy * d,
                )
            }
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? MirrorNodeState ?: return
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            LabeledSlider(
                label = "Angle ${state.angleDegrees.toInt()}°",
                value = state.angleDegrees,
                range = 0f..360f,
                onValueChange = { onNodeChange(node.copy(state = state.copy(angleDegrees = it))) },
            )
        }
    }
}
