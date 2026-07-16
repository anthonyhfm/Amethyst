package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.serialization.Serializable

@Serializable
data class RotateNodeState(
    val angleDegrees: Float = 0f,
) : CompositionNodeState

object RotateNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<RotateNodeState>("angle", "Angle", 0f, 360f, RotateNodeState::angleDegrees) { state, value -> state.copy(angleDegrees = value) },
    )

    override val type = "rotate"
    override val label = "Rotate"
    override val icon = Lucide.RotateCcw
    override val hasInput = true
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Transform
    override val bodyHeight: Dp = 128.dp
    override val bodyWidth: Dp = 128.dp

    override fun defaultState(): CompositionNodeState = RotateNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? RotateNodeState ?: return inputFrames
        return inputFrames.map { frame ->
            frame.copy(
                strokes = frame.strokes.map { stroke ->
                    rotateStroke(
                        stroke = stroke,
                        angleDegrees = state.angleDegrees,
                        context = context,
                    )
                }
            )
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

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AutomatableDial(
                parameterId = "angle",
                type = DialType.Knob,
                title = "Rotation",
                text = "${state.angleDegrees.roundToInt()}°",
                value = (state.angleDegrees / 360f).coerceIn(0f, 1f),
                defaultValue = 0f,
                onValueChange = { value ->
                    val angle = (value * 360f).roundToInt().toFloat()
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                angleDegrees = angle,
                            )
                        )
                    )
                },
                onResolveTextValue = { value ->
                    value.removeSuffix("°").trim().toFloatOrNull()?.let { angle ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    angleDegrees = angle.coerceIn(0f, 360f),
                                )
                            )
                        )
                    }
                },
            )
        }
    }
}
