package dev.anthonyhfm.amethyst.workspace.modes.defaults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.timeline.Timeline
import dev.anthonyhfm.amethyst.timeline.TimelineKeyHandler
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode

class TimelineWorkspaceMode(
    override val displayName: String = "Timeline"
) : WorkspaceMode() {
    override val selectableMode: Boolean = true

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return TimelineKeyHandler.handleKeyInput(event)
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) {
    }

    @Composable
    override fun Content(modifier: Modifier) {
        Timeline()
    }
}
