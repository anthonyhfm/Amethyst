package dev.anthonyhfm.amethyst.devices.effects.composition.nodes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Repeat
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import kotlin.math.floor
import kotlinx.serialization.Serializable

@Serializable
data class LoopNodeState(
    val startProgress: Float = 0f,
    val endProgress: Float = 1f,
    val repeats: Int = 2,
) : CompositionNodeState

object LoopNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<LoopNodeState>("start", "Start", 0f, 1f, LoopNodeState::startProgress) { state, value -> state.copy(startProgress = value) },
        floatAutomationParameter<LoopNodeState>("end", "End", 0f, 1f, LoopNodeState::endProgress) { state, value -> state.copy(endProgress = value) },
        intAutomationParameter<LoopNodeState>("repeats", "Repeats", 1, 16, LoopNodeState::repeats) { state, value -> state.copy(repeats = value) },
    )

    override val type = "loop"
    override val label = "Loop"
    override val icon = Lucide.Repeat

    override val hasInput = true
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Time

    override val bodyWidth = 160.dp
    override val bodyHeight = 128.dp

    override fun defaultState() = LoopNodeState()

    override fun inputContext(
        node: CompositionNode,
        context: EvaluationContext,
    ): EvaluationContext {
        val s = node.state as? LoopNodeState ?: return context
        val a = s.startProgress.coerceIn(0f, 0.99f)
        val b = s.endProgress.coerceIn(a + 0.01f, 1f)
        val p = context.progress.coerceIn(0f, 1f)
        val phase = if (p >= 1f) {
            1f
        } else {
            p * s.repeats.coerceIn(1, 16) - floor(p * s.repeats.coerceIn(1, 16))
        }
        return context.copy(
            progress = a + (b - a) * phase,
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val s = node.state as? LoopNodeState ?: return

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Dial(
                type = DialType.Steps(values = (1..16).toList()),
                value = s.repeats,
                defaultValue = 2,
                title = "Repeats",
                text = s.repeats.toString(),
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = s.copy(
                                repeats = it,
                            )
                        )
                    )
                },
            )
        }
    }
}
