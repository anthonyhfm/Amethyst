package dev.anthonyhfm.amethyst.devices.audio.clip

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

class ClipChainDevice : ChainDevice<ClipChainDeviceState>() {
    override val state = MutableStateFlow(ClipChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()

        AmethystDevice(
            title = "Clip",
            modifier = Modifier
                .width(400.dp)
        ) {

        }
    }

    override fun midiEnter(n: List<Signal>) {
        midiExit?.invoke(
            n.map {
                it.copy(
                    layer = state.value.layer
                )
            }
        )
    }
}

@Serializable
data class ClipChainDeviceState(
    val layer: Int = 0,
) : DeviceState()