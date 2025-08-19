package dev.anthonyhfm.amethyst.core.controls.shortcuts

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.flow.update

fun handleDuplicateShortcut(): Boolean {
    SelectionManager.selections.value.filterIsInstance<Selectable.ChainDevice>().map { selection ->
        val index = selection.parent.devices.value.indexOfFirst { it.selectionUUID == selection.selectionUUID }

        selection.parent.add(StateChain.unpackDevice(StateChain.packDevice(selection.device)), index)

        return@map selection
    }.apply {
        if (isNotEmpty()) return true
    }

    if (SelectionManager.selections.value.any { it is Selectable.GroupChainItem }) {
        val selected = SelectionManager.selections.value.filterIsInstance<Selectable.GroupChainItem>().sortedByDescending { it.groupIndex }
        val highest = selected.maxBy { it.groupIndex }.groupIndex

        selected.forEach {
            when (it.parent) {
                is GroupChainDevice -> {
                    it.parent.duplicateGroup(it.groupIndex, highest + 1)
                }

                is MultiGroupChainDevice -> {
                    it.parent.duplicateGroup(it.groupIndex, highest + 1)
                }
            }
        }

        SelectionManager.selections.update {
            SelectionManager.selections.value.filterIsInstance<Selectable.GroupChainItem>().mapIndexed { index, it ->
                it.copy(
                    groupIndex = highest + 1 + index
                )
            }
        }

        return true
    }

    return false
}