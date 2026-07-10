package dev.anthonyhfm.amethyst.devices.effects.composition.graph

import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry

object GraphValidator {
    data class Result(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val orderedNodeIds: List<String> = emptyList(),
    )

    fun validate(graph: CompositionGraph): Result {
        val errors = mutableListOf<String>()
        val nodeIds = graph.nodes.map { it.id }.toSet()
        val outputCount = graph.nodes.count { NodeRegistry.definitionFor(it)?.isOutput == true }

        if (outputCount != 1) {
            errors.add("Composition needs exactly one Output node.")
        }
        if (graph.outputNodeId !in nodeIds) {
            errors.add("Output node is missing.")
        }
        graph.node(graph.outputNodeId)?.let { outputNode ->
            if (NodeRegistry.definitionFor(outputNode)?.isOutput != true) {
                errors.add("Output node id must point to an Output node.")
            }
        }
        graph.nodes.forEach { node ->
            if (NodeRegistry.definitionFor(node) == null) {
                errors.add("Unknown or invalid node type: ${node.type}.")
            }
        }

        graph.connections.forEach { connection ->
            if (connection.fromNodeId !in nodeIds) errors.add("Connection source is missing.")
            if (connection.toNodeId !in nodeIds) errors.add("Connection target is missing.")
            if (connection.fromNodeId == connection.toNodeId) errors.add("Node cannot connect to itself.")
            val from = graph.node(connection.fromNodeId)
            val to = graph.node(connection.toNodeId)
            val fromDefinition = from?.let { NodeRegistry.definitionFor(it) }
            val toDefinition = to?.let { NodeRegistry.definitionFor(it) }
            if (fromDefinition != null && !fromDefinition.hasOutput) errors.add("${fromDefinition.label} has no output port.")
            if (toDefinition != null && !toDefinition.hasInput) errors.add("${toDefinition.label} has no input port.")
        }

        val ordered = topologicalOrder(graph)
        if (ordered == null) {
            errors.add("Composition graph cannot contain cycles.")
        }

        return Result(
            isValid = errors.isEmpty(),
            errors = errors.distinct(),
            orderedNodeIds = ordered.orEmpty(),
        )
    }

    private fun topologicalOrder(graph: CompositionGraph): List<String>? {
        val incomingCount = graph.nodes.associate { node ->
            node.id to graph.connections.count { it.toNodeId == node.id }
        }.toMutableMap()
        val outgoing = graph.connections.groupBy { it.fromNodeId }
        val queue = incomingCount.filterValues { it == 0 }.keys.toMutableList()
        val ordered = mutableListOf<String>()

        while (queue.isNotEmpty()) {
            val nodeId = queue.removeAt(0)
            ordered.add(nodeId)
            outgoing[nodeId].orEmpty().forEach { edge ->
                val nextCount = (incomingCount[edge.toNodeId] ?: 0) - 1
                incomingCount[edge.toNodeId] = nextCount
                if (nextCount == 0) {
                    queue.add(edge.toNodeId)
                }
            }
        }

        return ordered.takeIf { it.size == graph.nodes.size }
    }
}
