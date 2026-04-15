package dev.anthonyhfm.amethyst.gem.node.structure

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemNodeCategory
import dev.anthonyhfm.amethyst.gem.GemNodeDescriptor
import dev.anthonyhfm.amethyst.gem.GemNodeMetadata
import dev.anthonyhfm.amethyst.gem.GemNodeTypeId
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeStatus
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeStatusCode
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeSeverity

/**
 * Executes a compiled subgraph by delegating recursively through the context's subgraph evaluator.
 *
 * The static [descriptor] here is a placeholder for registry lookup only; the actual per-instance
 * descriptor (with ports matching the subgraph interface) is produced dynamically by
 * [GemBuiltInNodes.subgraphCallDescriptor].
 */
object SubgraphCallNode : GemNodeDefinition() {
    override val descriptor: GemNodeDescriptor = GemNodeDescriptor(
        type = GemNodeTypeId(typeId = GemBuiltInNodes.TypeIds.SUBGRAPH_CALL),
        metadata = GemNodeMetadata(
            label = "Subgraph Call",
            category = GemNodeCategory(id = "structure", label = "Structure"),
            description = "Invokes a subgraph through its explicit interface."
        ),
        inputs  = emptyList(),
        outputs = emptyList(),
        state   = emptyList()
    )

    override fun execute(context: GemNodeExecutionContext) {
        val subgraphId = context.nodePlan.subgraphId
        if (subgraphId == null) {
            context.error(
                code = GemRuntimeDiagnosticCode.SUBGRAPH_PLAN_MISSING,
                message = "Subgraph call node '${context.nodeId}' is missing a compiled subgraph reference."
            )
            return
        }

        val externalInputs = context.nodePlan.inputs
            .mapNotNull { input -> context.rawInput(input.pinId)?.let { input.pinId to it } }
            .toMap(linkedMapOf())

        val result = context.evaluateSubgraph(subgraphId, externalInputs) ?: run {
            context.error(
                code = GemRuntimeDiagnosticCode.SUBGRAPH_PLAN_MISSING,
                message = "Subgraph call node '${context.nodeId}' references missing plan '$subgraphId'."
            )
            return
        }

        context.statuses += GemRuntimeStatus(
            severity = GemRuntimeSeverity.INFO,
            code = GemRuntimeStatusCode.SUBGRAPH_ACTIVE,
            message = "Subgraph call '${context.nodeId}' evaluated '$subgraphId'.",
            graphId = context.graphId,
            nodeId = context.nodeId
        )

        context.nodePlan.outputs.forEach { output ->
            val datum = result.outputs[output.pinId]
            if (datum == null) {
                context.error(
                    code = GemRuntimeDiagnosticCode.SUBGRAPH_OUTPUT_UNRESOLVED,
                    message = "Subgraph call '${context.nodeId}' could not resolve output '${output.pinId}'.",
                    pinId = output.pinId
                )
            } else {
                context.emitDatum(output.pinId, datum)
            }
        }
    }
}
