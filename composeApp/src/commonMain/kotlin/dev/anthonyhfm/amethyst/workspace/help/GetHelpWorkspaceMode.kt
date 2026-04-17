package dev.anthonyhfm.amethyst.workspace.help

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

class GetHelpWorkspaceMode(
    val helpRef: String,
) : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Help"
    override val selectable: Boolean = false

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

    @Composable
    fun ModeContent(paddingValues: PaddingValues) {
        HelpViewer(
            helpRef = helpRef,
            paddingValues = paddingValues,
            onClose = { WorkspaceRepository.switchToPreviousMode() }
        )
    }
}
