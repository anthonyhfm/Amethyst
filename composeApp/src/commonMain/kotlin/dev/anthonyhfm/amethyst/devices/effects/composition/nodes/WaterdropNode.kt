package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Lucide
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableWorkspaceOriginSelector
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableSlider
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.serialization.Serializable

private const val RING_TRAVEL_SPAN = 18f
private const val MIN_CURVATURE = 0.5f
private const val MAX_CURVATURE = 8f
private const val POLYLINE_STEP = 1f

@Serializable
data class WaterdropNodeState(
    val originX: Float = 0.5f,
    val originY: Float = 0.5f,
    val curvature: Float = 2f,
) : CompositionNodeState

object WaterdropNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<WaterdropNodeState>("origin-x", "Origin X", 0f, 1f, WaterdropNodeState::originX) { state, value -> state.copy(originX = value) },
        floatAutomationParameter<WaterdropNodeState>("origin-y", "Origin Y", 0f, 1f, WaterdropNodeState::originY) { state, value -> state.copy(originY = value) },
        floatAutomationParameter<WaterdropNodeState>("curvature", "Curvature", MIN_CURVATURE, MAX_CURVATURE, WaterdropNodeState::curvature) { state, value -> state.copy(curvature = value) },
    )

    override val type = "waterdrop"
    override val label = "Waterdrop"
    override val icon = Lucide.Droplet
    override val hasInput = false
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Generators
    override val bodyWidth: Dp = 160.dp
    override val bodyHeight: Dp = 208.dp

    override fun defaultState(): CompositionNodeState = WaterdropNodeState()

    override fun sourceFrames(
        node: CompositionNode,
        context: EvaluationContext,
    ): List<GeometryFrame> {
        val state = node.state as? WaterdropNodeState ?: return emptyList()
        val center = state.resolveOrigin(bounds = context.bounds)
        val radius = context.progress.coerceIn(0f, 1f) * RING_TRAVEL_SPAN
        val curvature = state.curvature.coerceIn(MIN_CURVATURE, MAX_CURVATURE)
        val circumference = max(0.01f, 2f * PI.toFloat() * max(0.5f, abs(radius)))
        val segmentCount = max(12, ceil(circumference / POLYLINE_STEP).toInt())
        val points = (0 until segmentCount).map { index ->
            superellipsePoint(
                center = center,
                radius = radius,
                curvature = curvature,
                angle = index.toFloat() / segmentCount * 2f * PI.toFloat(),
            )
        }.let { it + it.first() }

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
        val state = node.state as? WaterdropNodeState ?: return
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
            AutomatableWorkspaceOriginSelector(
                originXParameterId = "origin-x",
                originYParameterId = "origin-y",
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

            AutomatableSlider(
                parameterId = "curvature",
                label = "Curvature",
                value = state.curvature,
                range = MIN_CURVATURE..MAX_CURVATURE,
                onValueChange = { curvature ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                curvature = curvature.coerceIn(MIN_CURVATURE, MAX_CURVATURE),
                            )
                        )
                    )
                },
            )
        }
    }
}

internal fun WaterdropNodeState.resolveOrigin(bounds: Pair<IntOffset, IntSize>): Vec2 {
    val resolvedBounds = bounds.validOrFallbackBounds()
    return Vec2(
        x = resolvedBounds.first.x + originX.coerceIn(0f, 1f) * (resolvedBounds.second.width - 1).coerceAtLeast(0),
        y = resolvedBounds.first.y + originY.coerceIn(0f, 1f) * (resolvedBounds.second.height - 1).coerceAtLeast(0),
    )
}

internal fun Pair<IntOffset, IntSize>.validOrFallbackBounds(): Pair<IntOffset, IntSize> =
    takeIf { it.second.width > 0 && it.second.height > 0 } ?: (IntOffset.Zero to IntSize(10, 10))

private fun superellipsePoint(
    center: Vec2,
    radius: Float,
    curvature: Float,
    angle: Float,
): Vec2 {
    val cosine = cos(angle)
    val sine = sin(angle)
    val exponent = 2f / curvature
    return Vec2(
        x = center.x + cosine.sign * abs(cosine).pow(exponent) * radius,
        y = center.y + sine.sign * abs(sine).pow(exponent) * radius,
    )
}
