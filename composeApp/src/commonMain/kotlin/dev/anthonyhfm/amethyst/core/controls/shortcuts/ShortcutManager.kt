package dev.anthonyhfm.amethyst.core.controls.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.shortcuts.handleRenameShortcut
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.deprecated.openFileSaver
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray

object ShortcutManager {
    @OptIn(ExperimentalSerializationApi::class)
    fun handleShortcut(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.Z) {
            return if (keyEvent.isShiftPressed) {
                UndoManager.redo()
                true
            } else {
                // Ctrl+Z = Undo
                UndoManager.undo()
                true
            }
        }

        // Alternative Redo shortcut (Ctrl+Y)
        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.Y) {
            UndoManager.redo()
            return true
        }

        if (keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace) {
            println("Delete/Backspace pressed")
            return handleDeletionShortcut()
        }

        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.D) {
            return handleDuplicateShortcut()
        }

        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.C) {
            if (SelectionManager.selections.value.isNotEmpty()) {
                ClipboardManager.copy(SelectionManager.selections.value)
                return true
            }
        }

        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.V) {
            ClipboardManager.paste()
            return true
        }

        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.S) {
            var path = WorkspaceRepository.saveableWorkspaceData?.path

            if (path == null) {
                runBlocking {
                    path = FileKit.openFileSaver(
                        suggestedName = "project",
                        extension = "amproj"
                    )?.path
                }
            }

            runBlocking {
                PlatformFile(path ?: return@runBlocking).write(
                    AmethystProtoBuf.encodeToByteArray(WorkspaceRepository.saveWorkspace())
                )
            }

            return true
        }

        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.R) {
            return handleRenameShortcut(keyEvent)
        }

        if (keyEvent.key == Key.DirectionDown || keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight) {
            return handleNavigationShortcut(keyEvent)
        }

        return false
    }
}