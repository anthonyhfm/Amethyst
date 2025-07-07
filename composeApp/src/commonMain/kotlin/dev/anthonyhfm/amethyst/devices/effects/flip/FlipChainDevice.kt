package dev.anthonyhfm.amethyst.devices.effects.flip

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

class FlipChainDevice : ChainDevice<FlipChainDeviceState>() {
    override val state = MutableStateFlow(FlipChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()

        AmethystDevice(
            title = "Flip",
            deviceId = internalUUID,
            modifier = Modifier
                .width(100.dp)
        ) {

        }
    }

    override fun midiEnter(n: List<Signal>) {
        midiExit?.invoke(n)
    }
}

@Serializable
data class FlipChainDeviceState(
    val bypass: Boolean = false,
) : DeviceState()