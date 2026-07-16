package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.composables.icons.lucide.Orbit
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.WorkspaceOriginSelector
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.serialization.Serializable

private const val SPIRAL_TRAVEL_SPAN = 18f
private const val SPIRAL_STRIDE = 4.5f
private const val MIN_TURNS = 0.25f
private const val MAX_TURNS = 8f
private const val POLYLINE_STEP = 1f

@Serializable
data class SpiralNodeState(
    val originX: Float = 0.5f,
    val originY: Float = 0.5f,
    val turns: Float = 2f,
) : CompositionNodeState

object SpiralNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<SpiralNodeState>("origin-x", "Origin X", 0f, 1f, SpiralNodeState::originX) { state, value -> state.copy(originX = value) },
        floatAutomationParameter<SpiralNodeState>("origin-y", "Origin Y", 0f, 1f, SpiralNodeState::originY) { state, value -> state.copy(originY = value) },
        floatAutomationParameter<SpiralNodeState>("turns", "Turns", MIN_TURNS, MAX_TURNS, SpiralNodeState::turns) { state, value -> state.copy(turns = value) },
    )

    override val type = "spiral"
    override val label = "Spiral"
    override val icon = Lucide.Orbit
    override val hasInput = false
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Generators
    override val bodyWidth: Dp = 160.dp
    override val bodyHeight: Dp = 208.dp

    override fun defaultState(): CompositionNodeState = SpiralNodeState()

    override fun sourceFrames(
        node: CompositionNode,
        context: EvaluationContext,
    ): List<GeometryFrame> {
        val state = node.state as? SpiralNodeState ?: return emptyList()
        val center = state.resolveOrigin(bounds = context.bounds)
        val targetDistance = context.progress.coerceIn(0f, 1f) * SPIRAL_TRAVEL_SPAN
        val turns = state.turns.coerceIn(MIN_TURNS, MAX_TURNS)
        val angleSteps = max(
            24,
            ceil((2f * PI.toFloat() * max(0.5f, targetDistance)) / POLYLINE_STEP).toInt(),
        )
        val points = buildList {
            for (index in 0 until angleSteps) {
                val angleFraction = index.toFloat() / angleSteps
                val radius = targetDistance - angleFraction * turns * SPIRAL_STRIDE
                if (radius < 0f) {
                    if (size == 1) {
                        add(center)
                    }
                    break
                }
                val angle = angleFraction * 2f * PI.toFloat()
                add(
                    Vec2(
                        x = center.x + cos(angle) * radius,
                        y = center.y + sin(angle) * radius,
                    )
                )
            }
        }
        if (points.size < 2) return emptyList()

        return listOf(
            GeometryFrame(
                timeMs = 0.0,
                strokes = listOf(
                    GeometryStroke(
                        points = points,
                        color = Color.White,
                        thickness = 1f,
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
        val state = node.state as? SpiralNodeState ?: return
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WorkspaceOriginSelector(
                originX = state.originX,
                originY = state.originY,
                bounds = bounds,
                onOriginChange = { position, size ->
                    onOriginChange.value(position, size)
                },
                modifier = Modifier.weight(
                    weight = 1f,
                    fill = true,
                ),
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            LabeledSlider(
                label = "Turns",
                value = state.turns,
                range = MIN_TURNS..MAX_TURNS,
                onValueChange = { turns ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                turns = turns.coerceIn(MIN_TURNS, MAX_TURNS),
                            )
                        )
                    )
                },
            )
        }
    }
}

private fun SpiralNodeState.resolveOrigin(bounds: Pair<IntOffset, IntSize>): Vec2 {
    val resolvedBounds = bounds.validOrFallbackBounds()
    return Vec2(
        x = resolvedBounds.first.x + originX.coerceIn(0f, 1f) * (resolvedBounds.second.width - 1).coerceAtLeast(0),
        y = resolvedBounds.first.y + originY.coerceIn(0f, 1f) * (resolvedBounds.second.height - 1).coerceAtLeast(0),
    )
}
