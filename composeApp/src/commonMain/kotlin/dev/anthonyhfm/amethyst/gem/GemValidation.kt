package dev.anthonyhfm.amethyst.gem

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class GemValidationErrorCode {
    DUPLICATE_GRAPH_ID,
    DUPLICATE_NODE_ID,
    DUPLICATE_CONNECTION_ID,
    DUPLICATE_EXPOSED_PARAMETER_ID,
    DUPLICATE_HOST_PORT_ID,
    UNKNOWN_NODE_TYPE,
    UNSUPPORTED_NODE_VERSION,
    MISSING_CONNECTION_NODE,
    MISSING_CONNECTION_PIN,
    INVALID_CONNECTION_DIRECTION,
    INCOMPATIBLE_PIN_TYPES,
    MULTIPLE_INPUT_CONNECTIONS,
    MISSING_REQUIRED_INPUT,
    INVALID_EXPOSED_PARAMETER_DEFAULT,
    UNKNOWN_DEFAULT_STATE_PARAMETER,
    INVALID_DEFAULT_STATE_PARAMETER,
    HOST_DOMAIN_MISMATCH,
    HOST_SHAPE_MISMATCH,
    HOST_NODE_OUTSIDE_ROOT,
    MISSING_HOST_PORT_ID,
    UNKNOWN_HOST_PORT,
    DUPLICATE_HOST_PORT_BINDING,
    MISSING_REQUIRED_HOST_PORT,
    GRAPH_CYCLE,
    UNKNOWN_SUBGRAPH,
    INVALID_SUBGRAPH_INTERFACE,
    INVALID_SUBGRAPH_BINDING,
    SUBGRAPH_RECURSION,
    SUBGRAPH_CALL_INTERFACE_MISMATCH,
    UNKNOWN_BINDING_GRAPH,
    MISSING_BINDING_NODE,
    MISSING_BINDING_PIN,
    INVALID_BINDING_TARGET,
    INCOMPATIBLE_BINDING_TYPE,
    LED_INPUT_MISSING,
    LED_INPUT_AMBIGUOUS,
    LED_OUTPUT_MISSING,
    LED_OUTPUT_AMBIGUOUS
}

data class GemValidationError(
    val code: GemValidationErrorCode,
    val message: String,
    val graphId: String? = null,
    val nodeId: String? = null,
    val pinId: String? = null,
    val connectionId: String? = null,
    val exposedParameterId: String? = null,
    val relatedNodeId: String? = null,
    val relatedPinId: String? = null,
    val relatedNodeIds: List<String> = emptyList()
)

