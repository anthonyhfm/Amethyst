package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Move
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import kotlinx.serialization.Serializable

@Serializable
data class MoveNodeState(
    val offsetX: Int = 0,
    val offsetY: Int = 0,
) : CompositionNodeState

object MoveNode : CompositionNodeDefinition {
    override val type = "move"
    override val label = "Move"
    override val icon = Lucide.Move

    override val hasInput = true
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Transform

    override val bodyWidth = 224.dp
    override val bodyHeight = 128.dp

    override fun defaultState() = MoveNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val s = node.state as? MoveNodeState ?: return inputFrames
        return inputFrames.map { frame ->
            frame.copy(
                strokes = frame.strokes.map { stroke ->
                    stroke.copy(
                        points = stroke.points.map { point ->
                            Vec2(
                                x = point.x + s.offsetX,
                                y = point.y + s.offsetY,
                            )
                        }
                    )
                }
            )
        }
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val s = node.state as? MoveNodeState ?: return

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dial(
                type = DialType.Steps(values = (-64..64).toList()),
                value = s.offsetX,
                defaultValue = 0,
                title = "X",
                text = s.offsetX.toString(),
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = s.copy(
                                offsetX = it,
                            )
                        )
                    )
                },
            )
            Dial(
                type = DialType.Steps(values = (-64..64).toList()),
                value = s.offsetY,
                defaultValue = 0,
                title = "Y",
                text = s.offsetY.toString(),
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = s.copy(
                                offsetY = it,
                            )
                        )
                    )
                },
            )
        }
    }
}
