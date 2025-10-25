package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import dev.anthonyhfm.amethyst.timeline.utils.TimelineClipUtils
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable

object TimelineKeyHandler {
    fun handleKeyInput(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.E) {
            val selection = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineTime>().firstOrNull()

            return selection?.let {
                TimelineClipUtils.cutAtSelection(it)
            } ?: false
        }

        return false
    }
}