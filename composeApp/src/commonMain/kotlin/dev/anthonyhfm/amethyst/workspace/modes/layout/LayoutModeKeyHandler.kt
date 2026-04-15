package dev.anthonyhfm.amethyst.workspace.modes.layout

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

object LayoutModeKeyHandler {
    fun handleKeyInput(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.Backspace, Key.Delete -> {
                    val devices = SelectionManager.selections.value.filterIsInstance<Selectable.VirtualViewportDevice>()

                    devices.forEach { device ->
                        WorkspaceRepository.removeVirtualDevice(device.selectionUUID)
                    }
                }

                Key.Escape -> {
                    SelectionManager.clear()

                    return true
                }
            }
        }

        return false
    }
}