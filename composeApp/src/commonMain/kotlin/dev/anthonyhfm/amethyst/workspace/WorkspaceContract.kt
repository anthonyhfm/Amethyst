package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceType
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.atsushieno.ktmidi.MidiPortDetails

interface WorkspaceContract {
    sealed interface Event {
        data object OpenVirtualDevicePicker : Event
        data object AddDeviceToViewport : Event

        data class ChangeWorkspaceMode(val mode: WorkspaceMode) : Event
        data class OnSelectDevice(val index: Int?) : Event
        data class ChangeViewportElementPosition(
            val index: Int,
            val offset: Offset
        ) : Event
        data class OnPanViewport(val offset: Offset) : Event
        data class OnClickDeviceConfigure(val index: Int) : Event
        data object OnDismissDeviceConfigure : Event

        data class OnChangeDeviceConfig(
            val index: Int,
            var inputPort: MidiPortDetails?,
            var outputPort: MidiPortDetails?,
        ) : Event



        data class AddChainDevice(val device: ChainDevice<*>, val atIndex: Int? = null) : Event
        data class ReorderChainDevice(val fromIndex: Int, val toIndex: Int) : Event

        data class OnPressVirtualDevice(val x: Int, val y: Int, val offset: Offset) : Event
        data class OnReleaseVirtualDevice(val x: Int, val y: Int, val offset: Offset) : Event
    }

    data class State(
        val mode: WorkspaceMode,
        val showDeviceConfigurator: Int? = null,
        val showDevicePicker: Boolean = false,
        val viewportState: ViewportState = ViewportState(),
        val viewportElements: List<LaunchpadViewportElement> = emptyList()
    )

    data class ViewportState(
        val offset: Offset = Offset.Zero,
        val zoom: Float = 1f,
        val selectedElement: Int? = null,
    )

    interface WorkspaceMode {
        val displayName: String
        val selectable: Boolean

        data class Layout(
            override val displayName: String = "Layout Editor",
            override val selectable: Boolean = true
        ) : WorkspaceMode

        data class Preview(
            override val displayName: String = "Preview",
            override val selectable: Boolean = true
        ) : WorkspaceMode

        data class Chain(
            override val displayName: String = "Chain Editor",
            override val selectable: Boolean = true
        ) : WorkspaceMode
    }
}