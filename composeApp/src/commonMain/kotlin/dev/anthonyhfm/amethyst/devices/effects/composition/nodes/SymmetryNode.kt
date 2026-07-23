package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Grid3x3
import com.composables.icons.lucide.Lucide
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableAngleControl
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.serialization.Serializable

@Serializable
data class SymmetryNodeState(
    val angleDegrees: Float = 90f,
) : CompositionNodeState

object SymmetryNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<SymmetryNodeState>("angle", "Angle", -180f, 180f, SymmetryNodeState::angleDegrees) { state, value -> state.copy(angleDegrees = value) },
    )

    override val type = "symmetry"
    override val label = "Symmetry"
    override val icon = Lucide.Grid3x3
    override val hasInput = true
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Transform
    override val bodyWidth = 128.dp
    override val bodyHeight = 128.dp

    override fun defaultState(): CompositionNodeState = SymmetryNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? SymmetryNodeState ?: return inputFrames
        val center = Vec2(
            x = context.bounds.first.x + (context.bounds.second.width - 1) / 2f,
            y = context.bounds.first.y + (context.bounds.second.height - 1) / 2f,
        )
        val radians = state.angleDegrees * kotlin.math.PI.toFloat() / 180f
        val cosDir = cos(radians)
        val sinDir = sin(radians)

        // Normal vector pointing to the source half-space
        val nx = -sinDir
        val ny = cosDir

        val cos2 = cos(2 * radians)
        val sin2 = sin(2 * radians)
        val a = cos2
        val b = sin2
        val c = sin2
        val d = -cos2

        return inputFrames.map { frame ->
            frame.copy(
                strokes = frame.strokes.flatMap { stroke ->
                    // Filter points on the source side of the symmetry line
                    val sourcePoints = stroke.points.filter { point ->
                        val dx = point.x - center.x
                        val dy = point.y - center.y
                        (dx * nx + dy * ny) >= -0.01f
                    }

                    if (sourcePoints.isEmpty()) return@flatMap emptyList()

                    val originalStroke = stroke.copy(points = sourcePoints)
                    val mirroredPoints = sourcePoints.map { point ->
                        val dx = point.x - center.x
                        val dy = point.y - center.y
                        Vec2(
                            x = center.x + dx * a + dy * b,
                            y = center.y + dx * c + dy * d,
                        )
                    }
                    val mirroredStroke = stroke.copy(points = mirroredPoints)

                    listOf(originalStroke, mirroredStroke)
                }
            )
        }
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? SymmetryNodeState ?: return
        val foreground = Theme[colors][foreground]

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AutomatableAngleControl(
                parameterId = "angle",
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
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxSize()
                    .clip(DefaultShape)
                    .background(Theme[colors][secondary])
                    .semantics { contentDescription = "Symmetry axis" },
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val angleRadians = state.angleDegrees * PI / 180.0
                    val direction = Offset(
                        x = cos(angleRadians).toFloat(),
                        y = sin(angleRadians).toFloat(),
                    )
                    val endpointDistance = min(size.width, size.height) / 2f - 10.dp.toPx()
                    val normal = Offset(x = -direction.y, y = direction.x)
                    val sourceEndpoint = center + normal * 14.dp.toPx()
                    val targetEndpoint = center - normal * 14.dp.toPx()
                    val markerRadius = 5.dp.toPx()

                    drawLine(
                        color = foreground,
                        start = center - direction * endpointDistance,
                        end = center + direction * endpointDistance,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // Source side: Solid white circle
                    drawCircle(
                        color = Color.White,
                        radius = markerRadius,
                        center = sourceEndpoint,
                    )
                    // Target side: Hollow mirrored circle
                    drawCircle(
                        color = Color.White.copy(alpha = 0.7f),
                        radius = markerRadius,
                        center = targetEndpoint,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                        ),
                    )
                }
            }
        }
    }
}
