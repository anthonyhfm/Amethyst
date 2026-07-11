package dev.anthonyhfm.amethyst.devices.effects.composition

import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionConnection
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionGraph
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.NodePosition
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.withoutConnection
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.withoutNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CompositionGraphSelection(
    val nodeIds: Set<String> = emptySet(),
    val connectionIds: Set<String> = emptySet(),
)

/** Commands and transient selection for one Composition workspace session. */
class CompositionGraphEditor(private val device: CompositionChainDevice) {
    private val _selection = MutableStateFlow(CompositionGraphSelection())
    val selection = _selection.asStateFlow()

    fun selectNode(id: String, additive: Boolean = false) {
        _selection.value = if (additive) {
            _selection.value.copy(nodeIds = _selection.value.nodeIds.toggle(id))
        } else CompositionGraphSelection(nodeIds = setOf(id))
    }

    fun selectConnection(id: String, additive: Boolean = false) {
        _selection.value = if (additive) {
            _selection.value.copy(connectionIds = _selection.value.connectionIds.toggle(id))
        } else CompositionGraphSelection(connectionIds = setOf(id))
    }

    fun clearSelection() { _selection.value = CompositionGraphSelection() }

    fun selectAll(graph: CompositionGraph = device.state.value.graph): Boolean {
        val nodeIds = graph.nodes
            .filterNot { NodeRegistry.definitionFor(it)?.isOutput == true }
            .mapTo(linkedSetOf(), CompositionNode::id)
        val connectionIds = graph.connections.mapTo(linkedSetOf(), CompositionConnection::id)
        if (nodeIds.isEmpty() && connectionIds.isEmpty()) return false
        _selection.value = CompositionGraphSelection(nodeIds, connectionIds)
        return true
    }

    fun canSelectAll(graph: CompositionGraph = device.state.value.graph): Boolean =
        graph.connections.isNotEmpty() || graph.nodes.any { NodeRegistry.definitionFor(it)?.isOutput != true }

    fun canCopy(): Boolean = selectedEditableNodes().isNotEmpty()
    fun canDelete(): Boolean = selectedEditableNodes().isNotEmpty() || _selection.value.connectionIds.isNotEmpty()
    fun canPaste(clipboard: ClipboardData? = ClipboardManager.clipboardData.value): Boolean =
        clipboard is ClipboardData.CompositionSubgraph && clipboard.nodes.isNotEmpty()

    fun copy(): Boolean {
        val graph = device.state.value.graph
        val nodes = selectedEditableNodes(graph)
        if (nodes.isEmpty()) return false
        val ids = nodes.mapTo(hashSetOf(), CompositionNode::id)
        ClipboardManager.setClipboardData(
            ClipboardData.CompositionSubgraph(
                nodes = nodes,
                connections = graph.connections.filter { it.fromNodeId in ids && it.toNodeId in ids },
            )
        )
        return true
    }

    fun cut(): Boolean {
        if (!copy()) return false
        return delete()
    }

    fun duplicate(): Boolean {
        val graph = device.state.value.graph
        val nodes = selectedEditableNodes(graph)
        if (nodes.isEmpty()) return false
        val ids = nodes.mapTo(hashSetOf(), CompositionNode::id)
        return pasteSubgraph(ClipboardData.CompositionSubgraph(nodes, graph.connections.filter { it.fromNodeId in ids && it.toNodeId in ids }))
    }

    fun paste(): Boolean = (ClipboardManager.clipboardData.value as? ClipboardData.CompositionSubgraph)
        ?.let(::pasteSubgraph) ?: false

    fun delete(): Boolean {
        val selection = _selection.value
        val editableNodeIds = selectedEditableNodes().mapTo(hashSetOf(), CompositionNode::id)
        val connectionIds = selection.connectionIds
        if (editableNodeIds.isEmpty() && connectionIds.isEmpty()) return false
        device.updateGraph { graph ->
            connectionIds.fold(graph) { current, id -> current.withoutConnection(id) }
                .let { current -> editableNodeIds.fold(current) { acc, id -> acc.withoutNode(id) } }
        }
        clearSelection()
        return true
    }

    /** Drops selections which no longer exist after undo/redo or another graph mutation. */
    fun reconcile(graph: CompositionGraph = device.state.value.graph) {
        val nodeIds = graph.nodes.mapTo(hashSetOf(), CompositionNode::id)
        val connectionIds = graph.connections.mapTo(hashSetOf(), CompositionConnection::id)
        _selection.value = _selection.value.copy(
            nodeIds = _selection.value.nodeIds.intersect(nodeIds),
            connectionIds = _selection.value.connectionIds.intersect(connectionIds),
        )
    }

    private fun pasteSubgraph(clip: ClipboardData.CompositionSubgraph): Boolean {
        if (clip.nodes.isEmpty()) return false
        val idMap = clip.nodes.associate { it.id to UUID.randomUUID() }
        val pastedNodes = clip.nodes.map { node ->
            node.copy(
                id = idMap.getValue(node.id),
                position = NodePosition(node.position.x + PASTE_OFFSET, node.position.y + PASTE_OFFSET),
            )
        }
        val pastedConnections = clip.connections.mapNotNull { connection ->
            val from = idMap[connection.fromNodeId] ?: return@mapNotNull null
            val to = idMap[connection.toNodeId] ?: return@mapNotNull null
            connection.copy(id = UUID.randomUUID(), fromNodeId = from, toNodeId = to)
        }
        device.updateGraph { graph -> graph.copy(nodes = graph.nodes + pastedNodes, connections = graph.connections + pastedConnections) }
        _selection.value = CompositionGraphSelection(
            nodeIds = pastedNodes.mapTo(linkedSetOf(), CompositionNode::id),
            connectionIds = pastedConnections.mapTo(linkedSetOf(), CompositionConnection::id),
        )
        return true
    }

    private fun selectedEditableNodes(graph: CompositionGraph = device.state.value.graph): List<CompositionNode> =
        graph.nodes.filter { it.id in _selection.value.nodeIds && NodeRegistry.definitionFor(it)?.isOutput != true }

    private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

    private companion object { const val PASTE_OFFSET = 32f }
}
