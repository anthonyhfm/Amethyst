package dev.anthonyhfm.amethyst.gem.node.host

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemPinType
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDatum
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode
import dev.anthonyhfm.amethyst.gem.runtime.GemSignalBatch

object HostLedInputNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.hostLedInput

    override fun execute(context: GemNodeExecutionContext) {
        val output = context.nodePlan.outputs.singleOrNull() ?: return
        val domain = (output.type as? GemPinType.Signal)?.domain ?: return
        val hostPortId = context.nodePlan.hostPortId ?: context.nodeId
        val batch = context.hostInputs[hostPortId] ?: GemSignalBatch(domain = domain)
        if (batch.domain != domain) {
            context.error(
                code = GemRuntimeDiagnosticCode.HOST_INPUT_DOMAIN_MISMATCH,
                message = "Host input '$hostPortId' expected ${domain.name} signals but received ${batch.domain.name}.",
                pinId = output.pinId
            )
        } else {
            context.emitSignal(output.pinId, batch)
        }
    }
}
