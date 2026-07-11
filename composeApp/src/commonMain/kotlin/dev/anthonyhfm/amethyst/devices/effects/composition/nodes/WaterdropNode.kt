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
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
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

private const val RING_TRAVEL_SPAN = 18f
private const val MIN_CURVATURE = 0.5f
private const val MAX_CURVATURE = 8f
private const val POLYLINE_STEP = 1f

object WaterdropNode : CompositionNodeDefinition {
    override val type = "waterdrop"
    override val label = "Waterdrop"
    override val hasInput = false
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Generators
    override val bodyWidth: Dp = 160.dp
    override val bodyHeight: Dp = 208.dp

    override fun defaultState(): CompositionNodeState = WaterdropNodeState()
    override fun acceptsState(state: CompositionNodeState): Boolean = state is WaterdropNodeState

    override fun sourceFrames(node: CompositionNode, context: EvaluationContext): List<GeometryFrame> {
        val state = node.state as? WaterdropNodeState ?: return emptyList()
        val center = state.resolveOrigin(context.bounds)
        val radius = context.progress.coerceIn(0f, 1f) * RING_TRAVEL_SPAN
        val curvature = state.curvature.coerceIn(MIN_CURVATURE, MAX_CURVATURE)
        val circumference = max(0.01f, 2f * PI.toFloat() * max(0.5f, abs(radius)))
        val segmentCount = max(12, ceil(circumference / POLYLINE_STEP).toInt())
        val points = (0 until segmentCount).map { index ->
            superellipsePoint(center, radius, curvature, index.toFloat() / segmentCount * 2f * PI.toFloat())
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
                    ),
                ),
            ),
        )
    }

    @Composable
    override fun NodeBody(node: CompositionNode, onNodeChange: (CompositionNode) -> Unit) {
        val state = node.state as? WaterdropNodeState ?: return
        val bounds = WorkspaceRepository.bounds.validOrFallbackBounds()
        val onOriginChange = rememberUpdatedState { position: Offset, size: IntSize ->
            if (size.width <= 0 || size.height <= 0) return@rememberUpdatedState
            onNodeChange(
                node.copy(
                    state = state.copy(
                        originX = (position.x / size.width).coerceIn(0f, 1f),
                        originY = (position.y / size.height).coerceIn(0f, 1f),
                    ),
                ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WorkspaceOriginPicker(
                originX = state.originX,
                originY = state.originY,
                bounds = bounds,
                onOriginChange = { position, size -> onOriginChange.value(position, size) },
                modifier = Modifier.weight(1f, fill = true),
            )

            Spacer(Modifier.height(12.dp))

            LabeledSlider(
                label = "Curvature",
                value = state.curvature,
                range = MIN_CURVATURE..MAX_CURVATURE,
                onValueChange = { curvature ->
                    onNodeChange(node.copy(state = state.copy(curvature = curvature.coerceIn(MIN_CURVATURE, MAX_CURVATURE))))
                },
            )
        }
    }
}

@Composable
internal fun WorkspaceOriginPicker(
    originX: Float,
    originY: Float,
    bounds: Pair<IntOffset, IntSize>,
    onOriginChange: (Offset, IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val aspectRatio = bounds.second.width.toFloat() / bounds.second.height.coerceAtLeast(1).toFloat()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val widthPx = min(maxWidthPx, maxHeightPx * aspectRatio)
        val heightPx = widthPx / aspectRatio
        val pickerWidth = with(density) { widthPx.toDp() }
        val pickerHeight = with(density) { heightPx.toDp() }

        Box(
            modifier = Modifier
                .width(pickerWidth)
                .height(pickerHeight)
                .clip(DefaultShape)
                .background(Theme[colors][secondary])
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectTapGestures { position -> onOriginChange(position, size) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position -> onOriginChange(position, size) },
                        onDrag = { change, _ ->
                            change.consume()
                            onOriginChange(change.position, size)
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(
                        x = originX.coerceIn(0f, 1f) * size.width,
                        y = originY.coerceIn(0f, 1f) * size.height,
                    ),
                )
            }
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

private fun superellipsePoint(center: Vec2, radius: Float, curvature: Float, angle: Float): Vec2 {
    val cosine = cos(angle)
    val sine = sin(angle)
    val exponent = 2f / curvature
    return Vec2(
        x = center.x + cosine.sign * abs(cosine).pow(exponent) * radius,
        y = center.y + sine.sign * abs(sine).pow(exponent) * radius,
    )
}
