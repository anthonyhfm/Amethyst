package dev.anthonyhfm.amethyst.core.controls.shortcuts

import androidx.compose.ui.input.key.KeyEvent
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager

fun handleRenameShortcut(@Suppress("UNUSED_PARAMETER") keyEvent: KeyEvent? = null): Boolean {
    val selections = SelectionManager.selections.value

    // Try GroupChainItem first
    val groupItem = selections.filterIsInstance<Selectable.GroupChainItem>().firstOrNull()
    if (groupItem != null) {
        SelectionManager.renameRequest.value = SelectionManager.RenameTarget.GroupItem(
            parentUUID = groupItem.parent.selectionUUID,
            groupIndex = groupItem.groupIndex
        )
        return true
    }

    // Try TimelineTrack
    val track = selections.filterIsInstance<Selectable.TimelineTrack>().firstOrNull()
    if (track != null) {
        SelectionManager.renameRequest.value = SelectionManager.RenameTarget.Track(
            trackIndex = track.trackIndex
        )
        return true
    }

    // Try TimelineEntryItem (clips)
    val entryItem = selections.filterIsInstance<Selectable.TimelineEntryItem>().firstOrNull()
    if (entryItem != null) {
        SelectionManager.renameRequest.value = SelectionManager.RenameTarget.TimelineEntry(
            trackIndex = entryItem.trackIndex,
            entryStartMs = entryItem.entryStartMs
        )
        return true
    }

    return false
}