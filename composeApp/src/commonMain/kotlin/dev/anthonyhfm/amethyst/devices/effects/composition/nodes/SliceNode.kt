package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableAngleControl
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scissors
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryPaint
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Serializable
data class SliceNodeState(
    val originX: Float = 0.5f,
    val originY: Float = 0.5f,
    val angleDegrees: Float = 0f,
    val width: Float = 0.2f,
    val invert: Boolean = false,
) : CompositionNodeState

object SliceNode : TransformNode() {
    override val automationParameters = listOf(
        floatAutomationParameter<SliceNodeState>("angle", "Angle", -180f, 180f, SliceNodeState::angleDegrees) { state, value -> state.copy(angleDegrees = value) },
        floatAutomationParameter<SliceNodeState>("width", "Width", 0f, 1f, SliceNodeState::width) { state, value -> state.copy(width = value) },
    )

    override val type = "slice"
    override val label = "Slice"
    override val icon = Lucide.Scissors

    override val bodyWidth = 190.dp
    override val bodyHeight = 142.dp

    override fun defaultState() = SliceNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? SliceNodeState ?: return inputFrames
        val radians = state.angleDegrees * PI.toFloat() / 180f
        val normal = Vec2(
            x = cos(radians),
            y = sin(radians),
        )
        val center = Vec2(
            x = context.bounds.first.x + state.originX * (context.bounds.second.width - 1),
            y = context.bounds.first.y + state.originY * (context.bounds.second.height - 1),
        )
        val halfWidth = state.width.coerceAtLeast(0f) * min(
            context.bounds.second.width,
            context.bounds.second.height,
        ) / 2f

        return inputFrames.map { frame ->
            frame.copy(
                strokes = frame.strokes.map { stroke ->
                    stroke.copy(
                        paint = GeometryPaint.Opacity(
                            source = stroke.paint,
                            predicate = { point, _ ->
                                var opacity = if (abs((point.x - center.x) * normal.x + (point.y - center.y) * normal.y) <= halfWidth) {
                                    1f
                                } else {
                                    0f
                                }
                                if (state.invert) {
                                    opacity = 1f - opacity
                                }
                                opacity
                            },
                        )
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
        val state = node.state as? SliceNodeState ?: return

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SliceAngleControl(
                angleDegrees = state.angleDegrees,
                onAngleChange = { angle ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                angleDegrees = angle,
                            )
                        )
                    )
                },
            )
            Dial(
                type = DialType.Continuous,
                value = state.width,
                defaultValue = 0.2f,
                title = "Width",
                text = "${(state.width * 100).roundToInt()}%",
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                width = it,
                            )
                        )
                    )
                },
            )
        }
    }
}

@Composable
private fun SliceAngleControl(
    angleDegrees: Float,
    onAngleChange: (Float) -> Unit,
) {
    val foregroundColor = Theme[colors][foreground]

    AutomatableAngleControl(
        parameterId = "angle",
        angleDegrees = angleDegrees,
        onAngleChange = onAngleChange,
        modifier = Modifier
            .size(80.dp)
            .clip(DefaultShape)
            .background(Theme[colors][secondary])
            .semantics { contentDescription = "Slice angle" },
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val radians = angleDegrees * PI / 180.0
            val direction = Offset(
                x = cos(radians).toFloat(),
                y = sin(radians).toFloat(),
            )
            val distance = min(size.width, size.height) / 2f - 10.dp.toPx()

            drawLine(
                color = foregroundColor,
                start = center - direction * distance,
                end = center + direction * distance,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = foregroundColor.copy(alpha = 0.45f),
                start = center - Offset(-direction.y, direction.x) * 14.dp.toPx(),
                end = center + Offset(-direction.y, direction.x) * 14.dp.toPx(),
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
