package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PenLine
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import kotlinx.serialization.Serializable

@Serializable
data class LineNodeState(
    val startX: Float = 0.25f,
    val startY: Float = 0.5f,
    val endX: Float = 0.75f,
    val endY: Float = 0.5f,
    val thickness: Float = 1f,
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 1f,
) : CompositionNodeState

object LineNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<LineNodeState>("start-x", "Start X", 0f, 1f, LineNodeState::startX) { state, value -> state.copy(startX = value) },
        floatAutomationParameter<LineNodeState>("start-y", "Start Y", 0f, 1f, LineNodeState::startY) { state, value -> state.copy(startY = value) },
        floatAutomationParameter<LineNodeState>("end-x", "End X", 0f, 1f, LineNodeState::endX) { state, value -> state.copy(endX = value) },
        floatAutomationParameter<LineNodeState>("end-y", "End Y", 0f, 1f, LineNodeState::endY) { state, value -> state.copy(endY = value) },
        floatAutomationParameter<LineNodeState>("thickness", "Thickness", 0f, 4f, LineNodeState::thickness) { state, value -> state.copy(thickness = value) },
    )

    override val type = "line"
    override val label = "Line"
    override val icon = Lucide.PenLine

    override val hasInput = false
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Generators

    override fun defaultState() = LineNodeState()

    override fun sourceFrames(
        node: CompositionNode,
        context: EvaluationContext,
    ): List<GeometryFrame> {
        val state = node.state as? LineNodeState ?: return emptyList()
        val bounds = context.bounds

        fun x(value: Float) =
            bounds.first.x + value.coerceIn(0f, 1f) * (bounds.second.width - 1).coerceAtLeast(0)

        fun y(value: Float) =
            bounds.first.y + value.coerceIn(0f, 1f) * (bounds.second.height - 1).coerceAtLeast(0)

        val color = Color(
            red = state.red.coerceIn(0f, 1f),
            green = state.green.coerceIn(0f, 1f),
            blue = state.blue.coerceIn(0f, 1f),
        )

        return listOf(
            GeometryFrame(
                timeMs = 0.0,
                strokes = listOf(
                    GeometryStroke(
                        points = listOf(
                            Vec2(
                                x = x(state.startX),
                                y = y(state.startY),
                            ),
                            Vec2(
                                x = x(state.endX),
                                y = y(state.endY),
                            ),
                        ),
                        color = color,
                        thickness = state.thickness.coerceAtLeast(0f),
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
        val state = node.state as? LineNodeState ?: return

        NodeControls {
            LabeledSlider(
                label = "Start X",
                value = state.startX,
                range = 0f..1f,
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                startX = it,
                            )
                        )
                    )
                },
            )
            LabeledSlider(
                label = "Start Y",
                value = state.startY,
                range = 0f..1f,
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                startY = it,
                            )
                        )
                    )
                },
            )
            LabeledSlider(
                label = "End X",
                value = state.endX,
                range = 0f..1f,
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                endX = it,
                            )
                        )
                    )
                },
            )
            LabeledSlider(
                label = "Thickness",
                value = state.thickness,
                range = 0f..4f,
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                thickness = it,
                            )
                        )
                    )
                },
            )
        }
    }
}
