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
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.node
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.withNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.CompositionAutomationLane
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.automationParameter
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.CompositionAutomationPoint
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.lane
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CompositionGraphSelection(
    val nodeIds: Set<String> = emptySet(),
    val connectionIds: Set<String> = emptySet(),
)

data class CompositionAutomationFocus(val nodeId: String, val parameterId: String)

/** Commands and transient selection for one Composition workspace session. */
class CompositionGraphEditor(private val device: CompositionChainDevice) {
    private val _selection = MutableStateFlow(CompositionGraphSelection())
    val selection = _selection.asStateFlow()
    private val _automationFocus = MutableStateFlow<CompositionAutomationFocus?>(null)
    val automationFocus = _automationFocus.asStateFlow()
    /** Graph before the current live automation edit, used to make its many frames one undo step. */
    private var automationPreviewBefore: CompositionGraph? = null

    fun closeAutomation() { _automationFocus.value = null }

    fun automate(nodeId: String, parameterId: String) {
        device.updateGraph { graph ->
            val node = graph.node(nodeId) ?: return@updateGraph graph
            val parameter = node.automationParameter(parameterId) ?: return@updateGraph graph
            if (node.lane(parameterId) != null) return@updateGraph graph
            val value = parameter.normalise(parameter.valueOf(node) ?: return@updateGraph graph)
            graph.withNode(node.copy(automation = node.automation + CompositionAutomationLane(
                parameterId = parameterId,
                points = listOf(CompositionAutomationPoint(0f, value), CompositionAutomationPoint(1f, value)),
            )))
        }
        _automationFocus.value = CompositionAutomationFocus(nodeId, parameterId)
    }

    fun editAutomation(nodeId: String, parameterId: String) {
        if (device.state.value.graph.node(nodeId)?.lane(parameterId) != null) {
            _automationFocus.value = CompositionAutomationFocus(nodeId, parameterId)
        }
    }

    fun removeAutomation(nodeId: String, parameterId: String, progress: Float) {
        device.updateGraph { graph ->
            val node = graph.node(nodeId) ?: return@updateGraph graph
            val parameter = node.automationParameter(parameterId) ?: return@updateGraph graph
            val lane = node.lane(parameterId) ?: return@updateGraph graph
            val fallback = parameter.valueOf(node) ?: return@updateGraph graph
            val staticNode = parameter.withValue(node, parameter.denormalise(lane.valueAt(progress, parameter.normalise(fallback))))
                ?: return@updateGraph graph
            graph.withNode(staticNode.copy(automation = staticNode.automation.filterNot { it.parameterId == parameterId }))
        }
        if (_automationFocus.value == CompositionAutomationFocus(nodeId, parameterId)) closeAutomation()
    }

    fun setAutomationPoint(nodeId: String, parameterId: String, progress: Float, nativeValue: Float) {
        device.updateGraph { graph ->
            val node = graph.node(nodeId) ?: return@updateGraph graph
            val parameter = node.automationParameter(parameterId) ?: return@updateGraph graph
            val lane = node.lane(parameterId) ?: return@updateGraph graph
            val point = CompositionAutomationPoint(progress.coerceIn(0f, 1f), parameter.normalise(nativeValue))
            val points = lane.points.filterNot { kotlin.math.abs(it.progress - point.progress) < .002f } + point
            graph.withNode(node.copy(automation = node.automation.map {
                if (it.parameterId == parameterId) it.copy(points = points).normalised() else it
            }))
        }
    }

    /** Updates only a selected point's value, preserving its time and curve handles. */
    fun updateAutomationPointValue(nodeId: String, parameterId: String, pointId: String, nativeValue: Float): Boolean {
        val node = device.state.value.graph.node(nodeId) ?: return false
        val parameter = node.automationParameter(parameterId) ?: return false
        val lane = node.lane(parameterId) ?: return false
        if (lane.points.none { it.pointId == pointId }) return false

        val normalisedValue = parameter.normalise(nativeValue)
        return device.updateGraph { graph ->
            val currentNode = graph.node(nodeId) ?: return@updateGraph graph
            graph.withNode(currentNode.copy(automation = currentNode.automation.map { currentLane ->
                if (currentLane.parameterId != parameterId) currentLane else currentLane.copy(
                    points = currentLane.points.map { point ->
                        if (point.pointId == pointId) point.copy(value = normalisedValue) else point
                    }
                ).normalised()
            }))
        }
    }

