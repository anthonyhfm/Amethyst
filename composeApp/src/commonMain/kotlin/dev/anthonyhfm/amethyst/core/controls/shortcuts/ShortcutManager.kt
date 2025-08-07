package dev.anthonyhfm.amethyst.core.controls.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import io.github.vinceglb.filekit.PlatformFile

object ShortcutManager {
    fun handleShortcut(keyEvent: KeyEvent): Boolean {
        if (keyEvent.isCtrlPressed && keyEvent.key == Key.C) {
            if (SelectionManager.selections.value.isNotEmpty()) {
                ClipboardManager.copy(SelectionManager.selections.value)
                return true
            }
        }

        if (keyEvent.isCtrlPressed && keyEvent.key == Key.V) {
            return true
        }

        if (keyEvent.isCtrlPressed && keyEvent.key == Key.S) {
            // Save the current workspace
            TODO("workspace saving logic")
            return true
        }

        return false
    }
}