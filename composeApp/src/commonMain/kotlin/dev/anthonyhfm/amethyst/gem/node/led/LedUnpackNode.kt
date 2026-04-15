package dev.anthonyhfm.amethyst.gem.node.led

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemNodeInstance
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDatum
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode

object LedUnpackNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.ledUnpack

    override fun execute(context: GemNodeExecutionContext) {
        val batch = context.signalInput("signal", GemSignalDomain.LED) ?: return
        val signal = batch.signals.filterIsInstance<dev.anthonyhfm.amethyst.core.engine.elements.Signal.LED>().firstOrNull()
        if (batch.signals.isNotEmpty() && signal == null) {
            context.error(
                code = GemRuntimeDiagnosticCode.INPUT_VALUE_UNRESOLVED,
                message = "LED Unpack node '${context.nodeId}' received a signal batch with no LED signals.",
                pinId = "signal"
            )
            return
        }
        if (signal != null) {
            context.emitNumber("r", signal.color.red.toDouble())
            context.emitNumber("g", signal.color.green.toDouble())
            context.emitNumber("b", signal.color.blue.toDouble())
            context.emitNumber("x", signal.x.toDouble())
            context.emitNumber("y", signal.y.toDouble())
            context.emitNumber("layer", signal.layer.toDouble())
        }
    }
}
