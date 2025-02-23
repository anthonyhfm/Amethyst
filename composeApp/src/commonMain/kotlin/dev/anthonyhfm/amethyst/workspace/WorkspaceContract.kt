package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.core.data.project.ProjectDeviceConfig
import dev.anthonyhfm.amethyst.core.midi.devices.DeviceType
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.atsushieno.ktmidi.MidiPortDetails

interface WorkspaceContract {
    sealed interface Event {
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
            var deviceType: DeviceType?
        ) : Event
    }

    sealed interface Effect

    data class State(
        val mode: WorkspaceMode = WorkspaceMode.LAYOUT,
        val showDeviceConfigurator: Int? = null,
        val viewportState: ViewportState = ViewportState(),
        val viewportElements: List<LaunchpadViewportElement> = emptyList()
    )

    data class ViewportState(
        val offset: Offset = Offset.Zero,
        val zoom: Float = 1f,
        val selectedElement: Int? = null,
    )

    enum class WorkspaceMode(
        val displayName: String,
        val selectable: Boolean
    ) {
        LAYOUT(
            displayName = "Layout Mode",
            selectable = true
        ),
        PREVIEW(
            displayName = "Preview Mode",
            selectable = true
        ),
        CHAIN(
            displayName = "Chain Editor",
            selectable = true
        ),
    }
}