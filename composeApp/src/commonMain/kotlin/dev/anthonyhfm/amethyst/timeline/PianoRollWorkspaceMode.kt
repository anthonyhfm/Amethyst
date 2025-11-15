package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

class PianoRollWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Piano Roll"
    override val selectable: Boolean = false
    override val claimInputs: Boolean = true

    var currentEntry: MidiEntry? = null
    var trackIndex: Int = -1
    var entryStartMs: Long = 0L

    @Composable
    fun ModeContent(paddingValues: PaddingValues) {
        val entry = currentEntry ?: return
        val launchpads = Heaven.devices

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(paddingValues)
        ) {
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return false
    }
}
