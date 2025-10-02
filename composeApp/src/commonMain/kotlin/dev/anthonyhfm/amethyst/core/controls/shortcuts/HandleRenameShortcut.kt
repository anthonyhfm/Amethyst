package dev.anthonyhfm.amethyst.core.controls.shortcuts

import androidx.compose.ui.input.key.KeyEvent
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager

fun handleRenameShortcut(@Suppress("UNUSED_PARAMETER") keyEvent: KeyEvent? = null): Boolean {
    // Find a GroupChainItem in the current selection and trigger rename for it.
    val target = SelectionManager.selections.value
        .filterIsInstance<Selectable.GroupChainItem>()
        .firstOrNull()
        ?: return false

    SelectionManager.renameRequest.value = SelectionManager.RenameTarget(
        parentUUID = target.parent.selectionUUID,
        groupIndex = target.groupIndex
    )

    return true
}