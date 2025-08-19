package dev.anthonyhfm.amethyst.core.controls.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager

object ShortcutManager {
    fun handleShortcut(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        // Undo/Redo Shortcuts
        if (keyEvent.isCtrlPressed && keyEvent.key == Key.Z) {
            return if (keyEvent.isShiftPressed) {
                // Ctrl+Shift+Z = Redo
                UndoManager.redo()
                true
            } else {
                // Ctrl+Z = Undo
                UndoManager.undo()
                true
            }
        }

        // Alternative Redo shortcut (Ctrl+Y)
        if (keyEvent.isCtrlPressed && keyEvent.key == Key.Y) {
            UndoManager.redo()
            return true
        }

        if (keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete) {
            return handleDeletionShortcut()
        }

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
            ClipboardManager.paste()
            return true
        }

        if (keyEvent.isCtrlPressed && keyEvent.key == Key.S) {
            println("TODO: Save")
            return true
        }

        if (keyEvent.key == Key.DirectionDown || keyEvent.key == Key.DirectionUp) {
            return handleNavigationShortcut(keyEvent)
        }

        return false
    }
}