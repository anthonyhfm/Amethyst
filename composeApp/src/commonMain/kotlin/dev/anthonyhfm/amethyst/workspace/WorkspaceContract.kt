package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.timeline.TimelineKeyHandler
import dev.anthonyhfm.amethyst.workspace.modes.defaults.ChainModeKeyHandler
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
interface WorkspaceContract {
    sealed interface Event {
        data object OpenVirtualDevicePicker : Event
        data object DismissVirtualDevicePicker : Event
        data object OnDismissDeviceConfigure : Event

        data class ChangeViewportElementPosition(
            val index: Int,
            val offset: Offset
        ) : Event

        data class OnViewportElementMoveFinished(val uuid: String) : Event

        data class OnClickDeviceConfigure(val uuid: String) : Event

        data class OnChangeDeviceConfig(
            val uuid: String,
            val deviceId: String?,
        ) : Event

        data class OnDeleteDevice(val uuid: String) : Event

        data class AddDeviceToViewport(val device: LaunchpadViewportElement) : Event

        data class AddChainDevice(val device: GenericChainDevice<*>, val atIndex: Int? = null) : Event
    }

    data class State(
        val mode: dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode,
        val showDeviceConfigurator: String? = null,
        val showDevicePicker: Boolean = false
    )
}
