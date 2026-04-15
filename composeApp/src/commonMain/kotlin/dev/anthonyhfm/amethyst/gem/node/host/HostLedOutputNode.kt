package dev.anthonyhfm.amethyst.gem.node.host

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDatum

object HostLedOutputNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.hostLedOutput

    override fun execute(context: GemNodeExecutionContext) {
        val batch = context.signalInput("signal", GemSignalDomain.LED) ?: return
        context.emitSignal("signal", batch)
        val portId = context.nodePlan.hostPortId ?: context.nodeId
        context.emitHostOutput(portId, batch)
    }
}
