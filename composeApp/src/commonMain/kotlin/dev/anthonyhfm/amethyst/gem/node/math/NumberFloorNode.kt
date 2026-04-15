package dev.anthonyhfm.amethyst.gem.node.math

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import kotlin.math.floor

object NumberFloorNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.numberFloor
    override fun execute(context: GemNodeExecutionContext) {
        val value = context.numberInput("value") ?: return
        context.emitNumber("result", floor(value))
    }
}
