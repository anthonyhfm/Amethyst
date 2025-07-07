package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.workspace.modes.chain.ChainModeKeyHandler
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.atsushieno.ktmidi.MidiPortDetails

interface WorkspaceContract {
    sealed interface Event {
        data object OpenVirtualDevicePicker : Event
        data object DismissVirtualDevicePicker : Event
        data object OnDismissDeviceConfigure : Event

        data class ChangeWorkspaceMode(val mode: WorkspaceMode) : Event
        data class OnSelectDevice(val index: Int?) : Event
        data class ChangeViewportElementPosition(
            val index: Int,
            val offset: Offset
        ) : Event
        data class OnPanViewport(val offset: Offset) : Event
        data class OnClickDeviceConfigure(val index: Int) : Event

        data class OnChangeDeviceConfig(
            val index: Int,
            var inputPort: MidiPortDetails?,
            var outputPort: MidiPortDetails?,
        ) : Event

        data class AddDeviceToViewport(val device: LaunchpadViewportElement) : Event

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

        fun onKeyEvent(event: KeyEvent): Boolean = false

        data class Layout(
            override val displayName: String = "Layout Editor",
            override val selectable: Boolean = true
        ) : WorkspaceMode

        data class Preview(
            override val displayName: String = "Preview",
            override val selectable: Boolean = true
        ) : WorkspaceMode

        data class LightsChain(
            override val displayName: String = "Lights (Chain Editor)",
            override val selectable: Boolean = true
        ) : WorkspaceMode {
            override fun onKeyEvent(event: KeyEvent): Boolean {
                return ChainModeKeyHandler.handleKeyInput(event)
            }
        }

        data class SamplingChain(
            override val displayName: String = "Sampling (Chain Editor)",
            override val selectable: Boolean = true
        ) : WorkspaceMode {
            override fun onKeyEvent(event: KeyEvent): Boolean {
                return ChainModeKeyHandler.handleKeyInput(event)
            }
        }
    }
}