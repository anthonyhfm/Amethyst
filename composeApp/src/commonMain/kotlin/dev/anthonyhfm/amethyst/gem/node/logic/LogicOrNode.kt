package dev.anthonyhfm.amethyst.gem.node.logic

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext

object LogicOrNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.logicOr
    override fun execute(context: GemNodeExecutionContext) {
        val a = context.booleanInput("a") ?: return
        val b = context.booleanInput("b") ?: return
        context.emitBoolean("result", a || b)
    }
}
