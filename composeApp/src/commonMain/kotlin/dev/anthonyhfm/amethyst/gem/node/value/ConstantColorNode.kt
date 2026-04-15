package dev.anthonyhfm.amethyst.gem.node.value

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode

object ConstantColorNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.colorConstant

    override fun execute(context: GemNodeExecutionContext) {
        val color = context.nodePlan.state["value"] as? GemValue.Color
        if (color == null) {
            context.error(
                code = GemRuntimeDiagnosticCode.MISSING_STATE_VALUE,
                message = "Color constant '${context.nodeId}' is missing or invalid runtime state 'value'.",
                pinId = "value"
            )
            return
        }
        context.emitNumber("r", color.value.red.toDouble())
        context.emitNumber("g", color.value.green.toDouble())
        context.emitNumber("b", color.value.blue.toDouble())
    }
}
