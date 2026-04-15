package dev.anthonyhfm.amethyst.gem.runtime

import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemBindingTargetKind
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemGraphBinding
import dev.anthonyhfm.amethyst.gem.GemNodeRegistry
import dev.anthonyhfm.amethyst.gem.GemValidatedGraph
import dev.anthonyhfm.amethyst.gem.GemValidatedNode
import dev.anthonyhfm.amethyst.gem.GemValidationResult
import dev.anthonyhfm.amethyst.gem.GemValidator
import dev.anthonyhfm.amethyst.gem.data.GemJsonPersistence
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

object GemCompiler {
    fun compile(
        asset: GemAsset,
        registry: GemNodeRegistry = GemNodeRegistry.builtIns
    ): GemCompilationResult {
        val validation = GemValidator.validate(asset, registry)
        return compile(asset = asset, validation = validation)
    }

    fun compile(
        asset: GemAsset,
        validation: GemValidationResult
    ): GemCompilationResult {
        val diagnostics = mutableListOf<GemRuntimeDiagnostic>()
        if (!validation.isValid) {
            diagnostics += validation.errors.map { error ->
                GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.COMPILE,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.VALIDATION_ERROR,
                    message = error.message,
                    graphId = error.graphId,
                    nodeId = error.nodeId,
                    pinId = error.pinId,
                    parameterId = error.exposedParameterId
                )
            }
            return GemCompilationResult(plan = null, diagnostics = diagnostics)
        }

        val inputBindings = mutableMapOf<BindingTarget, String>()
        val outputBindings = mutableMapOf<BindingTarget, String>()
        asset.definition.exposedParameters.forEach { parameter ->
            parameter.bindings.forEach { binding ->
                when (binding.targetKind) {
                    GemBindingTargetKind.INPUT_PIN -> registerBinding(
                        target = binding.toBindingTarget(),
                        parameterId = parameter.id,
                        bindings = inputBindings,
                        diagnostics = diagnostics,
                        description = "input"
                    )

                    GemBindingTargetKind.OUTPUT_PIN -> registerBinding(
                        target = binding.toBindingTarget(),
                        parameterId = parameter.id,
                        bindings = outputBindings,
                        diagnostics = diagnostics,
                        description = "output"
                    )
                }
            }
        }

        val graphPlans = validation.graphs.map { graph ->
            compileGraph(
                validatedGraph = graph,
                inputBindings = inputBindings,
                outputBindings = outputBindings,
                diagnostics = diagnostics
            )
        }

        if (diagnostics.any { it.severity == GemRuntimeSeverity.ERROR }) {
            return GemCompilationResult(plan = null, diagnostics = diagnostics)
        }

        val exposedParameters = compileExposedParameters(asset, diagnostics)
        if (diagnostics.any { it.severity == GemRuntimeSeverity.ERROR }) {
            return GemCompilationResult(plan = null, diagnostics = diagnostics)
        }

