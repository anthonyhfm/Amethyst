package dev.anthonyhfm.amethyst.devices.effects.delay

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

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
            TimeDial(
                headline = "Delay",
                timing = deviceState.timing,
                onSelectTiming = { timing, msValue ->
                    state.update {
                        it.copy(
                            timing = timing,
                            delayMs = msValue
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
            delayInMs = state.value.delayMs.toDouble()
        )
    }
}

@Serializable
data class DelayChainDeviceState(
    val timing: Timing = Timing.Duration(200.milliseconds),
    val delayMs: Int = 200,
) : DeviceState()