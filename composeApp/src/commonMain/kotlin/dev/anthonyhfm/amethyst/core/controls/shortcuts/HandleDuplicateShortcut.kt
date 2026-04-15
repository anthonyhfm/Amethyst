package dev.anthonyhfm.amethyst.core.controls.shortcuts

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

fun handleDuplicateShortcut(): Boolean {
    val selections = SelectionManager.selections.value

    // GroupChainItem: duplicate selected groups
    val groupItems = selections.filterIsInstance<Selectable.GroupChainItem>()
    if (groupItems.isNotEmpty()) {
        groupItems.groupBy { it.parent.selectionUUID }.forEach { (_, items) ->
            val parent = items.first().parent
            val indices = items.map { it.groupIndex }.sorted()

            when (parent) {
                is GroupChainDevice -> parent.duplicateGroups(indices)
                is MultiGroupChainDevice -> parent.duplicateGroups(indices)
            }
        }

        // Select the newly duplicated items (each original at index i is duplicated after it)
        SelectionManager.clear()
        // After duplication, each group at index i gains a copy at i+1, shifting subsequent groups.
        // Recompute new selection indices: for sorted original indices, the duplicate appears right after each.
        val parent = groupItems.first().parent
        val sortedOriginal = groupItems.map { it.groupIndex }.sorted()
        var offset = 0
        sortedOriginal.forEach { origIdx ->
            val duplicatedIdx = origIdx + offset + 1
            offset++
            SelectionManager.select(
                Selectable.GroupChainItem(parent = parent, groupIndex = duplicatedIdx),
                single = false
            )
        }

        return true
    }

    // ChainDevice: duplicate selected devices
    SelectionManager.selections.value.filterIsInstance<Selectable.ChainDevice>().map { selection ->
        val index = selection.parent.devices.value.indexOfFirst { it.selectionUUID == selection.selectionUUID }

        selection.parent.add(StateChain.unpackDevice(StateChain.packDevice(selection.device)), index)

        return@map selection
    }.apply {
        if (isNotEmpty()) return true
    }

    return false
}
