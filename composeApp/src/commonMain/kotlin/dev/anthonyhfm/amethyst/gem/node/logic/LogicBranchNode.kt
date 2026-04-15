package dev.anthonyhfm.amethyst.gem.node.logic

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext

/**
 * Routes an LED signal to the 'true' output when [condition] is true, or the 'false' output
 * otherwise. Both outputs carry separate signal batches; the inactive output is simply not emitted.
 */
object LogicBranchNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.logicBranch

    override fun execute(context: GemNodeExecutionContext) {
        val condition = context.booleanInput("condition") ?: return
        val batch     = context.signalInput("signal", GemSignalDomain.LED) ?: return
        if (condition) {
            context.emitSignal("true", batch)
        } else {
            context.emitSignal("false", batch)
        }
    }
}
