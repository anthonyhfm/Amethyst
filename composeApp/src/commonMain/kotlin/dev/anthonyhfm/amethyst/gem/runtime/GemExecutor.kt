package dev.anthonyhfm.amethyst.gem.runtime

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemNodeRegistry
import dev.anthonyhfm.amethyst.gem.GemPinType
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.LedGraphValidator
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinitionRegistry
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import dev.anthonyhfm.amethyst.gem.node.SubgraphCallResult

object GemExecutor {
    fun preview(
        plan: GemExecutionPlan,
        context: GemPreviewContext = GemPreviewContext()
    ): GemExecutionResult {
        val diagnostics = plan.diagnostics.toMutableList()
        val statuses = mutableListOf<GemRuntimeStatus>()
        val resolvedParameters = linkedMapOf<String, GemValue>()
        plan.exposedParameters.forEach { parameter ->
            if (parameter.id in resolvedParameters) {
                diagnostics += GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.EXECUTION,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.DUPLICATE_PARAMETER_ID,
                    message = "Execution plan contains duplicate exposed parameter '${parameter.id}'.",
                    parameterId = parameter.id
                )
                return@forEach
            }

            val value = context.parameterValues[parameter.id] ?: parameter.defaultValue
            if (!value.matches(parameter.type)) {
                diagnostics += GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.EXECUTION,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.INVALID_PARAMETER_DEFAULT_VALUE,
                    message = "Execution value for exposed parameter '${parameter.id}' does not match its declared type.",
                    parameterId = parameter.id
                )
                return@forEach
            }

