package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableSlider
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Timer
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.roundToInt

@Serializable
data class FrameLimitNodeState(
    val frames: Int = 12,
) : CompositionNodeState

object FrameLimitNode : TransformNode() {
    override val automationParameters = listOf(
        intAutomationParameter<FrameLimitNodeState>("frames", "Frames per cycle", 1, 120, FrameLimitNodeState::frames) { state, value -> state.copy(frames = value) },
    )

    override val type = "frame-limit"
    override val label = "Frame Limit"
    override val icon = Lucide.Timer
    override val pickerCategory = CompositionNodePickerCategory.Time

    override fun defaultState() = FrameLimitNodeState()

    override fun inputContext(
        node: CompositionNode,
        context: EvaluationContext,
    ): EvaluationContext {
        val frames = (node.state as? FrameLimitNodeState)?.frames?.coerceIn(1, 120) ?: 12
        return context.copy(
            progress = if (context.progress >= 1f) {
                1f
            } else {
                floor(context.progress * frames) / frames
            }
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? FrameLimitNodeState ?: return

        NodeControls {
            AutomatableSlider(
                parameterId = "frames",
                label = "Frames per cycle",
                value = state.frames.toFloat(),
                range = 1f..120f,
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                frames = it.roundToInt(),
                            )
                        )
                    )
                },
            )
        }
    }
}
