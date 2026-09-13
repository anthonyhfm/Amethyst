package dev.anthonyhfm.amethyst.devices.effects.macro_refresh

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.elements.refreshMacroValues
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

class MacroRefreshChainDevice : GenericChainDevice<MacroRefreshChainDeviceState>() {
    override val state = MutableStateFlow(MacroRefreshChainDeviceState)

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Macro Refresh",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(120.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {

        }
    }

    override fun signalEnter(n: List<Signal>) {
        signalExit?.invoke(n.map(Signal::refreshMacroValues))
    }

    override fun timelineDuration(context: TimelineDurationContext) = TimelineDuration.None

    companion object : ChainDeviceFactory<MacroRefreshChainDeviceState> {
        override val stateClass = MacroRefreshChainDeviceState::class
        override val serializer = MacroRefreshChainDeviceState.serializer()
        override fun create() = MacroRefreshChainDevice()
    }
}

@Serializable
data object MacroRefreshChainDeviceState : DeviceState()
