package dev.anthonyhfm.amethyst.devices.effects.copy

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

class CopyChainDevice : ChainDevice<CopyChainDeviceState>() {
    override val state = MutableStateFlow(CopyChainDeviceState())

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Copy",
            isSelected = selections.contains(this),
            modifier = Modifier.width(200.dp)
        ) {

        }
    }

    override fun midiEnter(n: List<Signal>) {
        midiExit?.invoke(n)
    }
}

@Serializable
data class CopyChainDeviceState(
    @Transient
    val data: Any = Any()
) : DeviceState()