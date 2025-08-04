package dev.anthonyhfm.amethyst.workspace.modes.chain

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager

object ChainModeKeyHandler {
    fun handleKeyInput(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type == KeyEventType.KeyUp) {
            when (keyEvent.key) {
                Key.Backspace, Key.Delete -> {
                    val selections = SelectionManager.selections.value.filter { it is Selectable.ChainDevice }

                    selections.forEach { selection ->
                        (selection as Selectable.ChainDevice).parent.remove(selection.device.selectionUUID)

                        SelectionManager.clear()
                    }
                }

                Key.Escape -> {
                    SelectionManager.clear()
                }
            }
        }

        return false
    }
}