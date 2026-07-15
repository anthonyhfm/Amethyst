package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryPaint
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.components.DialType
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class ColorShiftNodeState(
    val hueDegrees: Float = 0f,
    val saturationDelta: Float = 0f,
    val lightnessDelta: Float = 0f,
) : CompositionNodeState

object ColorShiftNode : TransformNode() {
    override val type = "color-shift"
    override val label = "Color Shift"
    override val icon = Lucide.Palette
    override val pickerCategory = CompositionNodePickerCategory.Color

    override val bodyWidth = 220.dp
    override val bodyHeight = 116.dp

    override fun defaultState() = ColorShiftNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> = inputFrames.map { frame ->
        val state = node.state as? ColorShiftNodeState ?: return inputFrames

        frame.copy(
            strokes = frame.strokes.map { stroke ->
                stroke.copy(
                    paint = GeometryPaint.ColorShift(
                        source = stroke.paint,
                        hueDegrees = state.hueDegrees,
                        saturationDelta = state.saturationDelta,
                        lightnessDelta = state.lightnessDelta,
                    )
                )
            }
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? ColorShiftNodeState ?: return

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Dial(
                type = DialType.Continuous,
                value = (state.hueDegrees + 180f) / 360f,
                defaultValue = 0.5f,
                title = "Hue",
                text = "${state.hueDegrees.roundToInt()}°",
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                hueDegrees = it * 360f - 180f,
                            )
                        )
                    )
                },
            )
            Dial(
                type = DialType.Continuous,
                value = (state.saturationDelta + 1f) / 2f,
                defaultValue = 0.5f,
                title = "Saturation",
                text = "${(state.saturationDelta * 100).roundToInt()}%",
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                saturationDelta = it * 2f - 1f,
                            )
                        )
                    )
                },
            )
            Dial(
                type = DialType.Continuous,
                value = (state.lightnessDelta + 1f) / 2f,
                defaultValue = 0.5f,
                title = "Lightness",
                text = "${(state.lightnessDelta * 100).roundToInt()}%",
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                lightnessDelta = it * 2f - 1f,
                            )
                        )
                    )
                },
            )
        }
    }
}
