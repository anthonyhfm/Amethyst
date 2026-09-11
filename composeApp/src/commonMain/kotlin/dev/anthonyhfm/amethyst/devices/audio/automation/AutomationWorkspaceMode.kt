package dev.anthonyhfm.amethyst.devices.audio.automation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode

internal class AutomationWorkspaceMode(
    private val device: AutomationChainDevice,
) : WorkspaceMode() {
    override val displayName: String = "Live Automation"

    @Composable
    override fun Content(modifier: Modifier) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            device.EditorContent()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val shouldClose = event.key == Key.Escape ||
            ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.W)
        if (shouldClose) WorkspaceRepository.switchToPreviousMode()
        return shouldClose
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) = Unit
}
