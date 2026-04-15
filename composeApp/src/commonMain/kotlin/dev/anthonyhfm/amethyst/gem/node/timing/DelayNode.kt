package dev.anthonyhfm.amethyst.gem.node.timing

import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDatum

object DelayNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.delay

    override fun execute(context: GemNodeExecutionContext) {
        val batch = (context.rawInput("signal_in") as? GemRuntimeDatum.SignalBatchValue)?.batch ?: return
        val delayMs = context.numberInput("delay") ?: return
        context.scheduleSignal(batch.signals, delayMs)
    }
}
