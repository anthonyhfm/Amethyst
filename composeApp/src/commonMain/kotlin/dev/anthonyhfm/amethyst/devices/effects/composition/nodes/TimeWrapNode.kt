package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.runtime.Composable
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
    override val type = "time-wrap"
    override val label = "Time Wrap"
    override val icon = Lucide.Repeat
    override val pickerCategory = CompositionNodePickerCategory.Time

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
            LabeledSlider(
                label = "Target start",
                value = state.targetStart,
                range = 0f..1f,
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                targetStart = it,
                            )
                        )
                    )
                },
            )
            LabeledSlider(
                label = "Target end",
                value = state.targetEnd,
                range = 0f..1f,
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                targetEnd = it,
                            )
                        )
                    )
                },
            )
        }
    }
}
