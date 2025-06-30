package dev.anthonyhfm.amethyst.devices.effects.loop

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

class LoopChainDevice : ChainDevice<LoopChainDeviceState>() {
    override val state = MutableStateFlow(LoopChainDeviceState())

    @Composable
    override fun Content() {
        AmethystDevice(
            title = "Loop",
            modifier = Modifier.width(200.dp)
        ) {

        }
    }

    override fun midiEnter(n: List<Signal>) {
        midiExit?.invoke(n)
    }
}

@Serializable
data class LoopChainDeviceState(
    val repeat: Int = 2,
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val gate: Float = 0.5f
) : DeviceState()