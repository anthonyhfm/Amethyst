package dev.anthonyhfm.amethyst.core.controls.shortcuts

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

fun handleDuplicateShortcut(): Boolean {
    SelectionManager.selections.value.filterIsInstance<Selectable.ChainDevice>().map { selection ->
        val index = selection.parent.devices.value.indexOfFirst { it.selectionUUID == selection.selectionUUID }

        selection.parent.add(StateChain.unpackDevice(StateChain.packDevice(selection.device)), index)

        return@map selection
    }.apply {
        if (isNotEmpty()) return true
    }

    return false
}