data class GemValidationResult(
    val graphs: List<GemValidatedGraph>,
    val errors: List<GemValidationError>
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

data class GemValidatedGraph(
    val graph: GemGraph,
    val nodes: List<GemValidatedNode>,
    val connections: List<GemValidatedConnection>,
    val executionOrder: List<String>
)

data class GemValidatedNode(
    val instance: GemNodeInstance,
    val descriptor: GemNodeDescriptor,
    val index: Int
) {
    val id: String
        get() = instance.id

    val pins: List<GemPin>
        get() = descriptor.pins

    fun pin(pinId: String): GemPin? = pins.firstOrNull { it.id == pinId }
}

data class GemValidatedConnection(
    val connection: GemConnection,
    val fromNode: GemValidatedNode,
    val fromPin: GemPin,
    val toNode: GemValidatedNode,
    val toPin: GemPin
)

object GemValidator {
    fun validate(
        asset: GemAsset,
        registry: GemNodeRegistry = GemNodeRegistry.builtIns
    ): GemValidationResult {
        val errors = mutableListOf<GemValidationError>()
        val graphs = listOf(asset.definition.rootGraph) + asset.definition.subgraphs
        val graphsById = linkedMapOf<String, GemGraph>()

        graphs.forEach { graph ->
            if (graphsById.containsKey(graph.id)) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.DUPLICATE_GRAPH_ID,
                    message = "Duplicate graph ID '${graph.id}'.",
                    graphId = graph.id
                )
            } else {
                graphsById[graph.id] = graph
            }
        }

        errors += validateHostContract(asset.definition.host)
        errors += validateExposedParameters(asset.definition)
        val subgraphContracts = validateSubgraphInterfaces(
            definition = asset.definition,
            graphsById = graphsById,
            registry = registry,
            errors = errors
        )
        val inputBindings = collectValidInputBindings(
            definition = asset.definition,
            graphsById = graphsById,
            registry = registry,
            errors = errors
        )

        val validatedGraphs = graphs.mapNotNull { graph ->
            validateGraph(
                graph = graph,
                host = asset.definition.host,
                rootGraphId = asset.definition.rootGraph.id,
                graphsById = graphsById,
                boundInputTargets = inputBindings[graph.id].orEmpty() + subgraphContracts[graph.id]?.inputTargets.orEmpty(),
                registry = registry,
                errors = errors
            )
        }

        findSubgraphRecursion(graphs, graphsById).takeIf { it.isNotEmpty() }?.let { cycleGraphIds ->
            errors += GemValidationError(
                code = GemValidationErrorCode.SUBGRAPH_RECURSION,
                message = "Gem asset '${asset.metadata.id}' contains recursive subgraph inclusion.",
                graphId = cycleGraphIds.firstOrNull(),
                relatedNodeIds = cycleGraphIds
            )
        }

        return GemValidationResult(
            graphs = validatedGraphs,
            errors = errors.toList()
        )
    }

    private fun validateHostContract(host: GemHostIoContract): List<GemValidationError> {
        val errors = mutableListOf<GemValidationError>()
        val supportedDomains = host.supportedDomains.toSet()

        host.inputs
            .groupBy(GemHostPort::id)
            .filterValues { it.size > 1 }
            .keys
            .sorted()
            .forEach { portId ->
                errors += GemValidationError(
                    code = GemValidationErrorCode.DUPLICATE_HOST_PORT_ID,
                    message = "Host input port ID '$portId' is declared more than once.",
                    pinId = portId
                )
            }

        host.outputs
            .groupBy(GemHostPort::id)
            .filterValues { it.size > 1 }
            .keys
            .sorted()
            .forEach { portId ->
                errors += GemValidationError(
                    code = GemValidationErrorCode.DUPLICATE_HOST_PORT_ID,
                    message = "Host output port ID '$portId' is declared more than once.",
                    pinId = portId
                )
            }

        host.inputs.forEach { port ->
            if (port.domain !in supportedDomains) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.HOST_DOMAIN_MISMATCH,
                    message = "Host input '${port.id}' uses unsupported domain ${port.domain}.",
                    pinId = port.id
                )
            }
        }

        host.outputs.forEach { port ->
            if (port.domain !in supportedDomains) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.HOST_DOMAIN_MISMATCH,
                    message = "Host output '${port.id}' uses unsupported domain ${port.domain}.",
                    pinId = port.id
                )
            }
        }

        if (host.assetShape == GemHostAssetShape.SOURCE && host.inputs.isNotEmpty()) {
            errors += GemValidationError(
                code = GemValidationErrorCode.HOST_SHAPE_MISMATCH,
                message = "Source gems cannot declare host inputs."
            )
        }

        if (host.assetShape == GemHostAssetShape.SINK && host.outputs.isNotEmpty()) {
            errors += GemValidationError(
                code = GemValidationErrorCode.HOST_SHAPE_MISMATCH,
                message = "Sink gems cannot declare host outputs."
            )
        }

        return errors
    }

    private fun validateExposedParameters(definition: GemDefinition): List<GemValidationError> {
        val errors = mutableListOf<GemValidationError>()
        val parametersById = linkedMapOf<String, GemExposedParameter>()

        definition.exposedParameters.forEach { parameter ->
            if (parametersById.containsKey(parameter.id)) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.DUPLICATE_EXPOSED_PARAMETER_ID,
                    message = "Exposed parameter ID '${parameter.id}' is declared more than once.",
                    exposedParameterId = parameter.id
                )
            } else {
                parametersById[parameter.id] = parameter
            }

            if (!parameter.defaultValue.matchesDeclaredType(parameter.type)) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.INVALID_EXPOSED_PARAMETER_DEFAULT,
                    message = "Exposed parameter '${parameter.id}' has a default value that does not match its declared type.",
                    exposedParameterId = parameter.id
                )
            }
        }

        definition.defaultState.exposedParameterValues.forEach { (parameterId, value) ->
            val parameter = parametersById[parameterId]
            if (parameter == null) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.UNKNOWN_DEFAULT_STATE_PARAMETER,
                    message = "Default state references unknown exposed parameter '$parameterId'.",
                    exposedParameterId = parameterId
                )
            } else if (!value.matchesDeclaredType(parameter.type)) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.INVALID_DEFAULT_STATE_PARAMETER,
                    message = "Default state for exposed parameter '$parameterId' does not match its declared type.",
                    exposedParameterId = parameterId
                )
            }
        }

        return errors
    }

    private fun collectValidInputBindings(
        definition: GemDefinition,
        graphsById: Map<String, GemGraph>,
        registry: GemNodeRegistry,
        errors: MutableList<GemValidationError>
    ): Map<String, Set<InputTarget>> {
        val validTargets = mutableMapOf<String, MutableSet<InputTarget>>()

        definition.exposedParameters.forEach { parameter ->
            parameter.bindings.forEach { binding ->
                val graph = graphsById[binding.graphId]
                if (graph == null) {
                    errors += GemValidationError(
                        code = GemValidationErrorCode.UNKNOWN_BINDING_GRAPH,
                        message = "Binding for exposed parameter '${parameter.id}' references unknown graph '${binding.graphId}'.",
                        graphId = binding.graphId,
                        exposedParameterId = parameter.id,
                        nodeId = binding.nodeId,
                        pinId = binding.pinId
                    )
                    return@forEach
                }

                val node = graph.nodes.firstOrNull { it.id == binding.nodeId }
                if (node == null) {
                    errors += GemValidationError(
                        code = GemValidationErrorCode.MISSING_BINDING_NODE,
                        message = "Binding for exposed parameter '${parameter.id}' references unknown node '${binding.nodeId}'.",
                        graphId = graph.id,
                        exposedParameterId = parameter.id,
                        nodeId = binding.nodeId,
                        pinId = binding.pinId
                    )
                    return@forEach
                }

                val descriptor = resolveNodeDescriptor(
                    graphId = graph.id,
                    node = node,
                    graphsById = graphsById,
                    registry = registry,
                    errors = errors
                ) ?: return@forEach
                val pin = descriptor.pin(binding.pinId)
                if (pin == null) {
                    errors += GemValidationError(
                        code = GemValidationErrorCode.MISSING_BINDING_PIN,
                        message = "Binding for exposed parameter '${parameter.id}' references unknown pin '${binding.pinId}' on node '${binding.nodeId}'.",
                        graphId = graph.id,
                        exposedParameterId = parameter.id,
                        nodeId = binding.nodeId,
                        pinId = binding.pinId
                    )
                    return@forEach
                }

                val pinDirection = when {
                    descriptor.inputs.any { it.id == binding.pinId } -> GemPinDirection.INPUT
                    descriptor.outputs.any { it.id == binding.pinId } -> GemPinDirection.OUTPUT
                    else -> null
                }
                val expectedDirection = when (binding.targetKind) {
                    GemBindingTargetKind.INPUT_PIN -> GemPinDirection.INPUT
                    GemBindingTargetKind.OUTPUT_PIN -> GemPinDirection.OUTPUT
                }
                if (pinDirection != expectedDirection || pin.type.family != GemPinFamily.VALUE) {
                    errors += GemValidationError(
                        code = GemValidationErrorCode.INVALID_BINDING_TARGET,
                        message = "Binding for exposed parameter '${parameter.id}' must target a ${expectedDirection.name.lowercase()} value pin.",
                        graphId = graph.id,
                        exposedParameterId = parameter.id,
                        nodeId = binding.nodeId,
                        pinId = binding.pinId
                    )
                    return@forEach
                }

                val expectedPinType = GemPinType.Value(parameter.type)
                if (pin.type != expectedPinType) {
                    errors += GemValidationError(
                        code = GemValidationErrorCode.INCOMPATIBLE_BINDING_TYPE,
                        message = "Binding for exposed parameter '${parameter.id}' is incompatible with pin '${binding.pinId}'.",
                        graphId = graph.id,
                        exposedParameterId = parameter.id,
                        nodeId = binding.nodeId,
                        pinId = binding.pinId
                    )
                    return@forEach
                }

                if (binding.targetKind == GemBindingTargetKind.INPUT_PIN) {
                    validTargets.getOrPut(graph.id) { linkedSetOf() } += InputTarget(
                        nodeId = binding.nodeId,
                        pinId = binding.pinId
                    )
                }
            }
        }

        return validTargets
    }

    private fun validateSubgraphInterfaces(
        definition: GemDefinition,
        graphsById: Map<String, GemGraph>,
        registry: GemNodeRegistry,
        errors: MutableList<GemValidationError>
    ): Map<String, SubgraphContract> {
        val contracts = mutableMapOf<String, SubgraphContract>()

        definition.subgraphs.forEach { graph ->
            val graphErrors = mutableListOf<GemValidationError>()
            val interfacePortIds = (graph.subgraphInterface.inputs.map { it.id } + graph.subgraphInterface.outputs.map { it.id })
            interfacePortIds
                .groupBy { it }
                .filterValues { it.size > 1 }
                .keys
                .sorted()
                .forEach { portId ->
                    graphErrors += GemValidationError(
                        code = GemValidationErrorCode.INVALID_SUBGRAPH_INTERFACE,
                        message = "Subgraph '${graph.id}' declares duplicate interface port '$portId'.",
                        graphId = graph.id,
                        pinId = portId
                    )
                }

            val inputTargets = linkedSetOf<InputTarget>()
            graph.subgraphInterface.inputs.forEach { port ->
                validateSubgraphPortDefault(graph.id, port.id, port.type, port.defaultValue, graphErrors)
                port.bindings.forEach { binding ->
                    val node = graph.nodes.firstOrNull { it.id == binding.nodeId }
                    if (node == null) {
                        graphErrors += GemValidationError(
                            code = GemValidationErrorCode.INVALID_SUBGRAPH_BINDING,
                            message = "Subgraph input '${port.id}' references unknown node '${binding.nodeId}'.",
                            graphId = graph.id,
                            nodeId = binding.nodeId,
                            pinId = binding.pinId
                        )
                        return@forEach
                    }
                    val descriptor = resolveNodeDescriptor(
                        graphId = graph.id,
                        node = node,
                        graphsById = graphsById,
                        registry = registry,
                        errors = graphErrors
                    ) ?: return@forEach
                    val pin = descriptor.pin(binding.pinId)
                    if (pin == null || descriptor.inputs.none { it.id == binding.pinId } || pin.type != port.type) {
                        graphErrors += GemValidationError(
                            code = GemValidationErrorCode.INVALID_SUBGRAPH_BINDING,
                            message = "Subgraph input '${port.id}' must bind to a matching input pin.",
                            graphId = graph.id,
                            nodeId = binding.nodeId,
                            pinId = binding.pinId
                        )
                        return@forEach
                    }
                    inputTargets += InputTarget(nodeId = binding.nodeId, pinId = binding.pinId)
                }
            }

            graph.subgraphInterface.outputs.forEach { port ->
                val binding = port.binding
                if (binding == null) {
                    graphErrors += GemValidationError(
                        code = GemValidationErrorCode.INVALID_SUBGRAPH_INTERFACE,
                        message = "Subgraph output '${port.id}' must declare an explicit binding.",
                        graphId = graph.id,
                        pinId = port.id
                    )
                    return@forEach
                }
                val node = graph.nodes.firstOrNull { it.id == binding.nodeId }
                if (node == null) {
                    graphErrors += GemValidationError(
                        code = GemValidationErrorCode.INVALID_SUBGRAPH_BINDING,
                        message = "Subgraph output '${port.id}' references unknown node '${binding.nodeId}'.",
                        graphId = graph.id,
                        nodeId = binding.nodeId,
                        pinId = binding.pinId
                    )
                    return@forEach
                }
                val descriptor = resolveNodeDescriptor(
                    graphId = graph.id,
                    node = node,
                    graphsById = graphsById,
                    registry = registry,
                    errors = graphErrors
                ) ?: return@forEach
                val pin = descriptor.pin(binding.pinId)
                if (pin == null || descriptor.outputs.none { it.id == binding.pinId } || pin.type != port.type) {
                    graphErrors += GemValidationError(
                        code = GemValidationErrorCode.INVALID_SUBGRAPH_BINDING,
                        message = "Subgraph output '${port.id}' must bind to a matching output pin.",
                        graphId = graph.id,
                        nodeId = binding.nodeId,
                        pinId = binding.pinId
                    )
                }
            }

            if (graphErrors.isEmpty()) {
                contracts[graph.id] = SubgraphContract(inputTargets = inputTargets)
            }
            errors += graphErrors
        }

        return contracts
    }

    private fun validateSubgraphPortDefault(
        graphId: String,
        portId: String,
        type: GemPinType,
        defaultValue: GemValue?,
        errors: MutableList<GemValidationError>
    ) {
        if (defaultValue == null) {
            return
        }

        val isValid = when {
            type is GemPinType.Value -> defaultValue.matchesDeclaredType(type.valueType)
            else -> false
        }
        if (!isValid) {
            errors += GemValidationError(
                code = GemValidationErrorCode.INVALID_SUBGRAPH_INTERFACE,
                message = "Subgraph interface port '$portId' has a default value that does not match its declared type.",
                graphId = graphId,
                pinId = portId
            )
        }
    }

    private fun resolveNodeDescriptor(
        graphId: String,
        node: GemNodeInstance,
        graphsById: Map<String, GemGraph>,
        registry: GemNodeRegistry,
        errors: MutableList<GemValidationError>
    ): GemNodeDescriptor? {
        if (node.type.typeId == GemBuiltInNodes.TypeIds.SUBGRAPH_CALL) {
            if (node.type.version != Gem.phase1SchemaVersion) {
                errors += unresolvedNodeTypeError(graphId, node, registry)
                return null
            }

            val subgraphId = node.subgraphId
            if (subgraphId == null) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.UNKNOWN_SUBGRAPH,
                    message = "Subgraph call node '${node.id}' is missing a referenced subgraph ID.",
                    graphId = graphId,
                    nodeId = node.id
                )
                return null
            }

            val subgraph = graphsById[subgraphId]
            if (subgraph == null || subgraph.kind != GemGraphKind.SUBGRAPH) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.UNKNOWN_SUBGRAPH,
                    message = "Subgraph call node '${node.id}' references unknown subgraph '$subgraphId'.",
                    graphId = graphId,
                    nodeId = node.id
                )
                return null
            }

            val descriptor = GemBuiltInNodes.subgraphCallDescriptor(subgraph)
            if (node.pins != descriptor.pins) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.SUBGRAPH_CALL_INTERFACE_MISMATCH,
                    message = "Subgraph call node '${node.id}' does not match the current interface of subgraph '$subgraphId'.",
                    graphId = graphId,
                    nodeId = node.id
                )
                return null
            }
            return descriptor
        }

        val descriptor = registry.find(node.type)
        if (descriptor == null) {
            errors += unresolvedNodeTypeError(graphId, node, registry)
        }
        return descriptor
    }

    private fun findSubgraphRecursion(
        graphs: List<GemGraph>,
        graphsById: Map<String, GemGraph>
    ): List<String> {
        val adjacency = graphs.associate { graph ->
            graph.id to graph.nodes
                .filter { it.type.typeId == GemBuiltInNodes.TypeIds.SUBGRAPH_CALL }
                .mapNotNull { node ->
                    node.subgraphId?.takeIf { graphsById[it]?.kind == GemGraphKind.SUBGRAPH }
                }
        }
        val visited = mutableSetOf<String>()
        val stack = mutableListOf<String>()

        fun dfs(graphId: String): List<String>? {
            if (graphId in stack) {
                val startIndex = stack.indexOf(graphId)
                return stack.drop(startIndex) + graphId
            }
            if (!visited.add(graphId)) {
                return null
            }

            stack += graphId
            adjacency[graphId].orEmpty().forEach { nextId ->
                dfs(nextId)?.let { return it }
            }
            stack.removeAt(stack.lastIndex)
            return null
        }

        return graphs
            .mapNotNull { dfs(it.id) }
            .firstOrNull()
            .orEmpty()
    }

    private fun validateGraph(
        graph: GemGraph,
        host: GemHostIoContract,
        rootGraphId: String,
        graphsById: Map<String, GemGraph>,
        boundInputTargets: Set<InputTarget>,
        registry: GemNodeRegistry,
        errors: MutableList<GemValidationError>
    ): GemValidatedGraph? {
        val graphErrors = mutableListOf<GemValidationError>()
        val nodesById = linkedMapOf<String, GemValidatedNode>()
        val hostBindings = mutableListOf<HostPortBinding>()

        graph.nodes.forEachIndexed { index, node ->
            if (nodesById.containsKey(node.id)) {
                graphErrors += GemValidationError(
                    code = GemValidationErrorCode.DUPLICATE_NODE_ID,
                    message = "Duplicate node ID '${node.id}' in graph '${graph.id}'.",
                    graphId = graph.id,
                    nodeId = node.id
                )
                return@forEachIndexed
            }

            val descriptor = resolveNodeDescriptor(
                graphId = graph.id,
                node = node,
                graphsById = graphsById,
                registry = registry,
                errors = graphErrors
            )
            if (descriptor == null) {
                return@forEachIndexed
            }

            validateHostNode(
                graphId = graph.id,
                rootGraphId = rootGraphId,
                node = node,
                host = host,
                errors = graphErrors
            )?.let(hostBindings::add)

            nodesById[node.id] = GemValidatedNode(
                instance = node.copy(pins = descriptor.pins),
                descriptor = descriptor,
                index = index
            )
        }

        if (graph.kind == GemGraphKind.ROOT) {
            validateRequiredHostPorts(
                graphId = graph.id,
                host = host,
                bindings = hostBindings,
                errors = graphErrors
            )
        }

        val validConnections = mutableListOf<GemValidatedConnection>()
        val inboundConnections = mutableMapOf<InputTarget, MutableList<GemConnection>>()
        val seenConnectionIds = mutableSetOf<String>()

        graph.connections.forEach { connection ->
            if (!seenConnectionIds.add(connection.id)) {
                graphErrors += GemValidationError(
                    code = GemValidationErrorCode.DUPLICATE_CONNECTION_ID,
                    message = "Duplicate connection ID '${connection.id}' in graph '${graph.id}'.",
                    graphId = graph.id,
                    connectionId = connection.id
                )
                return@forEach
            }

            val fromNode = nodesById[connection.from.nodeId]
            val toNode = nodesById[connection.to.nodeId]

            if (fromNode == null) {
                if (graph.nodes.none { it.id == connection.from.nodeId }) {
                    graphErrors += GemValidationError(
                        code = GemValidationErrorCode.MISSING_CONNECTION_NODE,
                        message = "Connection '${connection.id}' references missing source node '${connection.from.nodeId}'.",
                        graphId = graph.id,
                        connectionId = connection.id,
                        nodeId = connection.from.nodeId,
                        pinId = connection.from.pinId
                    )
                }
                return@forEach
            }

            if (toNode == null) {
                if (graph.nodes.none { it.id == connection.to.nodeId }) {
                    graphErrors += GemValidationError(
                        code = GemValidationErrorCode.MISSING_CONNECTION_NODE,
                        message = "Connection '${connection.id}' references missing target node '${connection.to.nodeId}'.",
                        graphId = graph.id,
                        connectionId = connection.id,
                        nodeId = connection.to.nodeId,
                        pinId = connection.to.pinId
                    )
                }
                return@forEach
            }

            val fromPin = fromNode.pin(connection.from.pinId)
            if (fromPin == null) {
                graphErrors += GemValidationError(
                    code = GemValidationErrorCode.MISSING_CONNECTION_PIN,
                    message = "Connection '${connection.id}' references missing source pin '${connection.from.pinId}'.",
                    graphId = graph.id,
                    connectionId = connection.id,
                    nodeId = fromNode.id,
                    pinId = connection.from.pinId
                )
                return@forEach
            }

            val toPin = toNode.pin(connection.to.pinId)
            if (toPin == null) {
                graphErrors += GemValidationError(
                    code = GemValidationErrorCode.MISSING_CONNECTION_PIN,
                    message = "Connection '${connection.id}' references missing target pin '${connection.to.pinId}'.",
                    graphId = graph.id,
                    connectionId = connection.id,
                    nodeId = toNode.id,
                    pinId = connection.to.pinId
                )
                return@forEach
            }

            if (fromPin.direction != GemPinDirection.OUTPUT || toPin.direction != GemPinDirection.INPUT) {
                graphErrors += GemValidationError(
                    code = GemValidationErrorCode.INVALID_CONNECTION_DIRECTION,
                    message = "Connection '${connection.id}' must connect an output pin to an input pin.",
                    graphId = graph.id,
                    connectionId = connection.id,
                    nodeId = fromNode.id,
                    pinId = fromPin.id,
                    relatedNodeId = toNode.id,
                    relatedPinId = toPin.id
                )
                return@forEach
            }

            fun pinsCompatible(a: GemPinType, b: GemPinType): Boolean {
                if (a == b) return true
                // AnySignal is compatible with any Signal domain in either direction.
                if (a is GemPinType.AnySignal && b is GemPinType.Signal) return true
                if (a is GemPinType.Signal && b is GemPinType.AnySignal) return true
                if (a is GemPinType.AnySignal && b is GemPinType.AnySignal) return true
                return false
            }

            if (!pinsCompatible(fromPin.type, toPin.type)) {
                graphErrors += GemValidationError(
                    code = GemValidationErrorCode.INCOMPATIBLE_PIN_TYPES,
                    message = "Connection '${connection.id}' links incompatible pin types.",
                    graphId = graph.id,
                    connectionId = connection.id,
                    nodeId = fromNode.id,
                    pinId = fromPin.id,
                    relatedNodeId = toNode.id,
                    relatedPinId = toPin.id
                )
                return@forEach
            }

            val inputTarget = InputTarget(nodeId = toNode.id, pinId = toPin.id)
            inboundConnections.getOrPut(inputTarget) { mutableListOf() } += connection
            validConnections += GemValidatedConnection(
                connection = connection,
                fromNode = fromNode,
                fromPin = fromPin,
                toNode = toNode,
                toPin = toPin
            )
        }

        inboundConnections
            .filterValues { it.size > 1 }
            .entries
            .sortedWith(compareBy<Map.Entry<InputTarget, MutableList<GemConnection>>>({ it.key.nodeId }, { it.key.pinId }))
            .forEach { entry ->
                val target = entry.key
                val connections = entry.value
                connections.drop(1).forEach { connection ->
                    graphErrors += GemValidationError(
                        code = GemValidationErrorCode.MULTIPLE_INPUT_CONNECTIONS,
                        message = "Input pin '${target.pinId}' on node '${target.nodeId}' has multiple inbound connections.",
                        graphId = graph.id,
                        connectionId = connection.id,
                        nodeId = target.nodeId,
                        pinId = target.pinId
                    )
                }
            }

        nodesById.values.forEach { node ->
            node.descriptor.inputs.forEach { input ->
                val target = InputTarget(nodeId = node.id, pinId = input.id)
                val hasInboundConnection = inboundConnections[target]?.isNotEmpty() == true
                val hasBinding = target in boundInputTargets
                val hasDefaultValue = input.defaultValue != null
                if (input.required && !hasInboundConnection && !hasBinding && !hasDefaultValue) {
                    graphErrors += GemValidationError(
                        code = GemValidationErrorCode.MISSING_REQUIRED_INPUT,
                        message = "Required input '${input.id}' on node '${node.id}' is not connected.",
                        graphId = graph.id,
                        nodeId = node.id,
                        pinId = input.id
                    )
                }
            }
        }

        val cycleNodeIds = findCycleNodes(nodesById.values.toList(), validConnections)
        if (cycleNodeIds.isNotEmpty()) {
            graphErrors += GemValidationError(
                code = GemValidationErrorCode.GRAPH_CYCLE,
                message = "Graph '${graph.id}' contains a cycle.",
                graphId = graph.id,
                relatedNodeIds = cycleNodeIds
            )
        }

        errors += graphErrors
        if (graphErrors.isNotEmpty()) {
            return null
        }

        return GemValidatedGraph(
            graph = graph,
            nodes = nodesById.values.toList(),
            connections = validConnections.toList(),
            executionOrder = topologicalOrder(nodesById.values.toList(), validConnections)
        )
    }

    private fun unresolvedNodeTypeError(
        graphId: String,
        node: GemNodeInstance,
        registry: GemNodeRegistry
    ): GemValidationError {
        val code = if (registry.findAll(node.type.typeId).isEmpty()) {
            GemValidationErrorCode.UNKNOWN_NODE_TYPE
        } else {
            GemValidationErrorCode.UNSUPPORTED_NODE_VERSION
        }

        val message = when (code) {
            GemValidationErrorCode.UNKNOWN_NODE_TYPE ->
                "Node '${node.id}' references unknown type '${node.type.typeId}'."
            GemValidationErrorCode.UNSUPPORTED_NODE_VERSION ->
                "Node '${node.id}' references unsupported version ${node.type.version} for type '${node.type.typeId}'."
            else -> error("Unexpected validation code: $code")
        }

        return GemValidationError(
            code = code,
            message = message,
            graphId = graphId,
            nodeId = node.id
        )
    }

    private fun validateHostNode(
        graphId: String,
        rootGraphId: String,
        node: GemNodeInstance,
        host: GemHostIoContract,
        errors: MutableList<GemValidationError>
    ): HostPortBinding? {
        val inputDomain = HOST_INPUT_TYPES[node.type.typeId]
        if (inputDomain != null) {
            if (graphId != rootGraphId) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.HOST_NODE_OUTSIDE_ROOT,
                    message = "Host input nodes may only appear on the root graph.",
                    graphId = graphId,
                    nodeId = node.id
                )
                return null
            }
            if (host.assetShape == GemHostAssetShape.SOURCE) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.HOST_SHAPE_MISMATCH,
                    message = "Source gems cannot use host input nodes.",
                    graphId = graphId,
                    nodeId = node.id
                )
                return null
            }
            return validateHostPortBinding(
                graphId = graphId,
                node = node,
                kind = HostPortKind.INPUT,
                domain = inputDomain,
                supportedDomains = host.supportedDomains.toSet(),
                declaredPorts = host.inputs,
                errors = errors
            )
        }

        val outputDomain = HOST_OUTPUT_TYPES[node.type.typeId]
        if (outputDomain != null) {
            if (graphId != rootGraphId) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.HOST_NODE_OUTSIDE_ROOT,
                    message = "Host output nodes may only appear on the root graph.",
                    graphId = graphId,
                    nodeId = node.id
                )
                return null
            }
            if (host.assetShape == GemHostAssetShape.SINK) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.HOST_SHAPE_MISMATCH,
                    message = "Sink gems cannot use host output nodes.",
                    graphId = graphId,
                    nodeId = node.id
                )
                return null
            }
            return validateHostPortBinding(
                graphId = graphId,
                node = node,
                kind = HostPortKind.OUTPUT,
                domain = outputDomain,
                supportedDomains = host.supportedDomains.toSet(),
                declaredPorts = host.outputs,
                errors = errors
            )
        }

        return null
    }

    private fun validateHostPortBinding(
        graphId: String,
        node: GemNodeInstance,
        kind: HostPortKind,
        domain: GemSignalDomain,
        supportedDomains: Set<GemSignalDomain>,
        declaredPorts: List<GemHostPort>,
        errors: MutableList<GemValidationError>
    ): HostPortBinding? {
        val portKind = kind.name.lowercase()
        if (domain !in supportedDomains) {
            errors += GemValidationError(
                code = GemValidationErrorCode.HOST_DOMAIN_MISMATCH,
                message = "Node '${node.id}' uses host $portKind domain $domain which is not declared in supportedDomains.",
                graphId = graphId,
                nodeId = node.id
            )
        }

        val portId = node.hostPortId()
        if (portId == null) {
            errors += GemValidationError(
                code = GemValidationErrorCode.MISSING_HOST_PORT_ID,
                message = "Node '${node.id}' must declare a host $portKind port ID in serialized state '${GemBuiltInNodes.HOST_PORT_ID_STATE_KEY}'.",
                graphId = graphId,
                nodeId = node.id,
                pinId = GemBuiltInNodes.HOST_PORT_ID_STATE_KEY
            )
            return null
        }

        // When no ports are explicitly declared, the host contract is open — accept any port ID.
        if (declaredPorts.isNotEmpty()) {
            val port = declaredPorts.firstOrNull { it.id == portId }
            if (port == null) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.UNKNOWN_HOST_PORT,
                    message = "Node '${node.id}' references unknown host $portKind port '$portId'.",
                    graphId = graphId,
                    nodeId = node.id,
                    pinId = portId
                )
                return null
            }

            if (port.domain != domain) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.HOST_DOMAIN_MISMATCH,
                    message = "Node '${node.id}' binds to host $portKind port '$portId' with domain ${port.domain}, but its node type requires $domain.",
                    graphId = graphId,
                    nodeId = node.id,
                    pinId = portId
                )
                return null
            }
        }

        return HostPortBinding(
            kind = kind,
            portId = portId,
            nodeId = node.id
        )
    }

    private fun validateRequiredHostPorts(
        graphId: String,
        host: GemHostIoContract,
        bindings: List<HostPortBinding>,
        errors: MutableList<GemValidationError>
    ) {
        HostPortKind.entries.forEach { kind ->
            bindings
                .filter { it.kind == kind }
                .groupBy(HostPortBinding::portId)
                .filterValues { it.size > 1 }
                .entries
                .sortedBy { it.key }
                .forEach { entry ->
                    errors += GemValidationError(
                        code = GemValidationErrorCode.DUPLICATE_HOST_PORT_BINDING,
                        message = "Host ${kind.name.lowercase()} port '${entry.key}' is bound by multiple nodes.",
                        graphId = graphId,
                        pinId = entry.key,
                        relatedNodeIds = entry.value.map(HostPortBinding::nodeId).sorted()
                    )
                }
        }

        val boundInputPortIds = bindings.filter { it.kind == HostPortKind.INPUT }.map(HostPortBinding::portId).toSet()
        host.inputs.filter(GemHostPort::required).forEach { port ->
            if (port.id !in boundInputPortIds) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.MISSING_REQUIRED_HOST_PORT,
                    message = "Required host input port '${port.id}' is not bound by any host input node.",
                    graphId = graphId,
                    pinId = port.id
                )
            }
        }

        val boundOutputPortIds = bindings.filter { it.kind == HostPortKind.OUTPUT }.map(HostPortBinding::portId).toSet()
        host.outputs.filter(GemHostPort::required).forEach { port ->
            if (port.id !in boundOutputPortIds) {
                errors += GemValidationError(
                    code = GemValidationErrorCode.MISSING_REQUIRED_HOST_PORT,
                    message = "Required host output port '${port.id}' is not bound by any host output node.",
                    graphId = graphId,
                    pinId = port.id
                )
            }
        }
    }

    private fun findCycleNodes(
        nodes: List<GemValidatedNode>,
        connections: List<GemValidatedConnection>
    ): List<String> {
        val indegree = nodes.associate { it.id to 0 }.toMutableMap()
        val adjacency = nodes.associate { it.id to mutableListOf<String>() }

        connections.forEach { connection ->
            adjacency.getValue(connection.fromNode.id) += connection.toNode.id
            indegree[connection.toNode.id] = indegree.getValue(connection.toNode.id) + 1
        }

        val comparator = compareBy<GemValidatedNode>({ it.index }, { it.id })
        val ready = nodes.filter { indegree.getValue(it.id) == 0 }.sortedWith(comparator).toMutableList()
        var visitedCount = 0

        while (ready.isNotEmpty()) {
            val node = ready.removeAt(0)
            visitedCount += 1
            adjacency.getValue(node.id).forEach { nextId ->
                val nextIndegree = indegree.getValue(nextId) - 1
                indegree[nextId] = nextIndegree
                if (nextIndegree == 0) {
                    val nextNode = nodes.first { it.id == nextId }
                    ready += nextNode
                    ready.sortWith(comparator)
                }
            }
        }

        if (visitedCount == nodes.size) {
            return emptyList()
        }

        return indegree
            .filterValues { it > 0 }
            .keys
            .sorted()
    }

    private fun topologicalOrder(
        nodes: List<GemValidatedNode>,
        connections: List<GemValidatedConnection>
    ): List<String> {
        val indegree = nodes.associate { it.id to 0 }.toMutableMap()
        val adjacency = nodes.associate { it.id to mutableListOf<String>() }

        connections.forEach { connection ->
            adjacency.getValue(connection.fromNode.id) += connection.toNode.id
            indegree[connection.toNode.id] = indegree.getValue(connection.toNode.id) + 1
        }

        val comparator = compareBy<GemValidatedNode>({ it.index }, { it.id })
        val ready = nodes.filter { indegree.getValue(it.id) == 0 }.sortedWith(comparator).toMutableList()
        val ordered = mutableListOf<String>()

        while (ready.isNotEmpty()) {
            val node = ready.removeAt(0)
            ordered += node.id
            adjacency.getValue(node.id).forEach { nextId ->
                val nextIndegree = indegree.getValue(nextId) - 1
                indegree[nextId] = nextIndegree
                if (nextIndegree == 0) {
                    val nextNode = nodes.first { it.id == nextId }
                    ready += nextNode
                    ready.sortWith(comparator)
                }
            }
        }

        return ordered
    }

    private data class InputTarget(
        val nodeId: String,
        val pinId: String
    )

    private data class HostPortBinding(
        val kind: HostPortKind,
        val portId: String,
        val nodeId: String
    )

    private data class SubgraphContract(
        val inputTargets: Set<InputTarget>
    )

    private enum class HostPortKind {
        INPUT,
        OUTPUT
    }

    private fun GemNodeInstance.hostPortId(): String? = serializedState[GemBuiltInNodes.HOST_PORT_ID_STATE_KEY]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

    private fun GemValue.matchesDeclaredType(type: GemValueType): Boolean = when {
        this is GemValue.Number && type == GemValueType.Number -> true
        this is GemValue.Boolean && type == GemValueType.Boolean -> true
        this is GemValue.Color && type == GemValueType.Color -> true
        this is GemValue.TimingValue && type == GemValueType.Timing -> true
        this is GemValue.Enum && type is GemValueType.Enum ->
            enumId == type.definition.id && type.definition.options.any { it.id == optionId }
        else -> false
    }

    private val HOST_INPUT_TYPES = mapOf(
        GemBuiltInNodes.TypeIds.HOST_LED_INPUT to GemSignalDomain.LED,
        GemBuiltInNodes.TypeIds.HOST_MIDI_INPUT to GemSignalDomain.MIDI
    )

    private val HOST_OUTPUT_TYPES = mapOf(
        GemBuiltInNodes.TypeIds.HOST_LED_OUTPUT to GemSignalDomain.LED,
        GemBuiltInNodes.TypeIds.HOST_MIDI_OUTPUT to GemSignalDomain.MIDI
    )
}


