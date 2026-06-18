package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.timeline.TimelineKeyHandler
import dev.anthonyhfm.amethyst.workspace.modes.chain.ChainModeKeyHandler
import dev.anthonyhfm.amethyst.workspace.modes.layout.LayoutModeKeyHandler
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.atsushieno.ktmidi.MidiPortDetails
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

        data class OnPanViewport(val offset: Offset) : Event
        data class OnZoomViewport(val zoomDelta: Float, val zoomCenter: Offset) : Event
        data class OnClickDeviceConfigure(val uuid: String) : Event

        data class OnChangeDeviceConfig(
            val uuid: String,
            var inputPort: MidiPortDetails?,
            var outputPort: MidiPortDetails?,
        ) : Event

        data class OnDeleteDevice(val uuid: String) : Event

        data class AddDeviceToViewport(val device: LaunchpadViewportElement) : Event

        data class AddChainDevice(val device: GenericChainDevice<*>, val atIndex: Int? = null) : Event
    }

    data class State(
        val mode: WorkspaceMode,
        val showDeviceConfigurator: String? = null,
        val showDevicePicker: Boolean = false,
        val viewportState: ViewportState = ViewportState(),
        val viewportElements: List<LaunchpadViewportElement> = emptyList()
    )

    data class ViewportState(
        val offset: Offset = Offset.Zero,
        val zoom: Float = 1f
    )

    interface WorkspaceMode {
        val displayName: String
        val selectable: Boolean
        val claimInputs: Boolean get() = false

        fun onKeyEvent(event: KeyEvent): Boolean = false
        fun onMidiInput(data: MidiInputData, offset: Offset) = { }

        data class Layout(
            override val displayName: String = "Layout Editor",
            override val selectable: Boolean = true
        ) : WorkspaceMode {
            override fun onKeyEvent(event: KeyEvent): Boolean {
                return LayoutModeKeyHandler.handleKeyInput(event)
            }
        }

        data class Performance(
            override val displayName: String = "Performance",
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

        data class Timeline(
            override val displayName: String = "Timeline",
            override val selectable: Boolean = true
        ) : WorkspaceMode {
            override fun onKeyEvent(event: KeyEvent): Boolean {
                return TimelineKeyHandler.handleKeyInput(event)
            }
        }
        
        // Note: PianoRollWorkspaceMode is defined in timeline package
        // as it needs special handling similar to KeyframesWorkspaceMode
        // Note: CompositionWorkspaceMode is defined in composition package
        // as it needs special handling similar to KeyframesWorkspaceMode
    }
}
