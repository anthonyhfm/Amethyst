package dev.anthonyhfm.amethyst.devices.effects.hold

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
import kotlin.time.Duration.Companion.milliseconds

class HoldChainDevice : ChainDevice<HoldChainDeviceState>() {
    override val state = MutableStateFlow(HoldChainDeviceState())

    @Composable
    override fun Content() {
        AmethystDevice(
            title = "Hold",
            modifier = Modifier.width(140.dp)
        ) {

        }
    }

    override fun midiEnter(n: List<Signal>) {
        midiExit?.invoke(n)
    }
}

@Serializable
data class HoldChainDeviceState(
    val hold: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
) : DeviceState()