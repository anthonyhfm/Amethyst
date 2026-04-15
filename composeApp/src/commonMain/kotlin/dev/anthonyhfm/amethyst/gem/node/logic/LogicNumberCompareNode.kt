package dev.anthonyhfm.amethyst.gem.node.logic

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext

object LogicNumberCompareNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.logicNumberCompare

    override fun execute(context: GemNodeExecutionContext) {
        val a  = context.numberInput("a")  ?: return
        val b  = context.numberInput("b")  ?: return
        val op = context.enumInput("op")   ?: return
        val result = when (op.optionId) {
            "eq"  -> a == b
            "neq" -> a != b
            "gt"  -> a > b
            "gte" -> a >= b
            "lt"  -> a < b
            "lte" -> a <= b
            else  -> false
        }
        context.emitBoolean("result", result)
    }
}
