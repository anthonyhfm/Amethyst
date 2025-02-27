package dev.anthonyhfm.amethyst.devices.effects.delay

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

class DelayChainDevice : ChainDevice<DelayChainDeviceState>() {
    override val state = MutableStateFlow(DelayChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()

        AmethystDevice(
            title = "Delay",
            modifier = Modifier
                .width(100.dp)
        ) {
            TextDial(
                headline = "Delay",
                text = "${deviceState.delayMs.roundToInt()} ms",
                value = deviceState.delayMs.toInt() / 1000f,
                onValueChange = { change ->
                    state.update {
                        it.copy(
                            delayMs = (change * 1000).toDouble()
                        )
                    }
                }
            )
        }
    }

    override fun midiEnter(n: List<Signal>) {
        Heaven.schedule(
            job = {
                midiExit?.invoke(n)
            },
            delayInMs = state.value.delayMs
        )
    }
}

@Serializable
data class DelayChainDeviceState(
    val delayMs: Double = 200.0
) : DeviceState()