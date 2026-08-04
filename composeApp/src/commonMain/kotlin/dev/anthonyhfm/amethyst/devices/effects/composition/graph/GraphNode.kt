package dev.anthonyhfm.amethyst.devices.effects.composition.graph

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.OutputNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.ScannerNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.CompositionNodeState
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.OriginBindableState
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.CompositionAutomationLane
import kotlinx.serialization.Serializable

@Serializable
data class CompositionGraph(
    val nodes: List<CompositionNode> = emptyList(),
    val connections: List<CompositionConnection> = emptyList(),
    val outputNodeId: String = "",
    val viewport: GraphViewportState = GraphViewportState(),
)

@Serializable
data class GraphViewportState(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zoom: Float = 1f,
)

@Serializable
data class CompositionConnection(
    val id: String = UUID.randomUUID(),
    val fromNodeId: String,
    val fromPort: String = OUTPUT_PORT,
    val toNodeId: String,
    val toPort: String = INPUT_PORT,
)

@Serializable
data class CompositionNode(
    val id: String = UUID.randomUUID(),
    val type: String,
    val position: NodePosition,
    val label: String = NodeRegistry.labelFor(type),
    val state: CompositionNodeState = NodeRegistry.defaultStateFor(type),
    val automation: List<CompositionAutomationLane> = emptyList(),
)

@Serializable
data class NodePosition(
    val x: Float,
    val y: Float,
)

const val INPUT_PORT = "in"
const val OUTPUT_PORT = "out"

fun defaultCompositionGraph(): CompositionGraph {
    val scanner = CompositionNode(
        type = ScannerNode.type,
        position = NodePosition(48f, 96f),
    )
    val output = CompositionNode(
        type = OutputNode.type,
        position = NodePosition(328f, 96f),
    )

    return CompositionGraph(
        nodes = listOf(scanner, output),
        connections = listOf(
            CompositionConnection(fromNodeId = scanner.id, toNodeId = output.id),
        ),
        outputNodeId = output.id,
    )
}

fun CompositionGraph.node(id: String): CompositionNode? = nodes.firstOrNull { it.id == id }

fun CompositionGraph.withNode(updated: CompositionNode): CompositionGraph =
    copy(nodes = nodes.map { if (it.id == updated.id) updated else it })

fun CompositionGraph.withViewport(viewport: GraphViewportState): CompositionGraph =
    copy(viewport = viewport)

fun CompositionGraph.withoutNode(nodeId: String): CompositionGraph {
    val node = node(nodeId) ?: return this
    if (NodeRegistry.definitionFor(node)?.isOutput == true) return this

    return copy(
        nodes = nodes.filterNot { it.id == nodeId },
        connections = connections.filterNot { it.fromNodeId == nodeId || it.toNodeId == nodeId },
    )
}

fun CompositionGraph.withConnection(fromNodeId: String, toNodeId: String): CompositionGraph {
    if (fromNodeId == toNodeId) return this
    val from = node(fromNodeId) ?: return this
    val to = node(toNodeId) ?: return this
    val fromDefinition = NodeRegistry.definitionFor(from) ?: return this
    val toDefinition = NodeRegistry.definitionFor(to) ?: return this
    if (!fromDefinition.hasOutput || !toDefinition.hasInput) return this

    val candidate = CompositionConnection(fromNodeId = fromNodeId, toNodeId = toNodeId)
    val next = copy(
        connections = connections
            .plus(candidate)
            .distinctBy { "${it.fromNodeId}:${it.fromPort}->${it.toNodeId}:${it.toPort}" },
    )

    return if (GraphValidator.validate(next).isValid) next else this
}

fun CompositionGraph.withoutConnection(connectionId: String): CompositionGraph =
    copy(connections = connections.filterNot { it.id == connectionId })

fun CompositionGraph.hasOriginBinding(): Boolean =
    nodes.any { (it.state as? OriginBindableState)?.boundToOrigin == true }
