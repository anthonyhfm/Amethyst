package dev.anthonyhfm.amethyst.gem.node.math

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext

object NumberDivideNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.numberDivide
    override fun execute(context: GemNodeExecutionContext) {
        val a = context.numberInput("a") ?: return
        val b = context.numberInput("b") ?: return
        context.emitNumber("result", if (b == 0.0) 0.0 else a / b)
    }
}
