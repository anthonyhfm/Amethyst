package dev.anthonyhfm.amethyst.gem.node.math

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import kotlin.math.ceil

object NumberCeilNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.numberCeil
    override fun execute(context: GemNodeExecutionContext) {
        val value = context.numberInput("value") ?: return
        context.emitNumber("result", ceil(value))
    }
}
