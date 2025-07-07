package dev.anthonyhfm.amethyst.devices.effects.rotate

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

class RotateChainDevice : ChainDevice<RotateChainDeviceState>() {
    override val state = MutableStateFlow(RotateChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()

        AmethystDevice(
            title = "Rotate",
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
data class RotateChainDeviceState(
    val bypass: Boolean = false,
) : DeviceState()