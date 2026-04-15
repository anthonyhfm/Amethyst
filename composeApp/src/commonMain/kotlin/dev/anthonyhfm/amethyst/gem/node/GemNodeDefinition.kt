package dev.anthonyhfm.amethyst.gem.node

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.gem.GemNodeDescriptor
import dev.anthonyhfm.amethyst.gem.GemNodeInstance

/**
 * A self-contained definition of a Gem node type: its static [descriptor] (shape, pins, state
 * fields), its optional inline Compose [Content] for in-canvas editing, and the [execute] function
 * that produces output values from resolved inputs.
 *
 * Create one subclass per node type and register it in [GemNodeDefinitionRegistry].
 */
abstract class GemNodeDefinition {
    abstract val descriptor: GemNodeDescriptor

    /**
     * Optional Compose UI rendered inside the node body on the canvas.
     * Only override when the node needs custom inline controls (e.g. a value editor).
     */
    @Composable
    open fun Content(
        node: GemNodeInstance,
        onNodeChange: (GemNodeInstance) -> Unit,
        modifier: Modifier = Modifier
    ) = Unit

    /**
     * Produces outputs for this node given the resolved [context].
     * Implementations should write to [GemNodeExecutionContext.outputs] and may append
     * diagnostics or host outputs via the context helpers.
     */
    abstract fun execute(context: GemNodeExecutionContext)
}
