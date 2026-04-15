package dev.anthonyhfm.amethyst.gem.node.led

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import dev.anthonyhfm.amethyst.gem.runtime.GemSignalBatch

object LedPackNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.ledPack

    override fun execute(context: GemNodeExecutionContext) {
        val r     = context.numberInput("r")     ?: return
        val g     = context.numberInput("g")     ?: return
        val b     = context.numberInput("b")     ?: return
        val x     = context.numberInput("x")     ?: return
        val y     = context.numberInput("y")     ?: return
        val layer = context.numberInput("layer") ?: return

        val signal = Signal.LED(
            origin = null,
            x = x.toInt(),
            y = y.toInt(),
            color = Color(
                red   = r.toFloat().coerceIn(0f, 1f),
                green = g.toFloat().coerceIn(0f, 1f),
                blue  = b.toFloat().coerceIn(0f, 1f)
            ),
            layer = layer.toInt()
        )
        context.emitSignal("signal", GemSignalBatch(domain = GemSignalDomain.LED, signals = listOf(signal)))
    }
}
