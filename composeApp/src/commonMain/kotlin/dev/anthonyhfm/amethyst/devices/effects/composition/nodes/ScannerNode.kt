package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableAngleControl
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scan
import com.composeunstyled.Icon
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.dot
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.serialization.Serializable

@Serializable
data class ScannerNodeState(
    val angleDegrees: Float = 0f,
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 1f,
    val thickness: Float = 1f,
) : CompositionNodeState

object ScannerNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<ScannerNodeState>("angle", "Angle", -180f, 180f, ScannerNodeState::angleDegrees) { state, value -> state.copy(angleDegrees = value) },
        floatAutomationParameter<ScannerNodeState>("thickness", "Thickness", 0f, 4f, ScannerNodeState::thickness) { state, value -> state.copy(thickness = value) },
    )

    override val type = "scanner"
    override val label = "Scanner"
    override val icon = Lucide.Scan
    override val hasInput = false
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Generators
    override val bodyWidth = 216.dp
    override val bodyHeight = 128.dp

    private const val SCAN_TRAVEL_PADDING = 0.5f
    private const val SCAN_POSITION_TIE_BREAK = 0.000001f
    private const val POLYLINE_STEP = 1f

    override fun defaultState(): CompositionNodeState = ScannerNodeState()

    override fun sourceFrames(node: CompositionNode, context: EvaluationContext): List<GeometryFrame> {
        val state = node.state as? ScannerNodeState ?: return emptyList()
        val stroke = buildScannerStroke(state, context) ?: return emptyList()
        return listOf(GeometryFrame(timeMs = 0.0, strokes = listOf(stroke)))
    }

    private fun buildScannerStroke(
        state: ScannerNodeState,
        context: EvaluationContext,
    ): GeometryStroke? {
        val radians = state.angleDegrees * kotlin.math.PI.toFloat() / 180f
        val axis = Vec2(cos(radians), sin(radians))
        val perp = Vec2(-axis.y, axis.x)
        val minX = context.bounds.first.x.toFloat()
        val minY = context.bounds.first.y.toFloat()
        val maxX = (context.bounds.first.x + context.bounds.second.width - 1).toFloat()
        val maxY = (context.bounds.first.y + context.bounds.second.height - 1).toFloat()
        val corners = listOf(
            Vec2(minX, minY),
            Vec2(minX, maxY),
            Vec2(maxX, minY),
            Vec2(maxX, maxY),
        )

        var minAxis = Float.POSITIVE_INFINITY
        var maxAxis = Float.NEGATIVE_INFINITY
        var minPerp = Float.POSITIVE_INFINITY
        var maxPerp = Float.NEGATIVE_INFINITY
        corners.forEach { corner ->
            val axisProjection = corner.dot(axis)
            val perpProjection = corner.dot(perp)
            minAxis = min(minAxis, axisProjection)
            maxAxis = max(maxAxis, axisProjection)
            minPerp = min(minPerp, perpProjection)
            maxPerp = max(maxPerp, perpProjection)
        }

        val scanStart = minAxis - SCAN_TRAVEL_PADDING
        val scanEnd = maxAxis + SCAN_TRAVEL_PADDING
        val travelRange = scanEnd - scanStart
        if (travelRange <= 0f) return null

        val scanPos = scanStart + context.progress.coerceIn(0f, 1f) * travelRange + SCAN_POSITION_TIE_BREAK
        val span = maxPerp - minPerp
        val count = max(2, ceil(span / max(POLYLINE_STEP, 0.01f)).toInt())
        val points = (0 until count).map { index ->
            val s = minPerp + (index.toFloat() / (count - 1).toFloat()) * span
            Vec2(
                x = axis.x * scanPos + perp.x * s,
                y = axis.y * scanPos + perp.y * s,
            )
        }

        return GeometryStroke(
            points = points,
            color = Color.White,
            thickness = state.thickness.coerceAtLeast(0f),
            origin = context.outputOrigin,
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? ScannerNodeState ?: return

        Row(
            modifier = Modifier
                .fillMaxSize(),
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
                    .padding(vertical = 12.dp)
                    .padding(start = 12.dp)
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(DefaultShape)
                    .background(Theme[colors][secondary]),
            ) {
                Icon(
                    imageVector = Lucide.ArrowUp,
                    contentDescription = "Scanner direction",
                    tint = Theme[colors][foreground],
                    modifier = Modifier
                        .size(32.dp)
                        .rotate(state.angleDegrees + 90f),
                )

                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val angleRadians = state.angleDegrees * PI / 180.0
                    val distance = min(size.width, size.height) / 2f - 8.dp.toPx()
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = center + Offset(
                            x = cos(angleRadians).toFloat() * distance,
                            y = sin(angleRadians).toFloat() * distance,
                        ),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),

                contentAlignment = Alignment.Center,
            ) {
                AutomatableDial(
                    parameterId = "thickness",
                    type = DialType.Continuous,
                    value = (state.thickness / 4f).coerceIn(0f, 1f),
                    defaultValue = 0.25f,
                    title = "Thickness",
                    text = "${(state.thickness * 10).roundToInt() / 10f}",
                    onValueChange = { value ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    thickness = (value * 4f).coerceIn(0f, 4f),
                                )
                            )
                        )
                    },
                    onResolveTextValue = { value ->
                        value.toFloatOrNull()?.let { thickness ->
                            onNodeChange(
                                node.copy(
                                    state = state.copy(
                                        thickness = thickness.coerceIn(0f, 4f),
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
