package dev.anthonyhfm.amethyst.gem.node.math

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext

object NumberMinNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.numberMin
    override fun execute(context: GemNodeExecutionContext) {
        val a = context.numberInput("a") ?: return
        val b = context.numberInput("b") ?: return
        context.emitNumber("result", minOf(a, b))
    }
}
