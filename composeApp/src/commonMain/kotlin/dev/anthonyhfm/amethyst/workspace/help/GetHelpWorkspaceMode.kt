package dev.anthonyhfm.amethyst.workspace.help

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode

class GetHelpWorkspaceMode(
    val helpRef: String,
) : WorkspaceMode() {
    override val displayName: String = "Help"
    override val selectableMode: Boolean = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        val isExitKey = event.key == Key.Escape ||
            ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.W)

        if (isExitKey) {
            WorkspaceRepository.switchToPreviousMode()
            return true
        }

        return false
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) {
    }

    @Composable
    override fun Content(modifier: Modifier) {
        Box(modifier = modifier) {
            HelpViewer(
                helpRef = helpRef,
                paddingValues = PaddingValues(),
            )
        }
    }
}
