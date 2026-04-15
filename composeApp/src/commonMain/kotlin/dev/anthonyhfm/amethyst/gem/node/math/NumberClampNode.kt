package dev.anthonyhfm.amethyst.gem.node.math

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext

object NumberClampNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.numberClamp
    override fun execute(context: GemNodeExecutionContext) {
        val value = context.numberInput("value") ?: return
        val min   = context.numberInput("min")   ?: return
        val max   = context.numberInput("max")   ?: return
        context.emitNumber("result", value.coerceIn(min, max))
    }
}
