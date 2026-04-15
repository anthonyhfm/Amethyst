package dev.anthonyhfm.amethyst.gem.node

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.runtime.GemExecutionNodePlan
import dev.anthonyhfm.amethyst.gem.runtime.GemGraphExecutionResult
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDatum
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnostic
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimePhase
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeSeverity
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeStatus
import dev.anthonyhfm.amethyst.gem.runtime.GemScheduledPreviewValue
import dev.anthonyhfm.amethyst.gem.runtime.GemSignalBatch

/**
 * Result produced by a subgraph call delegated from [GemNodeExecutionContext.evaluateSubgraph].
 */
data class SubgraphCallResult(
    val outputs: Map<String, GemRuntimeDatum>,
    val scheduledValues: List<GemScheduledPreviewValue>,
    val diagnostics: List<GemRuntimeDiagnostic>,
    val statuses: List<GemRuntimeStatus>,
    val nestedGraphResults: List<GemGraphExecutionResult>
)

/**
 * Runtime context passed to [GemNodeDefinition.execute].
 *
 * Provides typed accessors for resolved input values, state, and helpers for writing outputs and
 * diagnostics. Subgraph calls are delegated back to the executor via [evaluateSubgraph].
 */
class GemNodeExecutionContext(
    val graphId: String,
    val nodePlan: GemExecutionNodePlan,
    private val resolvedInputs: Map<String, GemRuntimeDatum?>,
    /** Incoming host signal batches (keyed by host port ID) from the preview context. */
    val hostInputs: Map<String, GemSignalBatch>,
    /** Write node output values here. */
    val outputs: LinkedHashMap<String, GemRuntimeDatum>,
    /** Write signals to host ports here (for host output nodes). */
    val hostOutputs: MutableMap<String, GemSignalBatch>,
    val diagnostics: MutableList<GemRuntimeDiagnostic>,
    val statuses: MutableList<GemRuntimeStatus>,
    val scheduledValues: MutableList<GemScheduledPreviewValue>,
    val nestedGraphResults: MutableList<GemGraphExecutionResult>,
    /** Invoked by SUBGRAPH_CALL to recursively execute a sub-graph. */
    private val subgraphEvaluator: ((subgraphId: String, scopeId: String, externalInputs: Map<String, GemRuntimeDatum>) -> SubgraphCallResult?)?,
    val scopeId: String,
    /** Optional scheduler: emits a delayed signal burst via the host after [delayMs] milliseconds. */
    private val signalScheduler: ((signals: List<Signal>, delayMs: Double) -> Unit)? = null
) {
    val nodeId: String get() = nodePlan.nodeId

    // ── Typed input accessors ────────────────────────────────────────────────

    fun numberInput(pinId: String): Double? = requireNumber(pinId, resolvedInputs[pinId])

    fun booleanInput(pinId: String): Boolean? = requireBoolean(pinId, resolvedInputs[pinId])

    fun signalInput(pinId: String, expectedDomain: GemSignalDomain): GemSignalBatch? =
        requireSignal(pinId, expectedDomain, resolvedInputs[pinId])

    fun enumInput(pinId: String): GemValue.Enum? {
        val value = (resolvedInputs[pinId] as? GemRuntimeDatum.Value)?.value
        return when (value) {
            is GemValue.Enum -> value
            null -> { diagnostics += missingInputDiagnostic(pinId, "enum"); null }
            else -> { diagnostics += typeMismatchDiagnostic(pinId, "enum"); null }
        }
    }

    /** Returns the raw resolved datum for a pin, without type checking. */
    fun rawInput(pinId: String): GemRuntimeDatum? = resolvedInputs[pinId]

    // ── Compiled state accessors ─────────────────────────────────────────────

    fun numberState(fieldId: String): Double? =
        (nodePlan.state[fieldId] as? GemValue.Number)?.value

    fun booleanState(fieldId: String): Boolean? =
        (nodePlan.state[fieldId] as? GemValue.Boolean)?.value

    // ── Output helpers ───────────────────────────────────────────────────────

    fun emitNumber(pinId: String, value: Double) {
        outputs[pinId] = GemRuntimeDatum.Value(GemValue.Number(value))
    }

    fun emitBoolean(pinId: String, value: Boolean) {
        outputs[pinId] = GemRuntimeDatum.Value(GemValue.Boolean(value))
    }

    fun emitSignal(pinId: String, batch: GemSignalBatch) {
        outputs[pinId] = GemRuntimeDatum.SignalBatchValue(batch)
    }

    fun emitDatum(pinId: String, datum: GemRuntimeDatum) {
        outputs[pinId] = datum
    }

    fun emitHostOutput(portId: String, batch: GemSignalBatch) {
        hostOutputs[portId] = batch
    }

    fun scheduleSignal(signals: List<Signal>, delayMs: Double) {
        signalScheduler?.invoke(signals, delayMs)
    }

    // ── Subgraph delegation ──────────────────────────────────────────────────

    /**
     * Recursively evaluates a subgraph and returns its result, or null if the subgraph plan
     * cannot be found. Side-effects (diagnostics, statuses, nested results) are merged into this
     * context automatically.
     */
    fun evaluateSubgraph(
        subgraphId: String,
        externalInputs: Map<String, GemRuntimeDatum>
    ): SubgraphCallResult? {
        val childScopeId = "$scopeId/$nodeId[$subgraphId]"
        val result = subgraphEvaluator?.invoke(subgraphId, childScopeId, externalInputs)
        if (result != null) {
            diagnostics += result.diagnostics
            statuses += result.statuses
            scheduledValues += result.scheduledValues
            nestedGraphResults += result.nestedGraphResults
        }
        return result
    }

    // ── Diagnostic helpers ───────────────────────────────────────────────────

    fun error(code: GemRuntimeDiagnosticCode, message: String, pinId: String? = null) {
        diagnostics += GemRuntimeDiagnostic(
            phase = GemRuntimePhase.EXECUTION,
            severity = GemRuntimeSeverity.ERROR,
            code = code,
            message = message,
            graphId = graphId,
            nodeId = nodeId,
            pinId = pinId
        )
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun requireNumber(pinId: String, input: GemRuntimeDatum?): Double? {
        val value = (input as? GemRuntimeDatum.Value)?.value
        return when (value) {
            is GemValue.Number -> value.value
            null -> { diagnostics += missingInputDiagnostic(pinId, "number"); null }
            else -> { diagnostics += typeMismatchDiagnostic(pinId, "number"); null }
        }
    }

    private fun requireBoolean(pinId: String, input: GemRuntimeDatum?): Boolean? {
        val value = (input as? GemRuntimeDatum.Value)?.value
        return when (value) {
            is GemValue.Boolean -> value.value
            null -> { diagnostics += missingInputDiagnostic(pinId, "boolean"); null }
            else -> { diagnostics += typeMismatchDiagnostic(pinId, "boolean"); null }
        }
    }

    private fun requireSignal(
        pinId: String,
        expectedDomain: GemSignalDomain,
        input: GemRuntimeDatum?
    ): GemSignalBatch? {
        val batch = (input as? GemRuntimeDatum.SignalBatchValue)?.batch
        return when {
            batch == null -> {
                diagnostics += missingInputDiagnostic(pinId, "${expectedDomain.name.lowercase()} signal batch")
                null
            }
            batch.domain != expectedDomain -> {
                diagnostics += GemRuntimeDiagnostic(
                    phase = GemRuntimePhase.EXECUTION,
                    severity = GemRuntimeSeverity.ERROR,
                    code = GemRuntimeDiagnosticCode.HOST_INPUT_DOMAIN_MISMATCH,
                    message = "Input '$pinId' on node '$nodeId' expected ${expectedDomain.name} signals but received ${batch.domain.name}.",
                    graphId = graphId,
                    nodeId = nodeId,
                    pinId = pinId
                )
                null
            }
            else -> batch
        }
    }

    private fun missingInputDiagnostic(pinId: String, expectedDescription: String) =
        GemRuntimeDiagnostic(
            phase = GemRuntimePhase.EXECUTION,
            severity = GemRuntimeSeverity.ERROR,
            code = GemRuntimeDiagnosticCode.INPUT_VALUE_UNRESOLVED,
            message = "Input '$pinId' on node '$nodeId' is missing a resolvable $expectedDescription value.",
            graphId = graphId,
            nodeId = nodeId,
            pinId = pinId
        )

    private fun typeMismatchDiagnostic(pinId: String, expectedDescription: String) =
        GemRuntimeDiagnostic(
            phase = GemRuntimePhase.EXECUTION,
            severity = GemRuntimeSeverity.ERROR,
            code = GemRuntimeDiagnosticCode.INPUT_VALUE_TYPE_MISMATCH,
            message = "Input '$pinId' on node '$nodeId' does not match the expected $expectedDescription type.",
            graphId = graphId,
            nodeId = nodeId,
            pinId = pinId
        )
}
