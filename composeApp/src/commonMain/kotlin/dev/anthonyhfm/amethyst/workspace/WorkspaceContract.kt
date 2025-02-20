package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

interface WorkspaceContract {
    sealed interface Event {
        data object AddDeviceToViewport : Event

        data class ChangeWorkspaceMode(val mode: WorkspaceMode) : Event
        data class ChangeViewportElementPosition(
            val index: Int,
            val offset: Offset
        ) : Event
        data class OnClickDeviceConfigure(val index: Int) : Event
        data object OnDismissDeviceConfigure : Event
    }

    sealed interface Effect {

    }

    data class State(
        val mode: WorkspaceMode = WorkspaceMode.PREVIEW,
        val showDeviceConfigurator: Int? = null,
        val viewportElements: List<LaunchpadViewportElement> = emptyList()
    )

    enum class WorkspaceMode(
        val displayName: String,
        // If selectable, it will be displayed in the Workspace Mode Switcher list.
        val selectable: Boolean
    ) {
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