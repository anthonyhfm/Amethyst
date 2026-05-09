package dev.anthonyhfm.amethyst.devices.effects.clear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class ClearChainDevice : LEDChainDevice<ClearChainDeviceState>() {
    override val state = MutableStateFlow(ClearChainDeviceState())

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == selectionUUID }

        ChainDeviceShell(
            title = "Clear",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(140.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) { }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        signalExit?.invoke(n)
    }

    companion object : ChainDeviceFactory<ClearChainDeviceState> {
        override val stateClass = ClearChainDeviceState::class
        override val serializer = ClearChainDeviceState.serializer()
        override fun create() = ClearChainDevice()
    }
}

@Serializable
data class ClearChainDeviceState(
    val mode: ClearMode = ClearMode.Both
) : DeviceState()

enum class ClearMode {
    Lights, Multi, Both
}
