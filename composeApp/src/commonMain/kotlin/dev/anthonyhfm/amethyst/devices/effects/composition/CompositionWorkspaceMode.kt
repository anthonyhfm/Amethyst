package dev.anthonyhfm.amethyst.devices.effects.composition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode

class CompositionWorkspaceMode : WorkspaceMode() {
    override val displayName: String = "Composition"
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
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Composition Workspace",
                color = Theme[colors][foreground]
            )
        }
    }
}
