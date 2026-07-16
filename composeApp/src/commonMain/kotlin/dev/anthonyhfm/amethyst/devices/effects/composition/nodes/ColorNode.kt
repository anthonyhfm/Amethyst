package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryPaint
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.components.ColorPicker
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.HexColorEditor
import dev.anthonyhfm.amethyst.ui.components.HuePickerBar
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.rememberColorPickerState
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class ColorNodeState(val red: Float = 1f, val green: Float = 1f, val blue: Float = 1f, val alpha: Float = 1f) :
    CompositionNodeState

object ColorNode : TransformNode() {
    override val automationParameters = listOf(
        floatAutomationParameter<ColorNodeState>("alpha", "Alpha", 0f, 1f, ColorNodeState::alpha) { state, value -> state.copy(alpha = value) },
    )

    override val type = "color";
    override val label = "Color";
    override val icon = Lucide.Palette
    override val pickerCategory = CompositionNodePickerCategory.Color

    override val bodyWidth = 220.dp;
    override val bodyHeight = 248.dp

    override fun defaultState() = ColorNodeState()

    override fun transformFrames(
        node: CompositionNode, context: EvaluationContext, inputFrames: List<GeometryFrame>
    ): List<GeometryFrame> {
        val state = node.state as? ColorNodeState ?: return inputFrames

        val color = Color(
            state.red.coerceIn(0f, 1f),
            state.green.coerceIn(0f, 1f),
            state.blue.coerceIn(0f, 1f),
            state.alpha.coerceIn(0f, 1f)
        )

        return inputFrames.map { frame ->
            frame.copy(strokes = frame.strokes.map {
                it.copy(
                    color = color, paint = GeometryPaint.Solid(color)
                )
            })
        }
    }

    @Composable
    override fun NodeBody(node: CompositionNode, onNodeChange: (CompositionNode) -> Unit) {
        val state = node.state as? ColorNodeState ?: return
        val picker = rememberColorPickerState(Color(state.red, state.green, state.blue))

        LaunchedEffect(state.red, state.green, state.blue) {
            if (picker.color != Color(
                    state.red, state.green, state.blue
                )
            ) picker.setColor(Color(state.red, state.green, state.blue))
        }

        fun commit(color: Color) =
            onNodeChange(node.copy(state = state.copy(red = color.red, green = color.green, blue = color.blue)))

        LaunchedEffect(picker.color) {
            if (picker.color != Color(state.red, state.green, state.blue)) {
                commit(picker.color)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),

            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorPicker(
                    modifier = Modifier.weight(1f),
                    state = picker,
                    onSelectionStart = { },
                    onSelectionFinish = ::commit
                )

                HuePickerBar(
                    vertical = true,
                    state = picker,
                    onSelectionStart = { },
                    onSelectionFinish = ::commit
                )
            }

            HexColorEditor(state = picker)

            AutomatableDial(
                parameterId = "alpha",
                type = DialType.Continuous,
                value = state.alpha,
                defaultValue = 1f,
                title = "Opacity",
                text = "${(state.alpha * 100).roundToInt()}%",
                onValueChange = {
                    onNodeChange(node.copy(state = state.copy(alpha = it)))
                },
                onResolveTextValue = {
                    it.removeSuffix("%").trim().toFloatOrNull()?.let { value ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    alpha = (value / 100f).coerceIn(0f, 1f)
                                )
                            )
                        )
                    }
                }
            )
        }
    }
}
