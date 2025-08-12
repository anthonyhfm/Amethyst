package dev.anthonyhfm.amethyst.core.controls.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.any

object ShortcutManager {
    fun handleShortcut(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        if (keyEvent.isCtrlPressed && keyEvent.key == Key.D) {
            return handleDuplicateShortcut()
        }

        if (keyEvent.isCtrlPressed && keyEvent.key == Key.C) {
            if (SelectionManager.selections.value.isNotEmpty()) {
                ClipboardManager.copy(SelectionManager.selections.value)
                return true
            }
        }

        if (keyEvent.isCtrlPressed && keyEvent.key == Key.V) {
            if (SelectionManager.selections.value.isNotEmpty()) {
                ClipboardManager.paste()
                return true
            }
            return true
        }

        if (keyEvent.isCtrlPressed && keyEvent.key == Key.S) {
            println("TODO: Save")
            return true
        }

        return false
    }
}