package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rewind
import com.composeunstyled.Icon
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground

object ReverseNode : CompositionNodeDefinition {
    override val type = "reverse"
    override val label = "Reverse"
    override val hasInput = true
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Time
    override val bodyHeight: Dp = 100.dp
    override val bodyWidth: Dp = 100.dp

    override fun defaultState(): CompositionNodeState = ReverseNodeState
    override fun acceptsState(state: CompositionNodeState): Boolean = state is ReverseNodeState

    override fun inputContext(node: CompositionNode, context: EvaluationContext): EvaluationContext =
        context.copy(progress = 1f - context.progress)

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.Rewind,
                contentDescription = null,
                tint = Theme[colors][foreground].copy(0.5f),
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
