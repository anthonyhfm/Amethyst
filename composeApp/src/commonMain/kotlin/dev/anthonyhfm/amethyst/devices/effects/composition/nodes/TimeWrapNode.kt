package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableRangeSlider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Repeat
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
data class TimeWrapNodeState(
    val targetStart: Float = 0f,
    val targetEnd: Float = 1f,
    val mode: String = "stretch",
) : CompositionNodeState

object TimeWrapNode : TransformNode() {
    override val automationParameters = listOf(
        floatAutomationParameter<TimeWrapNodeState>("target-start", "Target start", 0f, 1f, TimeWrapNodeState::targetStart) { state, value -> state.copy(targetStart = value) },
        floatAutomationParameter<TimeWrapNodeState>("target-end", "Target end", 0f, 1f, TimeWrapNodeState::targetEnd) { state, value -> state.copy(targetEnd = value) },
    )

    override val type = "time-wrap"
    override val label = "Time Wrap"
    override val icon = Lucide.Repeat
    override val pickerCategory = CompositionNodePickerCategory.Time

    override val bodyWidth: Dp = 208.dp
    override val bodyHeight: Dp = 72.dp

    override fun defaultState() = TimeWrapNodeState()

    override fun inputContext(
        node: CompositionNode,
        context: EvaluationContext,
    ): EvaluationContext {
        val state = node.state as? TimeWrapNodeState ?: return context
        val start = min(state.targetStart, state.targetEnd)
        val end = max(state.targetStart, state.targetEnd)

        return if (context.progress !in start..end || end - start < 0.0001f) {
            context.copy(
                progress = -1f,
            )
        } else {
            context.copy(
                progress = ((context.progress - start) / (end - start)).coerceIn(0f, 1f),
            )
        }
    }

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? TimeWrapNodeState ?: return inputFrames
        val start = min(state.targetStart, state.targetEnd)
        val end = max(state.targetStart, state.targetEnd)

        return if (context.progress in start..end) {
            inputFrames
        } else {
            emptyList()
        }
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? TimeWrapNodeState ?: return

        NodeControls {
            AutomatableRangeSlider(
                startParameterId = "target-start",
                endParameterId = "target-end",
                label = "Target range",
                start = state.targetStart,
                end = state.targetEnd,
                onRangeChange = { start, end ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                targetStart = start,
                                targetEnd = end,
                            )
                        )
                    )
                },
            )
        }
    }
}
