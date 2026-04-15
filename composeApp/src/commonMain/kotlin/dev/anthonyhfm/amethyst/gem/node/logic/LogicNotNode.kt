package dev.anthonyhfm.amethyst.gem.node.logic

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext

object LogicNotNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.logicNot
    override fun execute(context: GemNodeExecutionContext) {
        val value = context.booleanInput("value") ?: return
        context.emitBoolean("result", !value)
    }
}
