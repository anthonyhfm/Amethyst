package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
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
import com.composables.icons.lucide.Aperture
import com.composables.icons.lucide.Lucide
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.resolveOrigin
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableWorkspaceOriginSelector
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MIN_SLICES = 2
private const val MAX_SLICES = 16
private const val DEFAULT_SLICES = 6

@Serializable
data class KaleidoscopeNodeState(
    val slices: Int = DEFAULT_SLICES,
    val angleDegrees: Float = 0f,
    val originX: Float = 0.5f,
    val originY: Float = 0.5f,
    val mirror: Boolean = true,
    override val boundToOrigin: Boolean = false,
) : CompositionNodeState, OriginBindableState

object KaleidoscopeNode : TransformNode() {
    override val automationParameters = listOf(
        floatAutomationParameter<KaleidoscopeNodeState>("origin-x", "Origin X", 0f, 1f, KaleidoscopeNodeState::originX) { state, value -> state.copy(originX = value) },
        floatAutomationParameter<KaleidoscopeNodeState>("origin-y", "Origin Y", 0f, 1f, KaleidoscopeNodeState::originY) { state, value -> state.copy(originY = value) },
        floatAutomationParameter<KaleidoscopeNodeState>("angle", "Angle", -180f, 180f, KaleidoscopeNodeState::angleDegrees) { state, value -> state.copy(angleDegrees = value) },
        intAutomationParameter<KaleidoscopeNodeState>("slices", "Slices", MIN_SLICES, MAX_SLICES, KaleidoscopeNodeState::slices) { state, value -> state.copy(slices = value) },
    )

    override val type = "kaleidoscope"
    override val label = "Kaleidoscope"
    override val icon = Lucide.Aperture
    override val pickerCategory = CompositionNodePickerCategory.Transform
    override val bodyWidth: Dp = 236.dp
    override val bodyHeight: Dp = 128.dp

    override fun defaultState(): CompositionNodeState = KaleidoscopeNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? KaleidoscopeNodeState ?: return inputFrames
        val slices = state.slices.coerceIn(MIN_SLICES, MAX_SLICES)
        val center = context.resolveOrigin(state.originX, state.originY, state.boundToOrigin)
        val angleOffset = state.angleDegrees * kotlin.math.PI.toFloat() / 180f
        val delta = 2f * kotlin.math.PI.toFloat() / slices

        return inputFrames.map { frame ->
            frame.copy(
                strokes = frame.strokes.flatMap { stroke ->
                    (0 until slices).map { k ->
                        val angleK = k * delta + angleOffset
                        if (state.mirror && k % 2 == 1) {
                            val cos2 = cos(2f * angleK)
                            val sin2 = sin(2f * angleK)
                            val mirroredPoints = stroke.points.map { point ->
                                val dx = point.x - center.x
                                val dy = point.y - center.y
                                Vec2(
                                    x = center.x + dx * cos2 + dy * sin2,
                                    y = center.y + dx * sin2 - dy * cos2,
                                )
                            }
                            stroke.copy(points = mirroredPoints)
                        } else {
                            val c = cos(angleK)
                            val s = sin(angleK)
                            val rotatedPoints = stroke.points.map { point ->
                                val dx = point.x - center.x
                                val dy = point.y - center.y
                                Vec2(
                                    x = center.x + dx * c - dy * s,
                                    y = center.y + dx * s + dy * c,
                                )
                            }
                            stroke.copy(points = rotatedPoints)
                        }
                    }
                }
            )
        }
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? KaleidoscopeNodeState ?: return
        val bounds = WorkspaceRepository.bounds.validOrFallbackBounds()

        val onOriginChange = rememberUpdatedState { position: Offset, size: IntSize ->
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

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AutomatableDial(
                    parameterId = "slices",
                    type = DialType.Steps(values = listOf(2, 3, 4, 5, 6, 8, 10, 12, 16)),
                    value = state.slices,
                    defaultValue = DEFAULT_SLICES,
                    title = "Slices",
                    text = "${state.slices}",
                    onValueChange = { value ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    slices = value.coerceIn(MIN_SLICES, MAX_SLICES),
                                )
                            )
                        )
                    },
                )
                AutomatableDial(
                    parameterId = "angle",
                    type = DialType.Continuous,
                    value = (state.angleDegrees + 180f) / 360f,
                    defaultValue = 0.5f,
                    title = "Angle",
                    text = "${state.angleDegrees.roundToInt()}°",
                    onValueChange = { value ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    angleDegrees = value * 360f - 180f,
                                )
                            )
                        )
                    },
                    onResolveTextValue = { value ->
                        value.removeSuffix("°").toFloatOrNull()?.let { angleVal ->
                            onNodeChange(
                                node.copy(
                                    state = state.copy(
                                        angleDegrees = angleVal.coerceIn(-180f, 180f),
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
