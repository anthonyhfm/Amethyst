package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Focus
import com.composables.icons.lucide.Lucide
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryPaint
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.distanceSquared
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.WorkspaceOriginSelector
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Serializable
data class CutoutNodeState(
    val originX: Float = 0.5f,
    val originY: Float = 0.5f,
    val radius: Float = 0.25f,
    val feather: Float = 0f,
) : CompositionNodeState

/** Removes the circular area from the image while preserving the outside. */
object CutoutNode : TransformNode() {
    override val type = "cutout"
    override val label = "Cutout"
    override val icon = Lucide.Focus

    override val bodyWidth = 220.dp
    override val bodyHeight = 254.dp

    override fun defaultState() = CutoutNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? CutoutNodeState ?: return inputFrames
        val center = Vec2(
            x = context.bounds.first.x + state.originX.coerceIn(0f, 1f) * (context.bounds.second.width - 1),
            y = context.bounds.first.y + state.originY.coerceIn(0f, 1f) * (context.bounds.second.height - 1),
        )
        val radius = state.radius.coerceIn(0f, 1f) * min(
            (context.bounds.second.width - 1).coerceAtLeast(0),
            (context.bounds.second.height - 1).coerceAtLeast(0),
        ) / 2f
        val feather = state.feather.coerceIn(0f, 1f) * radius

        return inputFrames.map { frame ->
            frame.copy(
                strokes = frame.strokes.map { stroke ->
                    stroke.copy(
                        paint = GeometryPaint.Opacity(
                            source = stroke.paint,
                            predicate = { point, _ ->
                                val distance = sqrt(point.distanceSquared(center))
                                when {
                                    distance <= radius - feather -> 0f
                                    distance >= radius || feather <= 0f -> 1f
                                    else -> {
                                        val progress = ((distance - (radius - feather)) / feather).coerceIn(0f, 1f)
                                        progress * progress * (3 - 2 * progress)
                                    }
                                }
                            },
                        ),
                    )
                },
            )
        }
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? CutoutNodeState ?: return
        val bounds = WorkspaceRepository.bounds.validOrFallbackBounds()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WorkspaceOriginSelector(
                originX = state.originX,
                originY = state.originY,
                bounds = bounds,
                onOriginChange = { position, size ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                originX = (position.x / size.width).coerceIn(0f, 1f),
                                originY = (position.y / size.height).coerceIn(0f, 1f),
                            ),
                        ),
                    )
                },
                modifier = Modifier.weight(1f, fill = true),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Dial(
                    type = DialType.Continuous,
                    value = state.radius,
                    defaultValue = 0.25f,
                    title = "Radius",
                    text = "${(state.radius * 100).roundToInt()}%",
                    onValueChange = { onNodeChange(node.copy(state = state.copy(radius = it))) },
                )
                Dial(
                    type = DialType.Continuous,
                    value = state.feather,
                    defaultValue = 0f,
                    title = "Feather",
                    text = "${(state.feather * 100).roundToInt()}%",
                    onValueChange = { onNodeChange(node.copy(state = state.copy(feather = it))) },
                )
            }
        }
    }
}
