package dev.anthonyhfm.amethyst.gem

import dev.anthonyhfm.amethyst.core.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GemValidatorTest {
    @Test
    fun validDagProducesDeterministicExecutionOrderAndNormalizedGraph() {
        val graph = GemGraph(
            id = Gem.rootGraphId,
            kind = GemGraphKind.ROOT,
            nodes = listOf(
                GemBuiltInNodes.numberAdd.instantiate("delay"),
                GemBuiltInNodes.numberAdd.instantiate("scale"),
                GemBuiltInNodes.constantNumber.instantiate("value"),
                GemBuiltInNodes.constantNumber.instantiate("amount")
            ),
            connections = listOf(
                connection("value", "value", "scale", "a"),
                connection("amount", "value", "scale", "b"),
                connection("scale", "result", "delay", "a")
            )
        )

        val result = GemValidator.validate(asset(graph = graph))

        assertTrue(result.isValid)
        assertEquals(listOf("value", "amount", "scale", "delay"), result.graphs.single().executionOrder)
        assertEquals(
            GemBuiltInNodes.numberAdd.inputs.map { it.id },
            result.graphs.single().nodes.first { it.id == "scale" }.pins
                .filter { it.direction == GemPinDirection.INPUT }
                .map { it.id }
        )
    }

    @Test
    fun exposedInputBindingsSatisfyRequiredInputs() {
        val graph = GemGraph(
            id = Gem.rootGraphId,
            kind = GemGraphKind.ROOT,
            nodes = listOf(GemBuiltInNodes.numberAdd.instantiate("scale"))
        )
        val asset = asset(
            graph = graph,
            exposedParameters = listOf(
                GemExposedParameter(
                    id = "amount",
                    label = "Amount",
                    type = GemValueType.Number,
                    defaultValue = GemValue.Number(1.0),
                    bindings = listOf(
                        GemGraphBinding(
                            nodeId = "scale",
                            pinId = "a",
                            targetKind = GemBindingTargetKind.INPUT_PIN
                        )
                    )
                )
            )
        )

        val result = GemValidator.validate(asset)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun unknownNodeTypesAndUnsupportedVersionsAreRejected() {
        val graph = GemGraph(
            id = Gem.rootGraphId,
            kind = GemGraphKind.ROOT,
            nodes = listOf(
                GemNodeInstance(
                    id = "unknown",
                    type = GemNodeTypeId(typeId = "amethyst.unknown.node", version = Gem.phase1SchemaVersion)
                ),
                GemBuiltInNodes.numberAdd.instantiate("wrong-version").copy(
                    type = GemNodeTypeId(
                        typeId = GemBuiltInNodes.TypeIds.NUMBER_ADD,
                        version = Version(9, 9, 9)
                    )
                )
            )
        )

        val result = GemValidator.validate(asset(graph = graph))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.code == GemValidationErrorCode.UNKNOWN_NODE_TYPE && it.nodeId == "unknown" })
        assertTrue(result.errors.any { it.code == GemValidationErrorCode.UNSUPPORTED_NODE_VERSION && it.nodeId == "wrong-version" })
    }

    @Test
    fun invalidConnectionsAndMissingInputsProduceStableErrors() {
        val graph = GemGraph(
            id = Gem.rootGraphId,
            kind = GemGraphKind.ROOT,
            nodes = listOf(
                GemBuiltInNodes.constantBoolean.instantiate("boolean"),
                GemBuiltInNodes.constantNumber.instantiate("number-a"),
                GemBuiltInNodes.constantNumber.instantiate("number-b"),
                GemBuiltInNodes.numberAdd.instantiate("scale"),
                GemBuiltInNodes.ledUnpack.instantiate("lonely")
            ),
            connections = listOf(
                connection("boolean", "value", "scale", "a"),
                connection("number-a", "value", "scale", "b"),
                connection("number-b", "value", "scale", "b"),
                GemConnection(
                    id = "missing-node",
                    from = GemPinRef(nodeId = "ghost", pinId = "value"),
                    to = GemPinRef(nodeId = "scale", pinId = "bias")
                ),
                GemConnection(
                    id = "missing-pin",
                    from = GemPinRef(nodeId = "number-a", pinId = "ghost"),
                    to = GemPinRef(nodeId = "scale", pinId = "bias")
                )
            )
        )

        val result = GemValidator.validate(asset(graph = graph))
        val codes = result.errors.map { it.code }.toSet()

        assertFalse(result.isValid)
        assertTrue(GemValidationErrorCode.INCOMPATIBLE_PIN_TYPES in codes)
        assertTrue(GemValidationErrorCode.MULTIPLE_INPUT_CONNECTIONS in codes)
        assertTrue(GemValidationErrorCode.MISSING_CONNECTION_NODE in codes)
        assertTrue(GemValidationErrorCode.MISSING_CONNECTION_PIN in codes)
        assertTrue(result.errors.any {
            it.code == GemValidationErrorCode.MISSING_REQUIRED_INPUT &&
                it.nodeId == "lonely" &&
                it.pinId == "signal"
        })
    }

    @Test
    fun hostMismatchesCyclesAndInvalidBindingsAreRejected() {
        val graph = GemGraph(
            id = Gem.rootGraphId,
            kind = GemGraphKind.ROOT,
            nodes = listOf(
                GemBuiltInNodes.numberAdd.instantiate("scale"),
                GemBuiltInNodes.numberAdd.instantiate("delay")
            ),
            connections = listOf(
                connection("scale", "result", "delay", "a"),
                connection("delay", "result", "scale", "a")
            )
        )
        val asset = asset(
            graph = graph,
            host = GemHostIoContract(
                assetShape = GemHostAssetShape.SOURCE,
                supportedDomains = listOf(GemSignalDomain.LED),
                outputs = listOf(GemHostPort(id = "led-out", domain = GemSignalDomain.LED, required = true))
            ),
            exposedParameters = listOf(
                GemExposedParameter(
                    id = "toggle",
                    type = GemValueType.Boolean,
                    defaultValue = GemValue.Boolean(false),
                    bindings = listOf(
                        GemGraphBinding(
                            nodeId = "scale",
                            pinId = "a",
                            targetKind = GemBindingTargetKind.INPUT_PIN
                        )
                    )
                )
            )
        )

        val result = GemValidator.validate(asset)
        val codes = result.errors.map { it.code }.toSet()

        assertFalse(result.isValid)
        assertTrue(GemValidationErrorCode.INCOMPATIBLE_BINDING_TYPE in codes)
        assertTrue(GemValidationErrorCode.GRAPH_CYCLE in codes)
    }

    @Test
    fun duplicateParameterIdsAndInvalidDefaultsAreRejected() {
        val asset = asset(
            graph = GemGraph(
                id = Gem.rootGraphId,
                kind = GemGraphKind.ROOT,
                nodes = listOf(GemBuiltInNodes.numberAdd.instantiate("scale"))
            ),
            exposedParameters = listOf(
                GemExposedParameter(
                    id = "amount",
                    label = "Amount A",
                    type = GemValueType.Number,
                    defaultValue = GemValue.Number(1.0),
                    bindings = listOf(
                        GemGraphBinding(
                            nodeId = "scale",
                            pinId = "a",
                            targetKind = GemBindingTargetKind.INPUT_PIN
                        )
                    )
                ),
                GemExposedParameter(
                    id = "amount",
                    label = "Amount B",
                    type = GemValueType.Number,
                    defaultValue = GemValue.Boolean(false)
                )
            )
        ).copy(
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(GemBuiltInNodes.numberAdd.instantiate("scale"))
                ),
                host = GemHostIoContract(
                    assetShape = GemHostAssetShape.PROCESSOR,
                    supportedDomains = listOf(GemSignalDomain.LED),
                    inputs = listOf(GemHostPort(id = "led-in", domain = GemSignalDomain.LED, required = true)),
                    outputs = listOf(GemHostPort(id = "led-out", domain = GemSignalDomain.LED, required = true))
                ),
                exposedParameters = listOf(
                    GemExposedParameter(
                        id = "amount",
                        label = "Amount A",
                        type = GemValueType.Number,
                        defaultValue = GemValue.Number(1.0),
                        bindings = listOf(
                            GemGraphBinding(
                                nodeId = "scale",
                                pinId = "a",
                                targetKind = GemBindingTargetKind.INPUT_PIN
                            )
                        )
                    ),
                    GemExposedParameter(
                        id = "amount",
                        label = "Amount B",
                        type = GemValueType.Number,
                        defaultValue = GemValue.Boolean(false)
                    )
                ),
                defaultState = GemDefaultState(
                    exposedParameterValues = mapOf(
                        "amount" to GemValue.Boolean(true),
                        "missing" to GemValue.Number(4.0)
                    )
                )
            )
        )

        val result = GemValidator.validate(asset)
        val codes = result.errors.map { it.code }.toSet()

        assertFalse(result.isValid)
        assertTrue(GemValidationErrorCode.DUPLICATE_EXPOSED_PARAMETER_ID in codes)
        assertTrue(GemValidationErrorCode.INVALID_EXPOSED_PARAMETER_DEFAULT in codes)
        assertTrue(GemValidationErrorCode.INVALID_DEFAULT_STATE_PARAMETER in codes)
        assertTrue(GemValidationErrorCode.UNKNOWN_DEFAULT_STATE_PARAMETER in codes)
    }

    @Test
    fun requiredHostPortsMustBindByPortIdExactlyOnce() {
        val graph = GemGraph(
            id = Gem.rootGraphId,
            kind = GemGraphKind.ROOT,
            nodes = listOf(
                GemBuiltInNodes.hostLedInput.instantiate(
                    "input-a",
                    serializedState = GemBuiltInNodes.hostPortState("led-in")
                ),
                GemBuiltInNodes.hostLedInput.instantiate(
                    "input-b",
                    serializedState = GemBuiltInNodes.hostPortState("led-in")
                )
            )
        )

        val result = GemValidator.validate(
            asset(
                graph = graph,
                host = GemHostIoContract(
                    assetShape = GemHostAssetShape.PROCESSOR,
                    supportedDomains = listOf(GemSignalDomain.LED),
                    inputs = listOf(GemHostPort(id = "led-in", domain = GemSignalDomain.LED, required = true)),
                    outputs = listOf(GemHostPort(id = "led-out", domain = GemSignalDomain.LED, required = true))
                )
            )
        )
        val codes = result.errors.map { it.code }.toSet()

        assertFalse(result.isValid)
        assertTrue(GemValidationErrorCode.DUPLICATE_HOST_PORT_BINDING in codes)
        assertTrue(GemValidationErrorCode.MISSING_REQUIRED_HOST_PORT in codes)
    }

    @Test
    fun enumDefaultsMustUseDeclaredOptions() {
        val modeEnum = GemValueType.Enum(
            GemEnumDefinition(
                id = "mode",
                options = listOf(
                    GemEnumOption(id = "pulse"),
                    GemEnumOption(id = "hold")
                )
            )
        )
        val asset = asset(
            graph = GemGraph(
                id = Gem.rootGraphId,
                kind = GemGraphKind.ROOT
            ),
            exposedParameters = listOf(
                GemExposedParameter(
                    id = "mode",
                    label = "Mode",
                    type = modeEnum,
                    defaultValue = GemValue.Enum(enumId = "mode", optionId = "missing")
                )
            )
        )

        val result = GemValidator.validate(asset)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.code == GemValidationErrorCode.INVALID_EXPOSED_PARAMETER_DEFAULT })
    }

    @Test
    fun recursiveSubgraphsAndNestedHostNodesAreRejected() {
        val subgraphASeed = GemGraph(
            id = "sub-a",
            kind = GemGraphKind.SUBGRAPH,
            subgraphInterface = GemSubgraphInterface()
        )
        val subgraphBSeed = GemGraph(
            id = "sub-b",
            kind = GemGraphKind.SUBGRAPH,
            subgraphInterface = GemSubgraphInterface()
        )
        val subgraphA = subgraphASeed.copy(
            nodes = listOf(GemBuiltInNodes.instantiateSubgraphCall(nodeId = "call-b", subgraph = subgraphBSeed))
        )
        val subgraphB = subgraphBSeed.copy(
            nodes = listOf(
                GemBuiltInNodes.instantiateSubgraphCall(nodeId = "call-a", subgraph = subgraphASeed),
                GemBuiltInNodes.hostLedInput.instantiate(
                    nodeId = "host-in",
                    serializedState = GemBuiltInNodes.hostPortState("led-in")
                )
            )
        )
        val asset = GemAsset(
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT
                ),
                subgraphs = listOf(subgraphA, subgraphB),
                host = GemHostIoContract(
                    assetShape = GemHostAssetShape.PROCESSOR,
                    supportedDomains = listOf(GemSignalDomain.LED),
                    inputs = listOf(GemHostPort(id = "led-in", domain = GemSignalDomain.LED))
                )
            )
        )

        val result = GemValidator.validate(asset)
        val codes = result.errors.map { it.code }.toSet()

        assertFalse(result.isValid)
        assertTrue(GemValidationErrorCode.SUBGRAPH_RECURSION in codes)
        assertTrue(GemValidationErrorCode.HOST_NODE_OUTSIDE_ROOT in codes)
    }

    private fun asset(
        graph: GemGraph,
        host: GemHostIoContract = GemHostIoContract(
            assetShape = GemHostAssetShape.PROCESSOR,
            supportedDomains = listOf(GemSignalDomain.LED)
        ),
        exposedParameters: List<GemExposedParameter> = emptyList()
    ): GemAsset = GemAsset(
        definition = GemDefinition(
            rootGraph = graph,
            host = host,
            exposedParameters = exposedParameters
        )
    )

    private fun connection(
        fromNodeId: String,
        fromPinId: String,
        toNodeId: String,
        toPinId: String
    ): GemConnection = GemConnection(
        id = "$fromNodeId-$fromPinId-$toNodeId-$toPinId",
        from = GemPinRef(nodeId = fromNodeId, pinId = fromPinId),
        to = GemPinRef(nodeId = toNodeId, pinId = toPinId)
    )
}
