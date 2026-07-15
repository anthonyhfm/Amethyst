package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.runtime.Composable
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scissors
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryPaint
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
data class TimeCutNodeState(
    val startProgress: Float = 0f,
    val endProgress: Float = 1f,
    val fadeIn: Float = 0f,
    val fadeOut: Float = 0f,
) : CompositionNodeState

object TimeCutNode : TransformNode() {
    override val type = "time-cut"
    override val label = "Time Cut"
    override val icon = Lucide.Scissors
    override val pickerCategory = CompositionNodePickerCategory.Time

    override fun defaultState() = TimeCutNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? TimeCutNodeState ?: return inputFrames
        val start = min(state.startProgress, state.endProgress).coerceIn(0f, 1f)
        val end = max(state.startProgress, state.endProgress).coerceIn(0f, 1f)

        return if (context.progress in start..end) {
            inputFrames
        } else {
            inputFrames.map { frame ->
                frame.copy(
                    strokes = frame.strokes.map { stroke ->
                        stroke.copy(
                            paint = GeometryPaint.Opacity(
                                source = stroke.paint,
                                predicate = { _, _ -> 0f },
                            )
                        )
                    }
                )
            }
        }
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? TimeCutNodeState ?: return

        NodeControls {
            LabeledRangeSlider(
                label = "Playback range",
                start = state.startProgress,
                end = state.endProgress,
                onRangeChange = { start, end ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                startProgress = start,
                                endProgress = end,
                            )
                        )
                    )
                },
            )
        }
    }
}
