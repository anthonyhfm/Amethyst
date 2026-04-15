package dev.anthonyhfm.amethyst.gem.runtime

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.Version
import dev.anthonyhfm.amethyst.gem.GemGraphKind
import dev.anthonyhfm.amethyst.gem.GemNodeTypeId
import dev.anthonyhfm.amethyst.gem.GemPinType
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.GemValueType

data class GemExecutionPlan(
    val assetId: String,
    val assetName: String,
    val schemaVersion: Version,
    val rootGraphId: String,
    val exposedParameters: List<GemExecutionParameter>,
    val graphPlans: List<GemExecutionGraphPlan>,
    val diagnostics: List<GemRuntimeDiagnostic> = emptyList()
) {
    fun graph(graphId: String): GemExecutionGraphPlan? = graphPlans.firstOrNull { it.graphId == graphId }

    fun parameter(parameterId: String): GemExecutionParameter? = exposedParameters.firstOrNull { it.id == parameterId }
}

data class GemExecutionParameter(
    val id: String,
    val label: String,
    val type: GemValueType,
    val defaultValue: GemValue
)

data class GemExecutionGraphPlan(
    val graphId: String,
    val kind: GemGraphKind,
    val executionOrder: List<String>,
    val nodePlans: List<GemExecutionNodePlan>,
    val inputs: List<GemExecutionGraphInput> = emptyList(),
    val outputs: List<GemExecutionGraphOutput> = emptyList()
) {
    fun node(nodeId: String): GemExecutionNodePlan? = nodePlans.firstOrNull { it.nodeId == nodeId }

    fun input(portId: String): GemExecutionGraphInput? = inputs.firstOrNull { it.portId == portId }

    fun output(portId: String): GemExecutionGraphOutput? = outputs.firstOrNull { it.portId == portId }
}

data class GemExecutionGraphInput(
    val portId: String,
    val label: String,
    val type: GemPinType,
    val required: Boolean = false,
    val defaultValue: GemValue? = null
)

data class GemExecutionGraphOutput(
    val portId: String,
    val label: String,
    val type: GemPinType
)

data class GemExecutionNodePlan(
    val nodeId: String,
    val label: String,
    val type: GemNodeTypeId,
    val inputs: List<GemExecutionInputBinding> = emptyList(),
    val outputs: List<GemExecutionOutputBinding> = emptyList(),
    val state: Map<String, GemValue> = emptyMap(),
    val hostPortId: String? = null,
    val outputParameterBindings: Map<String, String> = emptyMap(),
    val graphOutputBindings: Map<String, String> = emptyMap(),
    val subgraphId: String? = null
) {
    fun input(pinId: String): GemExecutionInputBinding? = inputs.firstOrNull { it.pinId == pinId }

    fun output(pinId: String): GemExecutionOutputBinding? = outputs.firstOrNull { it.pinId == pinId }
}

data class GemExecutionInputBinding(
    val pinId: String,
    val type: GemPinType,
    val source: GemExecutionInputSource
)

data class GemExecutionOutputBinding(
    val pinId: String,
    val type: GemPinType
)

sealed interface GemExecutionInputSource {
    data class Connection(
        val fromNodeId: String,
        val fromPinId: String
    ) : GemExecutionInputSource

    data class DefaultValue(
        val value: GemValue
    ) : GemExecutionInputSource

    data class ExposedParameter(
        val parameterId: String
    ) : GemExecutionInputSource

    data class GraphInput(
        val portId: String
    ) : GemExecutionInputSource

    data object Unresolved : GemExecutionInputSource
}

data class GemNodeOutputRef(
    val nodeId: String,
    val pinId: String
)

enum class GemRuntimePhase {
    COMPILE,
    EXECUTION
}

enum class GemRuntimeSeverity {
    INFO,
    WARNING,
    ERROR
}

enum class GemRuntimeDiagnosticCode {
    VALIDATION_ERROR,
    DUPLICATE_PARAMETER_ID,
    AMBIGUOUS_PARAMETER_BINDING,
    MULTIPLE_INPUT_SOURCES,
    INVALID_PARAMETER_DEFAULT_VALUE,
    INVALID_STATE_VALUE,
    MISSING_STATE_VALUE,
    PARAMETER_BINDING_MISSING,
    MISSING_PARAMETER_VALUE,
    INPUT_VALUE_UNRESOLVED,
    INPUT_VALUE_TYPE_MISMATCH,
    HOST_INPUT_DOMAIN_MISMATCH,
    INVALID_RUNTIME_VALUE,
    SUBGRAPH_OUTPUT_UNRESOLVED,
    SUBGRAPH_PLAN_MISSING,
    UNSUPPORTED_NODE_SEMANTICS,
    EXECUTION_ERROR
}

data class GemRuntimeDiagnostic(
    val phase: GemRuntimePhase,
    val severity: GemRuntimeSeverity,
    val code: GemRuntimeDiagnosticCode,
    val message: String,
    val graphId: String? = null,
    val nodeId: String? = null,
    val pinId: String? = null,
    val parameterId: String? = null
)