        val plan = GemExecutionPlan(
            assetId = asset.metadata.id,
            assetName = asset.metadata.name,
            schemaVersion = asset.schemaVersion,
            rootGraphId = asset.definition.rootGraph.id,
            exposedParameters = exposedParameters,
            graphPlans = graphPlans,
            diagnostics = diagnostics.toList()
        )
        return GemCompilationResult(plan = plan, diagnostics = diagnostics.toList())
    }

    private fun compileGraph(
        validatedGraph: GemValidatedGraph,
        inputBindings: Map<BindingTarget, String>,
        outputBindings: Map<BindingTarget, String>,
        diagnostics: MutableList<GemRuntimeDiagnostic>
    ): GemExecutionGraphPlan {
        val graphInputBindings = validatedGraph.graph.subgraphInterface.inputs
            .flatMap { port ->
                port.bindings.map { binding ->
                    BindingTarget(
                        graphId = validatedGraph.graph.id,
                        nodeId = binding.nodeId,
                        pinId = binding.pinId
                    ) to port.id
                }
            }
            .toMap()
        val graphInputs = validatedGraph.graph.subgraphInterface.inputs.map { port ->
            GemExecutionGraphInput(
                portId = port.id,
                label = port.label,
                type = port.type,
                required = port.required,
                defaultValue = port.defaultValue
            )
        }
        val graphOutputs = validatedGraph.graph.subgraphInterface.outputs.map { port ->
            GemExecutionGraphOutput(
                portId = port.id,
                label = port.label,
                type = port.type
            )
        }
        val nodeById = validatedGraph.nodes.associateBy(GemValidatedNode::id)
        val connectionsByInput = validatedGraph.connections.associateBy { connection ->
            BindingTarget(
                graphId = validatedGraph.graph.id,
                nodeId = connection.toNode.id,
                pinId = connection.toPin.id
            )
        }
        val nodePlans = validatedGraph.executionOrder.mapNotNull { nodeId ->
            val node = nodeById[nodeId] ?: return@mapNotNull null
            val inputs = node.descriptor.inputs.map { pin ->
                val target = BindingTarget(
                    graphId = validatedGraph.graph.id,
                    nodeId = node.id,
                    pinId = pin.id
                )
                val connection = connectionsByInput[target]
                val exposedParameterId = inputBindings[target]
                val graphInputPortId = graphInputBindings[target]
                val sourceCount = listOfNotNull(connection, exposedParameterId, graphInputPortId).size
                if (sourceCount > 1) {
                    diagnostics += GemRuntimeDiagnostic(
                        phase = GemRuntimePhase.COMPILE,
                        severity = GemRuntimeSeverity.ERROR,
                        code = GemRuntimeDiagnosticCode.MULTIPLE_INPUT_SOURCES,
                        message = "Input '${pin.id}' on node '${node.id}' cannot be fed by multiple sources.",
                        graphId = validatedGraph.graph.id,
                        nodeId = node.id,
                        pinId = pin.id,
                        parameterId = exposedParameterId
                    )
                }
                GemExecutionInputBinding(
                    pinId = pin.id,
                    type = pin.type,
                    source = when {
                        connection != null -> GemExecutionInputSource.Connection(
                            fromNodeId = connection.fromNode.id,
                            fromPinId = connection.fromPin.id
                        )

                        exposedParameterId != null -> GemExecutionInputSource.ExposedParameter(exposedParameterId)
                        graphInputPortId != null -> GemExecutionInputSource.GraphInput(graphInputPortId)
                        pin.defaultValue != null -> GemExecutionInputSource.DefaultValue(pin.defaultValue)
                        else -> GemExecutionInputSource.Unresolved
                    }
                )
            }
            val outputs = node.descriptor.outputs.map { pin ->
                GemExecutionOutputBinding(pinId = pin.id, type = pin.type)
            }
            val state = compileNodeState(validatedGraph.graph.id, node, diagnostics)
            val outputParameterBindingsForNode = linkedMapOf<String, String>()
            val graphOutputBindingsForNode = linkedMapOf<String, String>()
            outputs.forEach { output ->
                val target = BindingTarget(validatedGraph.graph.id, node.id, output.pinId)
                val parameterId = outputBindings[target]
                if (parameterId != null) {
                    outputParameterBindingsForNode[output.pinId] = parameterId
                }
                validatedGraph.graph.subgraphInterface.outputs
                    .firstOrNull { it.binding == dev.anthonyhfm.amethyst.gem.GemPinRef(node.id, output.pinId) }
                    ?.let { graphOutputBindingsForNode[output.pinId] = it.id }
            }
            GemExecutionNodePlan(
                nodeId = node.id,
                label = node.instance.label,
                type = node.instance.type,
                inputs = inputs,
                outputs = outputs,
                state = state,
                hostPortId = node.instance.hostPortId(),
                outputParameterBindings = outputParameterBindingsForNode,
                graphOutputBindings = graphOutputBindingsForNode,
                subgraphId = node.instance.subgraphId
            )
        }
        return GemExecutionGraphPlan(
            graphId = validatedGraph.graph.id,
            kind = validatedGraph.graph.kind,
            executionOrder = validatedGraph.executionOrder,
            nodePlans = nodePlans,
            inputs = graphInputs,
            outputs = graphOutputs
        )
    }

    private fun compileNodeState(
        graphId: String,
        node: GemValidatedNode,
        diagnostics: MutableList<GemRuntimeDiagnostic>
    ): Map<String, dev.anthonyhfm.amethyst.gem.GemValue> {
        val resolvedState = linkedMapOf<String, dev.anthonyhfm.amethyst.gem.GemValue>()
        node.descriptor.state.forEach { field ->
            val serialized = node.instance.serializedState[field.id]
            val value = when {
                serialized != null -> decodeStateValue(
                    graphId = graphId,
                    node = node,
                    fieldId = field.id,
                    expectedType = field.type,
                    diagnostics = diagnostics,
                    serialized = serialized
                )

                field.defaultValue != null -> field.defaultValue
                else -> {
                    if (field.required) {
                        diagnostics += GemRuntimeDiagnostic(
                            phase = GemRuntimePhase.COMPILE,
                            severity = GemRuntimeSeverity.ERROR,
                            code = GemRuntimeDiagnosticCode.MISSING_STATE_VALUE,
                            message = "Node '${node.id}' is missing required state '${field.id}'.",
                            graphId = graphId,
                            nodeId = node.id,
                            pinId = field.id
                        )
                    }
                    null
                }
            }
            if (value != null) {
                resolvedState[field.id] = value
            }
        }
        return resolvedState
    }

    private fun decodeStateValue(
        graphId: String,
        node: GemValidatedNode,
        fieldId: String,
        expectedType: dev.anthonyhfm.amethyst.gem.GemValueType,
        diagnostics: MutableList<GemRuntimeDiagnostic>,
        serialized: kotlinx.serialization.json.JsonElement
    ): dev.anthonyhfm.amethyst.gem.GemValue? = try {
        val decoded = GemJsonPersistence.json.decodeFromJsonElement<dev.anthonyhfm.amethyst.gem.GemValue>(serialized)
        if (!decoded.matches(expectedType)) {
            diagnostics += GemRuntimeDiagnostic(
                phase = GemRuntimePhase.COMPILE,
                severity = GemRuntimeSeverity.ERROR,
                code = GemRuntimeDiagnosticCode.INVALID_STATE_VALUE,
                message = "State '${fieldId}' on node '${node.id}' does not match the expected type.",
                graphId = graphId,
                nodeId = node.id,
                pinId = fieldId
            )
            null
        } else {
            decoded
        }
    } catch (_: SerializationException) {
        diagnostics += GemRuntimeDiagnostic(
            phase = GemRuntimePhase.COMPILE,
            severity = GemRuntimeSeverity.ERROR,
            code = GemRuntimeDiagnosticCode.INVALID_STATE_VALUE,
            message = "State '${fieldId}' on node '${node.id}' could not be decoded.",
            graphId = graphId,
            nodeId = node.id,
            pinId = fieldId
        )
        null
    } catch (_: IllegalArgumentException) {
        diagnostics += GemRuntimeDiagnostic(
            phase = GemRuntimePhase.COMPILE,
            severity = GemRuntimeSeverity.ERROR,
            code = GemRuntimeDiagnosticCode.INVALID_STATE_VALUE,
            message = "State '${fieldId}' on node '${node.id}' could not be decoded.",
            graphId = graphId,
            nodeId = node.id,
            pinId = fieldId
        )
        null
    }

    private fun registerBinding(
        target: BindingTarget,
        parameterId: String,
        bindings: MutableMap<BindingTarget, String>,
        diagnostics: MutableList<GemRuntimeDiagnostic>,
        description: String
    ) {
        val existing = bindings[target]
        if (existing != null && existing != parameterId) {
            diagnostics += GemRuntimeDiagnostic(
                phase = GemRuntimePhase.COMPILE,
                severity = GemRuntimeSeverity.ERROR,
                code = GemRuntimeDiagnosticCode.AMBIGUOUS_PARAMETER_BINDING,
                message = "Graph ${description} '${target.pinId}' on node '${target.nodeId}' is already bound to exposed parameter '${existing}', so '${parameterId}' cannot also bind it.",
                graphId = target.graphId,
                nodeId = target.nodeId,
                pinId = target.pinId,
                parameterId = parameterId
            )
            return
        }
        bindings[target] = parameterId
    }

    private fun compileExposedParameters(
        asset: GemAsset,
        diagnostics: MutableList<GemRuntimeDiagnostic>
    ): List<GemExecutionParameter> {
        val compiled = mutableListOf<GemExecutionParameter>()
        val seenIds = mutableSetOf<String>()

        asset.definition.exposedParameters.forEach { parameter ->
            if (!seenIds.add(parameter.id)) {
                diagnostics += GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.COMPILE,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.DUPLICATE_PARAMETER_ID,
                    message = "Exposed parameter '${parameter.id}' is declared more than once.",
                    parameterId = parameter.id
                )
                return@forEach
            }

            val defaultValue = asset.definition.defaultState.exposedParameterValues[parameter.id] ?: parameter.defaultValue
            if (!defaultValue.matches(parameter.type)) {
                diagnostics += GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.COMPILE,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.INVALID_PARAMETER_DEFAULT_VALUE,
                    message = "Exposed parameter '${parameter.id}' has a default value that does not match its declared type.",
                    parameterId = parameter.id
                )
                return@forEach
            }

            compiled += GemExecutionParameter(
                id = parameter.id,
                label = parameter.label,
                type = parameter.type,
                defaultValue = defaultValue
            )
        }

        asset.definition.defaultState.exposedParameterValues.keys
            .filter { it !in seenIds }
            .sorted()
            .forEach { parameterId ->
                diagnostics += GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.COMPILE,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.INVALID_PARAMETER_DEFAULT_VALUE,
                    message = "Default state references unknown exposed parameter '$parameterId'.",
                    parameterId = parameterId
                )
            }

        return compiled
    }

    private fun GemGraphBinding.toBindingTarget(): BindingTarget = BindingTarget(
        graphId = graphId,
        nodeId = nodeId,
        pinId = pinId
    )

    private fun dev.anthonyhfm.amethyst.gem.GemNodeInstance.hostPortId(): String? {
        if (type.typeId !in HOST_NODE_TYPES) {
            return null
        }

        return serializedState[GemBuiltInNodes.HOST_PORT_ID_STATE_KEY]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }

    private val HOST_NODE_TYPES = setOf(
        GemBuiltInNodes.TypeIds.HOST_LED_INPUT,
        GemBuiltInNodes.TypeIds.HOST_MIDI_INPUT,
        GemBuiltInNodes.TypeIds.HOST_LED_OUTPUT,
        GemBuiltInNodes.TypeIds.HOST_MIDI_OUTPUT
    )

    private data class BindingTarget(
        val graphId: String,
        val nodeId: String,
        val pinId: String
    )
}
