package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Timer
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.DialType
import kotlinx.serialization.Serializable
import kotlin.math.floor

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
    override val bodyHeight: Dp = 128.dp
    override val bodyWidth: Dp = 128.dp

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

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AutomatableDial(
                parameterId = "frames",
                type = DialType.Steps(values = (1..120).toList()),
                value = state.frames,
                defaultValue = 0,
                title = "FPS",
                text = state.frames.toString(),
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                frames = it,
                            )
                        )
                    )
                },
            )
        }
    }
}
