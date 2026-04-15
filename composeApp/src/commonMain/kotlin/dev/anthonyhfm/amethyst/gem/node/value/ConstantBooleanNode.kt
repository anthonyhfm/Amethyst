package dev.anthonyhfm.amethyst.gem.node.value

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDatum
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode

object ConstantBooleanNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.constantBoolean

    override fun execute(context: GemNodeExecutionContext) {
        val value = context.nodePlan.state["value"]
        if (value == null) {
            context.error(
                code = GemRuntimeDiagnosticCode.MISSING_STATE_VALUE,
                message = "Constant node '${context.nodeId}' is missing runtime state 'value'.",
                pinId = "value"
            )
        } else {
            context.emitDatum("value", GemRuntimeDatum.Value(value))
        }
    }
}
