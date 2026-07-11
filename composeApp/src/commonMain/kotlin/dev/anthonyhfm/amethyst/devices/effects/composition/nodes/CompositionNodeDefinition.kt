package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame

interface CompositionNodeDefinition {
    val type: String
    val label: String
    val hasInput: Boolean
    val hasOutput: Boolean
    val isOutput: Boolean get() = false

    /**
     * The dimensions reserved for the node body. The engine renders the title bar and ports;
     * everything below is fully owned by [NodeBody].
     *
     * These defaults keep existing nodes visually unchanged while allowing each node type to
     * opt into the space its controls need.
     */
    val bodyWidth: Dp get() = 188.dp
    val bodyHeight: Dp get() = 96.dp

    fun defaultState(): CompositionNodeState
    fun acceptsState(state: CompositionNodeState): Boolean
    fun sourceFrames(node: CompositionNode, context: EvaluationContext): List<GeometryFrame> = emptyList()
    fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> = inputFrames

    /**
     * Fully custom body of the node. The engine only manages the title bar, ports and
     * drag/selection chrome — the node owns all rendering and interaction below the title,
     * including its own padding and layout.
     */
    @Composable
    fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    )
}