            resolvedParameters[parameter.id] = value
        }

        val rootGraphPlan = plan.graph(plan.rootGraphId)
        if (rootGraphPlan == null) {
            diagnostics += GemRuntimeDiagnostic(
                phase = GemRuntimePhase.EXECUTION,
                severity = GemRuntimeSeverity.ERROR,
                code = GemRuntimeDiagnosticCode.SUBGRAPH_PLAN_MISSING,
                message = "Execution plan is missing the root graph '${plan.rootGraphId}'.",
                graphId = plan.rootGraphId
            )
            return GemExecutionResult(
                plan = plan,
                graphResults = emptyList(),
                diagnostics = diagnostics.toList(),
                statuses = emptyList(),
                runtimeState = context.runtimeState
            )
        }

        val hostOutputs = linkedMapOf<String, GemSignalBatch>()
        val heldValues = context.runtimeState.heldValues.toMutableMap()
        val evaluation = executeGraph(
            plan = plan,
            graphPlan = rootGraphPlan,
            scopeId = rootGraphPlan.graphId,
            context = context,
            parameters = resolvedParameters,
            externalInputs = emptyMap(),
            heldValues = heldValues,
            hostOutputs = hostOutputs
        )
        diagnostics += evaluation.diagnostics
        statuses += evaluation.statuses

        return GemExecutionResult(
            plan = plan,
            graphResults = listOf(evaluation.result) + evaluation.nestedGraphResults,
            diagnostics = diagnostics.toList(),
            hostOutputs = hostOutputs.toMap(),
            statuses = statuses.toList(),
            runtimeState = GemRuntimeState(heldValues = heldValues.toMap())
        )
    }

    fun execute(
        asset: GemAsset,
        ledInputSignals: List<Signal>,
        registry: GemNodeRegistry = GemNodeRegistry.builtIns
    ): GemExecutionResult? {
        val compilation = GemCompiler.compile(asset, registry)
        val plan = compilation.plan ?: return null

        val ledErrors = LedGraphValidator.validateLedGraph(asset)
        if (ledErrors.isNotEmpty()) {
            val ledDiagnostics = ledErrors.map { error ->
                GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.COMPILE,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.VALIDATION_ERROR,
                    message = error.message,
                    graphId = error.graphId
                )
            }
            return GemExecutionResult(
                plan = plan,
                graphResults = emptyList(),
                diagnostics = plan.diagnostics + ledDiagnostics,
                statuses = emptyList()
            )
        }

        val rootGraphPlan = plan.graph(plan.rootGraphId)
        val ledInNode = rootGraphPlan?.nodePlans?.firstOrNull {
            it.type.typeId == GemBuiltInNodes.TypeIds.HOST_LED_INPUT
        }
        val ledInPortId = ledInNode?.hostPortId ?: ledInNode?.nodeId ?: return null

        val context = GemPreviewContext(
            hostInputs = mapOf(
                ledInPortId to GemSignalBatch(
                    domain = GemSignalDomain.LED,
                    signals = ledInputSignals.filterIsInstance<Signal.LED>()
                )
            )
        )

        return preview(plan, context)
    }

    private fun executeGraph(
        plan: GemExecutionPlan,
        graphPlan: GemExecutionGraphPlan,
        scopeId: String,
        context: GemPreviewContext,
        parameters: Map<String, GemValue>,
        externalInputs: Map<String, GemRuntimeDatum>,
        heldValues: MutableMap<String, GemValue>,
        hostOutputs: MutableMap<String, GemSignalBatch>
    ): GraphEvaluation {
        val graphDiagnostics = mutableListOf<GemRuntimeDiagnostic>()
        val graphStatuses = mutableListOf<GemRuntimeStatus>()
        val nestedGraphResults = mutableListOf<GemGraphExecutionResult>()
        val producedOutputs = linkedMapOf<GemNodeOutputRef, GemRuntimeDatum>()
        val graphOutputs = linkedMapOf<String, GemRuntimeDatum>()
        val nodeResults = mutableListOf<GemNodeExecutionResult>()
        val scheduledValues = mutableListOf<GemScheduledPreviewValue>()

        graphPlan.nodePlans.forEach { nodePlan ->
            val resolvedInputs = linkedMapOf<String, GemRuntimeDatum?>()
            nodePlan.inputs.forEach { input ->
                resolvedInputs[input.pinId] = resolveInput(
                    graphPlan = graphPlan,
                    nodePlan = nodePlan,
                    input = input,
                    producedOutputs = producedOutputs,
                    parameters = parameters,
                    externalInputs = externalInputs,
                    diagnostics = graphDiagnostics
                )
            }

            val evaluation = evaluateNode(
                plan = plan,
                graphPlan = graphPlan,
                scopeId = scopeId,
                nodePlan = nodePlan,
                context = context,
                parameters = parameters,
                inputs = resolvedInputs,
                heldValues = heldValues,
                hostOutputs = hostOutputs
            )
            graphDiagnostics += evaluation.diagnostics
            graphStatuses += evaluation.statuses
            nestedGraphResults += evaluation.nestedGraphResults
            producedOutputs.putAll(evaluation.outputs.mapKeys { GemNodeOutputRef(nodePlan.nodeId, it.key) })
            scheduledValues += evaluation.scheduledValues
            evaluation.outputs.forEach { (pinId, datum) ->
                nodePlan.graphOutputBindings[pinId]?.let { graphOutputs[it] = datum }
            }
            nodeResults += GemNodeExecutionResult(
                nodeId = nodePlan.nodeId,
                outputs = evaluation.outputs,
                scheduledValues = evaluation.scheduledValues,
                statuses = evaluation.statuses
            )
        }

        graphPlan.outputs.forEach { output ->
            if (output.portId !in graphOutputs) {
                graphDiagnostics += GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.EXECUTION,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.SUBGRAPH_OUTPUT_UNRESOLVED,
                    message = "Graph '${graphPlan.graphId}' could not resolve interface output '${output.portId}'.",
                    graphId = graphPlan.graphId,
                    pinId = output.portId
                )
            }
        }

        val result = GemGraphExecutionResult(
            graphId = graphPlan.graphId,
            nodeResults = nodeResults,
            outputs = graphOutputs.toMap(),
            scheduledValues = scheduledValues.toList(),
            diagnostics = graphDiagnostics.toList(),
            statuses = graphStatuses.toList()
        )
        return GraphEvaluation(
            result = result,
            nestedGraphResults = nestedGraphResults.toList(),
            outputs = graphOutputs.toMap(),
            scheduledValues = scheduledValues.toList() + nestedGraphResults.flatMap(GemGraphExecutionResult::scheduledValues),
            diagnostics = result.diagnostics + nestedGraphResults.flatMap(GemGraphExecutionResult::diagnostics),
            statuses = result.statuses + nestedGraphResults.flatMap(GemGraphExecutionResult::statuses)
        )
    }

    private fun resolveInput(
        graphPlan: GemExecutionGraphPlan,
        nodePlan: GemExecutionNodePlan,
        input: GemExecutionInputBinding,
        producedOutputs: Map<GemNodeOutputRef, GemRuntimeDatum>,
        parameters: Map<String, GemValue>,
        externalInputs: Map<String, GemRuntimeDatum>,
        diagnostics: MutableList<GemRuntimeDiagnostic>
    ): GemRuntimeDatum? = when (val source = input.source) {
        is GemExecutionInputSource.Connection -> {
            val datum = producedOutputs[GemNodeOutputRef(source.fromNodeId, source.fromPinId)]
            if (datum == null) {
                diagnostics += GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.EXECUTION,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.INPUT_VALUE_UNRESOLVED,
                    message = "Input '${input.pinId}' on node '${nodePlan.nodeId}' could not resolve upstream output '${source.fromNodeId}.${source.fromPinId}'.",
                    graphId = graphPlan.graphId,
                    nodeId = nodePlan.nodeId,
                    pinId = input.pinId
                )
            }
            datum
        }

        is GemExecutionInputSource.DefaultValue -> GemRuntimeDatum.Value(source.value)
        is GemExecutionInputSource.ExposedParameter -> parameters[source.parameterId]?.let(GemRuntimeDatum::Value) ?: run {
            diagnostics += GemRuntimeDiagnostic(
                phase = GemRuntimePhase.EXECUTION,
                severity = GemRuntimeSeverity.ERROR,
                code = GemRuntimeDiagnosticCode.MISSING_PARAMETER_VALUE,
                message = "Exposed parameter '${source.parameterId}' has no runtime value for input '${input.pinId}' on node '${nodePlan.nodeId}'.",
                graphId = graphPlan.graphId,
                nodeId = nodePlan.nodeId,
                pinId = input.pinId,
                parameterId = source.parameterId
            )
            null
        }

        is GemExecutionInputSource.GraphInput -> externalInputs[source.portId]
            ?: graphPlan.input(source.portId)?.defaultValue?.let(GemRuntimeDatum::Value)

        GemExecutionInputSource.Unresolved -> null
    }

    private fun evaluateNode(
        plan: GemExecutionPlan,
        graphPlan: GemExecutionGraphPlan,
        scopeId: String,
        nodePlan: GemExecutionNodePlan,
        context: GemPreviewContext,
        parameters: Map<String, GemValue>,
        inputs: Map<String, GemRuntimeDatum?>,
        heldValues: MutableMap<String, GemValue>,
        hostOutputs: MutableMap<String, GemSignalBatch>
    ): NodeEvaluation {
        val diagnostics = mutableListOf<GemRuntimeDiagnostic>()
        val scheduledValues = mutableListOf<GemScheduledPreviewValue>()
        val statuses = mutableListOf<GemRuntimeStatus>()
        val nestedGraphResults = mutableListOf<GemGraphExecutionResult>()
        val outputs = linkedMapOf<String, GemRuntimeDatum>()

        val nodeContext = GemNodeExecutionContext(
            graphId = graphPlan.graphId,
            nodePlan = nodePlan,
            resolvedInputs = inputs,
            hostInputs = context.hostInputs,
            outputs = outputs,
            hostOutputs = hostOutputs,
            diagnostics = diagnostics,
            statuses = statuses,
            scheduledValues = scheduledValues,
            nestedGraphResults = nestedGraphResults,
            subgraphEvaluator = { subgraphId, childScopeId, externalInputs ->
                val subgraphPlan = plan.graph(subgraphId) ?: return@GemNodeExecutionContext null
                val graphEval = executeGraph(
                    plan = plan,
                    graphPlan = subgraphPlan,
                    scopeId = childScopeId,
                    context = context,
                    parameters = parameters,
                    externalInputs = externalInputs,
                    heldValues = heldValues,
                    hostOutputs = hostOutputs
                )
                SubgraphCallResult(
                    outputs = graphEval.outputs,
                    scheduledValues = graphEval.scheduledValues,
                    diagnostics = graphEval.diagnostics,
                    statuses = graphEval.statuses,
                    nestedGraphResults = listOf(graphEval.result) + graphEval.nestedGraphResults
                )
            },
            scopeId = scopeId,
            signalScheduler = context.signalScheduler
        )

        try {
            val definition = GemNodeDefinitionRegistry.builtIns.find(nodePlan.type)
            if (definition != null) {
                definition.execute(nodeContext)
            } else {
                diagnostics += GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.EXECUTION,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.UNSUPPORTED_NODE_SEMANTICS,
                    message = "Node '${nodePlan.nodeId}' uses unsupported runtime semantics for type '${nodePlan.type.typeId}@${nodePlan.type.version}'.",
                    graphId = graphPlan.graphId,
                    nodeId = nodePlan.nodeId
                )
            }
        } catch (e: Exception) {
            diagnostics += GemRuntimeDiagnostic(
                phase = GemRuntimePhase.EXECUTION,
                severity = GemRuntimeSeverity.ERROR,
                code = GemRuntimeDiagnosticCode.EXECUTION_ERROR,
                message = "Node '${nodePlan.nodeId}' threw an unexpected exception during execution: ${e.message}",
                graphId = graphPlan.graphId,
                nodeId = nodePlan.nodeId
            )
        }

        return NodeEvaluation(
            outputs = outputs,
            scheduledValues = scheduledValues,
            diagnostics = diagnostics,
            statuses = statuses,
            nestedGraphResults = nestedGraphResults
        )
    }

    private fun requireNumber(
        graphId: String,
        nodePlan: GemExecutionNodePlan,
        pinId: String,
        input: GemRuntimeDatum?,
        diagnostics: MutableList<GemRuntimeDiagnostic>
    ): Double? {
        val value = (input as? GemRuntimeDatum.Value)?.value
        return when (value) {
            is GemValue.Number -> value.value
            null -> {
                diagnostics += missingInputDiagnostic(graphId, nodePlan, pinId, "number")
                null
            }

            else -> {
                diagnostics += typeMismatchDiagnostic(graphId, nodePlan, pinId, "number")
                null
            }
        }
    }

    private fun requireBoolean(
        graphId: String,
        nodePlan: GemExecutionNodePlan,
        pinId: String,
        input: GemRuntimeDatum?,
        diagnostics: MutableList<GemRuntimeDiagnostic>
    ): Boolean? {
        val value = (input as? GemRuntimeDatum.Value)?.value
        return when (value) {
            is GemValue.Boolean -> value.value
            null -> {
                diagnostics += missingInputDiagnostic(graphId, nodePlan, pinId, "boolean")
                null
            }

            else -> {
                diagnostics += typeMismatchDiagnostic(graphId, nodePlan, pinId, "boolean")
                null
            }
        }
    }

    private fun requireTiming(
        graphId: String,
        nodePlan: GemExecutionNodePlan,
        pinId: String,
        input: GemRuntimeDatum?,
        diagnostics: MutableList<GemRuntimeDiagnostic>
    ): Timing? {
        val value = (input as? GemRuntimeDatum.Value)?.value
        return when (value) {
            is GemValue.TimingValue -> value.value
            null -> {
                diagnostics += missingInputDiagnostic(graphId, nodePlan, pinId, "timing")
                null
            }

            else -> {
                diagnostics += typeMismatchDiagnostic(graphId, nodePlan, pinId, "timing")
                null
            }
        }
    }

    private fun requireSignal(
        graphId: String,
        nodePlan: GemExecutionNodePlan,
        pinId: String,
        expectedDomain: GemSignalDomain,
        input: GemRuntimeDatum?,
        diagnostics: MutableList<GemRuntimeDiagnostic>
    ): GemSignalBatch? {
        val batch = (input as? GemRuntimeDatum.SignalBatchValue)?.batch
        return when {
            batch == null -> {
                diagnostics += missingInputDiagnostic(graphId, nodePlan, pinId, "${expectedDomain.name.lowercase()} signal batch")
                null
            }

            batch.domain != expectedDomain -> {
                diagnostics += GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.EXECUTION,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.HOST_INPUT_DOMAIN_MISMATCH,
                    message = "Input '${pinId}' on node '${nodePlan.nodeId}' expected ${expectedDomain.name} signals but received ${batch.domain.name}.",
                    graphId = graphId,
                    nodeId = nodePlan.nodeId,
                    pinId = pinId
                )
                null
            }

            else -> batch
        }
    }

    private fun missingInputDiagnostic(
        graphId: String,
        nodePlan: GemExecutionNodePlan,
        pinId: String,
        expectedDescription: String
    ): GemRuntimeDiagnostic = GemRuntimeDiagnostic(
        phase = GemRuntimePhase.EXECUTION,
        severity = GemRuntimeSeverity.ERROR,
        code = GemRuntimeDiagnosticCode.INPUT_VALUE_UNRESOLVED,
        message = "Input '${pinId}' on node '${nodePlan.nodeId}' is missing a resolvable ${expectedDescription} value.",
        graphId = graphId,
        nodeId = nodePlan.nodeId,
        pinId = pinId
    )

    private fun typeMismatchDiagnostic(
        graphId: String,
        nodePlan: GemExecutionNodePlan,
        pinId: String,
        expectedDescription: String
    ): GemRuntimeDiagnostic = GemRuntimeDiagnostic(
        phase = GemRuntimePhase.EXECUTION,
        severity = GemRuntimeSeverity.ERROR,
        code = GemRuntimeDiagnosticCode.INPUT_VALUE_TYPE_MISMATCH,
        message = "Input '${pinId}' on node '${nodePlan.nodeId}' does not match the expected ${expectedDescription} type.",
        graphId = graphId,
        nodeId = nodePlan.nodeId,
        pinId = pinId
    )

    private fun runtimeOwnerId(
        context: GemPreviewContext,
        scopeId: String,
        nodeId: String
    ): String = context.ownerId ?: "$scopeId/$nodeId"

    private fun heldValueKey(scopeId: String, nodeId: String): String = "$scopeId/$nodeId:hold"

    private fun ownerCancelledStatus(
        graphId: String,
        nodeId: String,
        pinId: String,
        ownerId: String
    ): GemRuntimeStatus = GemRuntimeStatus(
        severity = GemRuntimeSeverity.WARNING,
        code = GemRuntimeStatusCode.OWNER_CANCELLED,
        message = "Owner '$ownerId' is cancelled, so '${nodeId}' skipped its scheduled emission.",
        graphId = graphId,
        nodeId = nodeId,
        pinId = pinId,
        ownerId = ownerId
    )

    private fun Double.toPositiveWholeNumber(): Int? {
        if (!isFinite()) {
            return null
        }
        val asInt = toInt()
        return asInt.takeIf { it >= 1 && abs(it.toDouble() - this) < 0.000001 }
    }

    private fun scaleTiming(timing: Timing, multiplier: Int): Timing? = when (timing) {
        is Timing.Duration -> Timing.Duration(timing.duration * multiplier)
        is Timing.Rythm -> {
            val targetFactor = timing.timing.factor * multiplier
            Timing.Rythm.RythmTiming.entries
                .firstOrNull { abs(it.factor - targetFactor) < 0.0001f }
                ?.let(Timing::Rythm)
        }
    }

    private data class NodeEvaluation(
        val outputs: Map<String, GemRuntimeDatum>,
        val hostOutputs: Map<String, GemSignalBatch> = emptyMap(),
        val scheduledValues: List<GemScheduledPreviewValue>,
        val diagnostics: List<GemRuntimeDiagnostic>,
        val statuses: List<GemRuntimeStatus>,
        val nestedGraphResults: List<GemGraphExecutionResult>
    )

    private data class GraphEvaluation(
        val result: GemGraphExecutionResult,
        val nestedGraphResults: List<GemGraphExecutionResult>,
        val outputs: Map<String, GemRuntimeDatum>,
        val scheduledValues: List<GemScheduledPreviewValue>,
        val diagnostics: List<GemRuntimeDiagnostic>,
        val statuses: List<GemRuntimeStatus>
    )
}