object LedGraphValidator {
    fun validateLedGraph(asset: GemAsset): List<GemValidationError> {
        val errors = mutableListOf<GemValidationError>()
        val rootGraph = asset.definition.rootGraph
        val ledInCount = rootGraph.nodes.count { it.type.typeId == GemBuiltInNodes.TypeIds.HOST_LED_INPUT }
        val ledOutCount = rootGraph.nodes.count { it.type.typeId == GemBuiltInNodes.TypeIds.HOST_LED_OUTPUT }
        when {
            ledInCount == 0 -> errors += GemValidationError(
                code = GemValidationErrorCode.LED_INPUT_MISSING,
                message = "Der Graph enthält keinen 'LED In'-Knoten. Genau ein Knoten vom Typ 'amethyst.host.led.in' ist erforderlich.",
                graphId = rootGraph.id
            )
            ledInCount > 1 -> errors += GemValidationError(
                code = GemValidationErrorCode.LED_INPUT_AMBIGUOUS,
                message = "Der Graph enthält $ledInCount 'LED In'-Knoten. Es ist genau einer erlaubt.",
                graphId = rootGraph.id
            )
        }
        when {
            ledOutCount == 0 -> errors += GemValidationError(
                code = GemValidationErrorCode.LED_OUTPUT_MISSING,
                message = "Der Graph enthält keinen 'LED Out'-Knoten. Genau ein Knoten vom Typ 'amethyst.host.led.out' ist erforderlich.",
                graphId = rootGraph.id
            )
            ledOutCount > 1 -> errors += GemValidationError(
                code = GemValidationErrorCode.LED_OUTPUT_AMBIGUOUS,
                message = "Der Graph enthält $ledOutCount 'LED Out'-Knoten. Es ist genau einer erlaubt.",
                graphId = rootGraph.id
            )
        }
        return errors
    }
}
