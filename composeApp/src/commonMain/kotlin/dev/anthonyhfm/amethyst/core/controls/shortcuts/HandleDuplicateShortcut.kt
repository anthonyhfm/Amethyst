package dev.anthonyhfm.amethyst.core.controls.shortcuts

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

fun handleDuplicateShortcut(): Boolean {
    val selections = SelectionManager.selections.value

    // PianoRollNote: duplicate selected notes
    val pianoRollNotes = selections.filterIsInstance<Selectable.PianoRollNote>()
    if (pianoRollNotes.isNotEmpty()) {
        val pianoRollMode = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.mode.value as? dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
        pianoRollMode?.duplicateSelectedNotes()
        return true
    }

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
    val selectedChainDevices = selections.filterIsInstance<Selectable.ChainDevice>()
    if (selectedChainDevices.isNotEmpty()) {
        selectedChainDevices.groupBy { it.parent }.forEach { (parentChain, deviceSelections) ->
            val sortedSelections = deviceSelections.map { sel ->
                val index = parentChain.devices.value.indexOfFirst { it.selectionUUID == sel.device.selectionUUID }
                index to sel.device
            }.filter { it.first >= 0 }.sortedBy { it.first }

            if (sortedSelections.isEmpty()) return@forEach

            if (sortedSelections.size == 1) {
                val (index, device) = sortedSelections.first()
                val duplicate = StateChain.unpackDevice(StateChain.packDevice(device))
                parentChain.add(duplicate, index + 1)
                SelectionManager.clear()
                SelectionManager.select(
                    Selectable.ChainDevice(parent = parentChain, device = duplicate),
                    single = true
                )
            } else {
                val duplicates = sortedSelections.map { (_, device) ->
                    StateChain.unpackDevice(StateChain.packDevice(device))
                }
                val maxIndex = sortedSelections.maxOf { it.first }
                parentChain.addAll(duplicates, atIndex = maxIndex + 1)
                SelectionManager.clear()
                duplicates.forEach { dup ->
                    SelectionManager.select(
                        Selectable.ChainDevice(parent = parentChain, device = dup),
                        single = false
                    )
                }
            }
        }
        return true
    }

    return false
}
