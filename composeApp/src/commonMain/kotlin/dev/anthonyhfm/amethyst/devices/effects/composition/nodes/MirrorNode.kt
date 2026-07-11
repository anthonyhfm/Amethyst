package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object MirrorNode : CompositionNodeDefinition {
    override val type = "mirror"
    override val label = "Mirror"
    override val hasInput = true
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Transform
    override val bodyWidth = 128.dp
    override val bodyHeight = 128.dp

    override fun defaultState(): CompositionNodeState = MirrorNodeState()
    override fun acceptsState(state: CompositionNodeState): Boolean = state is MirrorNodeState

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? MirrorNodeState ?: return inputFrames
        return inputFrames.map { frame ->
            frame.copy(strokes = frame.strokes.map { mirrorStroke(it, state.angleDegrees, context) })
        }
    }

    private fun mirrorStroke(
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

        val a = 2 * cos * cos - 1
        val b = 2 * cos * sin
        val c = b
        val d = 2 * sin * sin - 1

        return stroke.copy(
            points = stroke.points.map { point ->
                val dx = point.x - center.x
                val dy = point.y - center.y
                Vec2(
                    x = center.x + dx * a + dy * b,
                    y = center.y + dx * c + dy * d,
                )
            }
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? MirrorNodeState ?: return
        val foreground = Theme[colors][foreground]
        val onAngleChange = rememberUpdatedState { position: Offset, size: androidx.compose.ui.unit.IntSize ->
            if (size.width == 0 || size.height == 0) return@rememberUpdatedState

            val angle = (atan2(
                position.y - size.height / 2f,
                position.x - size.width / 2f,
            ) * 180.0 / PI).toFloat()
            onNodeChange(node.copy(state = state.copy(angleDegrees = angle)))
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(DefaultShape)
                    .background(Theme[colors][secondary])
                    .semantics { contentDescription = "Mirror axis" }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(Unit) {
                        detectTapGestures { position -> onAngleChange.value(position, size) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { position -> onAngleChange.value(position, size) },
                            onDrag = { change, _ ->
                                change.consume()
                                onAngleChange.value(change.position, size)
                            },
                        )
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val angleRadians = state.angleDegrees * PI / 180.0
                    val direction = Offset(
                        x = cos(angleRadians).toFloat(),
                        y = sin(angleRadians).toFloat(),
                    )
                    val endpointDistance = min(size.width, size.height) / 2f - 10.dp.toPx()
                    val normal = Offset(x = -direction.y, y = direction.x)
                    val filledEndpoint = center + normal * 14.dp.toPx()
                    val hollowEndpoint = center - normal * 14.dp.toPx()
                    val markerRadius = 5.dp.toPx()

                    drawLine(
                        color = foreground,
                        start = center - direction * endpointDistance,
                        end = center + direction * endpointDistance,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawCircle(color = Color.White, radius = markerRadius, center = filledEndpoint)
                    drawCircle(
                        color = Color.White,
                        radius = markerRadius,
                        center = hollowEndpoint,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
    }
}
