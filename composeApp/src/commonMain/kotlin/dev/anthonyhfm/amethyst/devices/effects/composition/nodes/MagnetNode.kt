package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Magnet
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.resolveOrigin
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableWorkspaceOriginSelector
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

private const val MIN_FORCE = -2f
private const val MAX_FORCE = 2f
private const val DEFAULT_FORCE = 0.5f

@Serializable
data class MagnetNodeState(
    val originX: Float = 0.5f,
    val originY: Float = 0.5f,
    val force: Float = DEFAULT_FORCE,
    override val boundToOrigin: Boolean = false,
) : CompositionNodeState, OriginBindableState

object MagnetNode : TransformNode() {
    override val automationParameters = listOf(
        floatAutomationParameter<MagnetNodeState>("origin-x", "Origin X", 0f, 1f, MagnetNodeState::originX) { state, value -> state.copy(originX = value) },
        floatAutomationParameter<MagnetNodeState>("origin-y", "Origin Y", 0f, 1f, MagnetNodeState::originY) { state, value -> state.copy(originY = value) },
        floatAutomationParameter<MagnetNodeState>("force", "Force", MIN_FORCE, MAX_FORCE, MagnetNodeState::force) { state, value -> state.copy(force = value) },
    )

    override val type = "magnet"
    override val label = "Magnet"
    override val icon = Lucide.Magnet
    override val pickerCategory = CompositionNodePickerCategory.Effects
    override val bodyWidth: Dp = 216.dp
    override val bodyHeight: Dp = 128.dp

    override fun defaultState(): CompositionNodeState = MagnetNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? MagnetNodeState ?: return inputFrames
        val force = state.force.coerceIn(MIN_FORCE, MAX_FORCE)
        if (force == 0f) return inputFrames

        val center = context.resolveOrigin(state.originX, state.originY, state.boundToOrigin)
        val maxRadius = max(context.bounds.second.width, context.bounds.second.height).toFloat().coerceAtLeast(1f)

        return inputFrames.map { frame ->
            frame.copy(
                strokes = frame.strokes.map { stroke ->
                    stroke.copy(
                        points = stroke.points.map { point ->
                            val dx = point.x - center.x
                            val dy = point.y - center.y
                            val dist = sqrt(dx * dx + dy * dy)
                            if (dist <= 0.0001f) {
                                point
                            } else {
                                val falloff = (1f - (dist / maxRadius)).coerceIn(0f, 1f)
                                val pull = falloff * force
                                Vec2(
                                    x = point.x - dx * pull,
                                    y = point.y - dy * pull,
                                )
                            }
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
        val state = node.state as? MagnetNodeState ?: return
        val bounds = WorkspaceRepository.bounds.validOrFallbackBounds()
        val onOriginChange = rememberUpdatedState { position: Offset, size: IntSize ->
            if (size.width <= 0 || size.height <= 0) return@rememberUpdatedState
            onNodeChange(
                node.copy(
                    state = state.copy(
                        originX = (position.x / size.width).coerceIn(0f, 1f),
                        originY = (position.y / size.height).coerceIn(0f, 1f),
                    )
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            AutomatableWorkspaceOriginSelector(
                originXParameterId = "origin-x",
                originYParameterId = "origin-y",
                originX = state.originX,
                originY = state.originY,
                bounds = bounds,
                onOriginChange = { position, size ->
                    onOriginChange.value(position, size)
                },
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .padding(start = 12.dp)
                    .fillMaxHeight()
                    .aspectRatio(1f),
                boundToOrigin = state.boundToOrigin,
                onBoundToOriginChange = { onNodeChange(node.copy(state = state.copy(boundToOrigin = it))) },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                AutomatableDial(
                    parameterId = "force",
                    type = DialType.Continuous,
                    value = (state.force - MIN_FORCE) / (MAX_FORCE - MIN_FORCE),
                    defaultValue = (DEFAULT_FORCE - MIN_FORCE) / (MAX_FORCE - MIN_FORCE),
                    title = "Force",
                    text = "${(state.force * 10).roundToInt() / 10f}",
                    onValueChange = { value ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    force = (MIN_FORCE + value * (MAX_FORCE - MIN_FORCE)).coerceIn(MIN_FORCE, MAX_FORCE),
                                )
                            )
                        )
                    },
                    onResolveTextValue = { value ->
                        value.toFloatOrNull()?.let { forceVal ->
                            onNodeChange(
                                node.copy(
                                    state = state.copy(
                                        force = forceVal.coerceIn(MIN_FORCE, MAX_FORCE),
                                    )
                                )
                            )
                        }
                    },
                )
            }
        }
    }
}
