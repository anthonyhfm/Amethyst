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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Radar
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.resolveOrigin
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableWorkspaceOriginSelector
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.serialization.Serializable

private const val MIN_THICKNESS = 0f
private const val MAX_THICKNESS = 4f
private const val DEFAULT_THICKNESS = 1f
private const val POLYLINE_STEP = 1f

@Serializable
data class RadarNodeState(
    val originX: Float = 0.5f,
    val originY: Float = 0.5f,
    val thickness: Float = DEFAULT_THICKNESS,
    override val boundToOrigin: Boolean = false,
) : CompositionNodeState, OriginBindableState

object RadarNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<RadarNodeState>("origin-x", "Origin X", 0f, 1f, RadarNodeState::originX) { state, value -> state.copy(originX = value) },
        floatAutomationParameter<RadarNodeState>("origin-y", "Origin Y", 0f, 1f, RadarNodeState::originY) { state, value -> state.copy(originY = value) },
        floatAutomationParameter<RadarNodeState>("thickness", "Thickness", MIN_THICKNESS, MAX_THICKNESS, RadarNodeState::thickness) { state, value -> state.copy(thickness = value) },
    )

    override val type = "radar"
    override val label = "Radar"
    override val icon = Lucide.Radar
    override val hasInput = false
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Generators
    override val bodyWidth: Dp = 216.dp
    override val bodyHeight: Dp = 128.dp

    override fun defaultState(): CompositionNodeState = RadarNodeState()

    override fun sourceFrames(
        node: CompositionNode,
        context: EvaluationContext,
    ): List<GeometryFrame> {
        val state = node.state as? RadarNodeState ?: return emptyList()
        val center = context.resolveOrigin(state.originX, state.originY, state.boundToOrigin)
        val beamLength = calculateRadarBeamLength(center, context.bounds)
        val angle = -PI.toFloat() / 2f + context.progress.coerceIn(0f, 1f) * 2f * PI.toFloat()
        val thickness = state.thickness.coerceIn(MIN_THICKNESS, MAX_THICKNESS)

        val steps = max(2, ceil(beamLength / POLYLINE_STEP).toInt())
        val dirX = cos(angle)
        val dirY = sin(angle)
        val points = (0..steps).map { index ->
            val dist = (index.toFloat() / steps) * beamLength
            Vec2(
                x = center.x + dirX * dist,
                y = center.y + dirY * dist,
            )
        }

        return listOf(
            GeometryFrame(
                timeMs = 0.0,
                strokes = listOf(
                    GeometryStroke(
                        points = points,
                        color = Color.White,
                        thickness = thickness,
                        origin = context.outputOrigin,
                    )
                ),
            )
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? RadarNodeState ?: return
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
                    parameterId = "thickness",
                    type = DialType.Continuous,
                    value = (state.thickness - MIN_THICKNESS) / (MAX_THICKNESS - MIN_THICKNESS),
                    defaultValue = (DEFAULT_THICKNESS - MIN_THICKNESS) / (MAX_THICKNESS - MIN_THICKNESS),
                    title = "Thickness",
                    text = "${(state.thickness * 10).roundToInt() / 10f}",
                    onValueChange = { value ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    thickness = (MIN_THICKNESS + value * (MAX_THICKNESS - MIN_THICKNESS)).coerceIn(MIN_THICKNESS, MAX_THICKNESS),
                                )
                            )
                        )
                    },
                    onResolveTextValue = { value ->
                        value.toFloatOrNull()?.let { thickness ->
                            onNodeChange(
                                node.copy(
                                    state = state.copy(
                                        thickness = thickness.coerceIn(MIN_THICKNESS, MAX_THICKNESS),
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

internal fun calculateRadarBeamLength(
    center: Vec2,
    bounds: Pair<IntOffset, IntSize>,
): Float {
    val minX = bounds.first.x.toFloat()
    val minY = bounds.first.y.toFloat()
    val maxX = minX + (bounds.second.width - 1).coerceAtLeast(0).toFloat()
    val maxY = minY + (bounds.second.height - 1).coerceAtLeast(0).toFloat()
    val maxDx = max(kotlin.math.abs(center.x - minX), kotlin.math.abs(center.x - maxX))
    val maxDy = max(kotlin.math.abs(center.y - minY), kotlin.math.abs(center.y - maxY))
    val diagonalSpan = kotlin.math.sqrt(maxDx * maxDx + maxDy * maxDy)
    return diagonalSpan + 1f
}
