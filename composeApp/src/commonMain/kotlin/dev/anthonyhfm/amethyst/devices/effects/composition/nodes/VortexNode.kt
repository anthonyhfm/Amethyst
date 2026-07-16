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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tornado
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.WorkspaceOriginSelector
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val MaxVortexStrengthRadians = 2f
@Serializable
enum class VortexDirection {
    Clockwise,
    CounterClockwise,
}

@Serializable
data class VortexNodeState(
    val originX: Float = 0.5f,
    val originY: Float = 0.5f,
    val strength: Float = 0.5f,
    val radius: Float = 1f,
    val falloff: Float = 1f,
    val direction: VortexDirection = VortexDirection.Clockwise,
) : CompositionNodeState

object VortexNode : TransformNode() {
    override val automationParameters = listOf(
        floatAutomationParameter<VortexNodeState>("origin-x", "Origin X", 0f, 1f, VortexNodeState::originX) { state, value -> state.copy(originX = value) },
        floatAutomationParameter<VortexNodeState>("origin-y", "Origin Y", 0f, 1f, VortexNodeState::originY) { state, value -> state.copy(originY = value) },
        floatAutomationParameter<VortexNodeState>("strength", "Strength", -2f, 2f, VortexNodeState::strength) { state, value -> state.copy(strength = value) },
        floatAutomationParameter<VortexNodeState>("radius", "Radius", 0f, 1f, VortexNodeState::radius) { state, value -> state.copy(radius = value) },
        floatAutomationParameter<VortexNodeState>("falloff", "Falloff", .1f, 4f, VortexNodeState::falloff) { state, value -> state.copy(falloff = value) },
    )

    override val type = "vortex"
    override val label = "Vortex"
    override val icon = Lucide.Tornado

    override val bodyWidth = 220.dp
    override val bodyHeight = 254.dp

    override fun defaultState() = VortexNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? VortexNodeState ?: return inputFrames
        if (state.radius <= 0f || state.strength == 0f) {
            return inputFrames
        }

        val width = context.bounds.second.width.coerceAtLeast(1)
        val height = context.bounds.second.height.coerceAtLeast(1)
        val center = Vec2(
            x = context.bounds.first.x + state.originX.coerceIn(0f, 1f) * (width - 1),
            y = context.bounds.first.y + state.originY.coerceIn(0f, 1f) * (height - 1),
        )
        val radius = state.radius.coerceIn(0f, 1f) * min(width, height) / 2f
        if (radius <= 0f) {
            return inputFrames
        }

        val direction = if (state.direction == VortexDirection.Clockwise) 1f else -1f
        val strength = state.strength.coerceIn(-MaxVortexStrengthRadians, MaxVortexStrengthRadians)

        return inputFrames.map { frame ->
            frame.copy(
                strokes = frame.strokes.map { stroke ->
                    stroke.copy(
                        points = stroke.points.map { point ->
                            val dx = point.x - center.x
                            val dy = point.y - center.y
                            val distance = sqrt(dx * dx + dy * dy)

                            if (distance >= radius) {
                                point
                            } else {
                                val angle = direction * strength * (1f - distance / radius).coerceIn(0f, 1f)
                                    .pow(state.falloff.coerceAtLeast(0.01f))
                                Vec2(
                                    x = center.x + dx * cos(angle) - dy * sin(angle),
                                    y = center.y + dx * sin(angle) + dy * cos(angle),
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
        val state = node.state as? VortexNodeState ?: return
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
                            )
                        )
                    )
                },
                modifier = Modifier.weight(
                    weight = 1f,
                    fill = true,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Dial(
                    type = DialType.Continuous,
                    value = (state.strength + MaxVortexStrengthRadians) / (2f * MaxVortexStrengthRadians),
                    defaultValue = 0.625f,
                    title = "Strength",
                    text = "${(state.strength * 10).roundToInt() / 10f}",
                    onValueChange = {
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    strength = it * 2f * MaxVortexStrengthRadians - MaxVortexStrengthRadians,
                                )
                            )
                        )
                    },
                )
                Dial(
                    type = DialType.Continuous,
                    value = state.radius,
                    defaultValue = 1f,
                    title = "Radius",
                    text = "${(state.radius * 100).roundToInt()}%",
                    onValueChange = {
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    radius = it,
                                )
                            )
                        )
                    },
                )
                Dial(
                    type = DialType.Continuous,
                    value = (state.falloff - 0.1f) / 3.9f,
                    defaultValue = (1f - 0.1f) / 3.9f,
                    title = "Falloff",
                    text = "${(state.falloff * 10).roundToInt() / 10f}",
                    onValueChange = {
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    falloff = 0.1f + it * 3.9f,
                                )
                            )
                        )
                    },
                )
            }
        }
    }
}