    /**
     * Applies a point value immediately so the graph, node controls and curve all observe the
     * same value during an interaction. Call [commitAutomationPreview] when the gesture ends.
     */
    fun previewAutomationPointValue(nodeId: String, parameterId: String, pointId: String, nativeValue: Float): Boolean {
        val node = device.state.value.graph.node(nodeId) ?: return false
        val parameter = node.automationParameter(parameterId) ?: return false
        val points = node.lane(parameterId)?.points ?: return false
        if (points.none { it.pointId == pointId }) return false
        return previewAutomationPoints(nodeId, parameterId, points.map { point ->
            if (point.pointId == pointId) point.copy(value = parameter.normalise(nativeValue)) else point
        })
    }

    /** Live, shared preview for moving points or their curve handles. */
    fun previewAutomationPoints(nodeId: String, parameterId: String, points: List<CompositionAutomationPoint>): Boolean {
        if (automationPreviewBefore == null) automationPreviewBefore = device.state.value.graph
        return device.updateGraph(undoable = false) { graph ->
            val node = graph.node(nodeId) ?: return@updateGraph graph
            if (node.lane(parameterId) == null) return@updateGraph graph
            graph.withNode(node.copy(automation = node.automation.map { lane ->
                if (lane.parameterId == parameterId) lane.copy(points = points).normalised() else lane
            }))
        }
    }

    /** Records the shared live preview as one undoable edit. */
    fun commitAutomationPreview() {
        automationPreviewBefore?.let(device::commitGraphEdit)
        automationPreviewBefore = null
    }

    fun updateAutomationLane(nodeId: String, parameterId: String, transform: (CompositionAutomationLane) -> CompositionAutomationLane) {
        device.updateGraph { graph ->
            val node = graph.node(nodeId) ?: return@updateGraph graph
            val lane = node.lane(parameterId) ?: return@updateGraph graph
            graph.withNode(node.copy(automation = node.automation.map {
                if (it.parameterId == parameterId) transform(lane).normalised() else it
            }))
        }
    }

    /** One atomic automation edit; callers keep drag previews local until this is invoked. */
    fun replaceAutomationPoints(nodeId: String, parameterId: String, points: List<CompositionAutomationPoint>) {
        updateAutomationLane(nodeId, parameterId) { lane -> lane.copy(points = points) }
    }

    fun deleteAutomationPoints(nodeId: String, parameterId: String, pointIds: Set<String>): Boolean {
        val lane = device.state.value.graph.node(nodeId)?.lane(parameterId) ?: return false
        val remaining = lane.points.filterNot { it.pointId in pointIds }
        if (remaining.isEmpty() || remaining.size == lane.points.size) return false
        replaceAutomationPoints(nodeId, parameterId, remaining)
        return true
    }

    fun resetAutomationHandle(nodeId: String, parameterId: String, pointId: String, incoming: Boolean): Boolean {
        val lane = device.state.value.graph.node(nodeId)?.lane(parameterId) ?: return false
        val updated = lane.points.map { point ->
            if (point.pointId != pointId) point else if (incoming) point.copy(inHandleTime = null, inHandleValue = null)
            else point.copy(outHandleTime = null, outHandleValue = null)
        }
        if (updated == lane.points) return false
        replaceAutomationPoints(nodeId, parameterId, updated)
        return true
    }

    fun deleteSelectedAutomation(): Boolean {
        val focus = _automationFocus.value ?: return false
        val selections = SelectionManager.selections.value
        val handle = selections.filterIsInstance<Selectable.CompositionAutomationHandle>()
            .lastOrNull { it.nodeId == focus.nodeId && it.parameterId == focus.parameterId }
        if (handle != null) return resetAutomationHandle(focus.nodeId, focus.parameterId, handle.pointId, handle.incoming)
        val pointIds = SelectionManager.selectedCompositionAutomationPointIds(device.selectionUUID, focus.nodeId, focus.parameterId)
        return deleteAutomationPoints(focus.nodeId, focus.parameterId, pointIds)
    }

    fun selectAllAutomationPoints(): Boolean {
        val focus = _automationFocus.value ?: return false
        val points = device.state.value.graph.node(focus.nodeId)?.lane(focus.parameterId)?.points.orEmpty()
        if (points.isEmpty()) return false
        SelectionManager.selectCompositionAutomationPoints(device.selectionUUID, focus.nodeId, focus.parameterId, points.map(CompositionAutomationPoint::pointId))
        return true
    }

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
        _automationFocus.value = _automationFocus.value?.takeIf { it.nodeId in nodeIds }
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
