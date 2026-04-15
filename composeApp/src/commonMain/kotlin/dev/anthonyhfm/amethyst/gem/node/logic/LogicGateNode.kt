package dev.anthonyhfm.amethyst.gem.node.logic

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext

/**
 * Passes an LED signal through when [enabled] is true; produces no output when false.
 */
object LogicGateNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.logicGate

    override fun execute(context: GemNodeExecutionContext) {
        val enabled = context.booleanInput("enabled") ?: return
        val batch   = context.signalInput("signal", GemSignalDomain.LED) ?: return
        if (enabled) {
            context.emitSignal("out", batch)
        }
    }
}