data class GemCompilationResult(
    val plan: GemExecutionPlan?,
    val diagnostics: List<GemRuntimeDiagnostic>
) {
    val isSuccess: Boolean
        get() = plan != null && diagnostics.none { it.severity == GemRuntimeSeverity.ERROR }
}

data class GemSignalBatch(
    val domain: GemSignalDomain,
    val signals: List<Signal> = emptyList()
)

data class GemPreviewContext(
    val parameterValues: Map<String, GemValue> = emptyMap(),
    val hostInputs: Map<String, GemSignalBatch> = emptyMap(),
    val evaluationTime: Timing? = null,
    val previewMode: Boolean = true,
    val runtimeState: GemRuntimeState = GemRuntimeState(),
    val ownerId: String? = null,
    val cancelledOwners: Set<String> = emptySet(),
    val signalScheduler: ((signals: List<Signal>, delayMs: Double) -> Unit)? = null
)

data class GemRuntimeState(
    val heldValues: Map<String, GemValue> = emptyMap()
)

sealed interface GemRuntimeDatum {
    data class Value(
        val value: GemValue
    ) : GemRuntimeDatum

    data class SignalBatchValue(
        val batch: GemSignalBatch
    ) : GemRuntimeDatum
}

data class GemScheduledPreviewValue(
    val graphId: String,
    val nodeId: String,
    val pinId: String,
    val value: GemValue,
    val delay: Timing,
    val ownerId: String? = null,
    val cancellable: Boolean = true
)

enum class GemRuntimeStatusCode {
    SUBGRAPH_ACTIVE,
    GATE_CLOSED,
    HOLD_ACTIVE,
    VALUE_SCHEDULED,
    OWNER_CANCELLED
}

data class GemRuntimeStatus(
    val severity: GemRuntimeSeverity,
    val code: GemRuntimeStatusCode,
    val message: String,
    val graphId: String,
    val nodeId: String,
    val pinId: String? = null,
    val ownerId: String? = null
)

data class GemNodeExecutionResult(
    val nodeId: String,
    val outputs: Map<String, GemRuntimeDatum> = emptyMap(),
    val scheduledValues: List<GemScheduledPreviewValue> = emptyList(),
    val statuses: List<GemRuntimeStatus> = emptyList()
)

data class GemGraphExecutionResult(
    val graphId: String,
    val nodeResults: List<GemNodeExecutionResult> = emptyList(),
    val outputs: Map<String, GemRuntimeDatum> = emptyMap(),
    val scheduledValues: List<GemScheduledPreviewValue> = emptyList(),
    val diagnostics: List<GemRuntimeDiagnostic> = emptyList(),
    val statuses: List<GemRuntimeStatus> = emptyList()
) {
    fun output(nodeId: String, pinId: String): GemRuntimeDatum? = nodeResults
        .firstOrNull { it.nodeId == nodeId }
        ?.outputs
        ?.get(pinId)

    fun interfaceOutput(portId: String): GemRuntimeDatum? = outputs[portId]
}

data class GemExecutionResult(
    val plan: GemExecutionPlan,
    val graphResults: List<GemGraphExecutionResult>,
    val diagnostics: List<GemRuntimeDiagnostic>,
    val hostOutputs: Map<String, GemSignalBatch> = emptyMap(),
    val statuses: List<GemRuntimeStatus> = emptyList(),
    val runtimeState: GemRuntimeState = GemRuntimeState()
) {
    val isSuccess: Boolean
        get() = diagnostics.none { it.severity == GemRuntimeSeverity.ERROR }

    fun graph(graphId: String): GemGraphExecutionResult? = graphResults.firstOrNull { it.graphId == graphId }

    fun datum(graphId: String, nodeId: String, pinId: String): GemRuntimeDatum? = graph(graphId)?.output(nodeId, pinId)

    fun value(graphId: String, nodeId: String, pinId: String): GemValue? =
        (datum(graphId, nodeId, pinId) as? GemRuntimeDatum.Value)?.value

    fun signalBatch(graphId: String, nodeId: String, pinId: String): GemSignalBatch? =
        (datum(graphId, nodeId, pinId) as? GemRuntimeDatum.SignalBatchValue)?.batch

    fun hostOutput(portId: String): GemSignalBatch? = hostOutputs[portId]
}

internal fun GemValue.matches(type: GemValueType): Boolean = when {
    this is GemValue.Number && type == GemValueType.Number -> true
    this is GemValue.Boolean && type == GemValueType.Boolean -> true
    this is GemValue.Color && type == GemValueType.Color -> true
    this is GemValue.TimingValue && type == GemValueType.Timing -> true
    this is GemValue.Enum && type is GemValueType.Enum ->
        enumId == type.definition.id && type.definition.options.any { it.id == optionId }
    else -> false
}
