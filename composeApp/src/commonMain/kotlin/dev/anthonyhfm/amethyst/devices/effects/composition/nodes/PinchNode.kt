package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Shrink
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.PinchGraph
import dev.anthonyhfm.amethyst.devices.effects.keyframes.util.Pincher
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
data class PinchNodeState(
    val pinch: Float = 0f,
    val bilateral: Boolean = false,
) : CompositionNodeState

object PinchNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<PinchNodeState>("pinch", "Pinch", -2f, 2f, PinchNodeState::pinch) { state, value -> state.copy(pinch = value) },
        choiceAutomationParameter<PinchNodeState>("bilateral", "Bilateral", listOf("Off", "On"), { if (it.bilateral) "On" else "Off" }) { state, value -> state.copy(bilateral = value == "On") },
    )

    override val type = "pinch"
    override val label = "Pinch"
    override val icon = Lucide.Shrink
    override val hasInput = true
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Time
    override val bodyWidth = 128.dp
    override val bodyHeight = 128.dp

    override fun defaultState(): CompositionNodeState = PinchNodeState()

    override fun inputContext(node: CompositionNode, context: EvaluationContext): EvaluationContext {
        val state = node.state as? PinchNodeState ?: return context
        return context.copy(
            progress = Pincher.inverseMapFraction(
                fraction = context.progress.toDouble(),
                pinch = state.pinch,
                bilateral = state.bilateral,
            ).toFloat(),
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? PinchNodeState ?: return
        val clampedPinch = state.pinch.coerceIn(-2f, 2f)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(
                space = 4.dp,
                alignment = Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PinchGraph(
                pinch = clampedPinch,
                onPinchChange = { value ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                pinch = value.coerceIn(-2f, 2f),
                            )
                        )
                    )
                },
                bilateral = state.bilateral,
                onToggleBilateral = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                pinch = 0f,
                                bilateral = !state.bilateral,
                            )
                        )
                    )
                },
                modifier = Modifier.size(80.dp),
            )

            Text(
                text = ((clampedPinch * 100f).roundToInt() / 100f).toString(),
                style = Theme[typography][small],
                color = Theme[colors][foreground],
            )
        }
    }
}
