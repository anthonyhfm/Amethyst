package dev.anthonyhfm.amethyst.gem.node.math

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import kotlin.math.abs

object NumberAbsNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.numberAbs
    override fun execute(context: GemNodeExecutionContext) {
        val value = context.numberInput("value") ?: return
        context.emitNumber("result", abs(value))
    }
}
