package dev.anthonyhfm.amethyst.core.controls.shortcuts

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncCoordinator
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import kotlinx.coroutines.flow.update

fun handleDeletionShortcut(): Boolean {
    val selections = SelectionManager.selections.value

    when {
        selections.any { it is Selectable.PianoRollNote } -> {
            val pianoRollMode = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.mode.value as? dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
            pianoRollMode?.deleteSelectedNotes()
            return true
        }

        selections.any { it is Selectable.GroupChainItem } -> {
            val groupItems = selections.filterIsInstance<Selectable.GroupChainItem>()

            groupItems.groupBy { it.parent.selectionUUID }.forEach { (_, items) ->
                val parent = items.first().parent
                val indices = items.map { it.groupIndex }.sorted()

                when (parent) {
                    is GroupChainDevice -> parent.removeGroups(indices)
                    is MultiGroupChainDevice -> parent.removeGroups(indices)
                    else -> return@forEach
                }
            }

            SelectionManager.clear()

            val firstItem = groupItems.first()
            val parent = firstItem.parent
            val remainingGroups = when (parent) {
                is GroupChainDevice -> parent.state.value.groups
                is MultiGroupChainDevice -> parent.state.value.groups
                else -> emptyList()
            }
            if (remainingGroups.isNotEmpty()) {
                val deletedMin = groupItems.minOf { it.groupIndex }
                val newIdx = deletedMin.coerceAtMost(remainingGroups.lastIndex)
                SelectionManager.select(
                    Selectable.GroupChainItem(parent = parent, groupIndex = newIdx),
                    single = true
                )
            }

            return true
        }

        selections.any { it is Selectable.ChainDevice } -> {
            val chainDevices = selections.filterIsInstance<Selectable.ChainDevice>()

            val devicesToDelete = chainDevices.map { chainDevice ->
                val chain = chainDevice.parent
                val deviceIndex = chain.devices.value.indexOfFirst { it.selectionUUID == chainDevice.device.selectionUUID }
                Triple(chainDevice, chain, deviceIndex)
            }.filter { it.third >= 0 }

            if (devicesToDelete.isEmpty()) return false

            val sortedDevicesToDelete = devicesToDelete.sortedByDescending { it.third }

            if (sortedDevicesToDelete.size == 1) {
                val (chainDevice, chain, _) = sortedDevicesToDelete.first()
                chain.remove(chainDevice.device.selectionUUID)
            } else {
                val removals = sortedDevicesToDelete.map { (chainDevice, chain, deviceIndex) ->
                    UndoableAction.ChainDeviceRemoval(
                        parent = chain,
                        device = chainDevice.device,
                        originalIndex = deviceIndex
                    )
                }

                sortedDevicesToDelete.forEach { (chainDevice, chain, _) ->
                    chain.remove(chainDevice.device.selectionUUID, fromUser = false)
                }

                UndoManager.addAction(UndoableAction.MultiChainDeviceRemoval(removals))
            }

            SelectionManager.clear()

            val (_, firstChain, firstIndex) = sortedDevicesToDelete.last()
            val newSelectionIndex = when {
                firstChain.devices.value.isEmpty() -> -1
                firstIndex >= firstChain.devices.value.size -> firstChain.devices.value.size - 1
                else -> firstIndex
            }

            if (newSelectionIndex >= 0) {
                SelectionManager.select(
                    Selectable.ChainDevice(
                        parent = firstChain,
                        device = firstChain.devices.value[newSelectionIndex]
                    ),
                    single = true
                )
            }

            return true
        }

        selections.any { it is Selectable.GradientStep } -> {
            val gradientSteps = selections.filterIsInstance<Selectable.GradientStep>()

            gradientSteps.groupBy { it.parent.selectionUUID }.forEach { (_, steps) ->
                val parent = steps.first().parent
                val idsToRemove = steps.map { it.selectionUUID }.toSet()
                val before = parent.state.value
                val remaining = before.gradientData.filterNot { it.selectionUUID in idsToRemove }
                if (remaining.size < 2 || remaining.size == before.gradientData.size) return@forEach

                val after = before.copy(gradientData = remaining)
                parent.state.value = after
                UndoManager.addAction(
                    UndoableAction.ChangeDeviceState(
                        device = parent,
                        beforeState = before,
                        afterState = after
                    )
                )
                ChainSyncCoordinator.onDeviceStateChanged(parent, after)

                val firstRemovedIndex = before.gradientData.indexOfFirst { it.selectionUUID in idsToRemove }
                val nextSelectionIndex = when {
                    after.gradientData.isEmpty() -> -1
                    firstRemovedIndex >= after.gradientData.size -> after.gradientData.lastIndex
                    else -> firstRemovedIndex
                }

                if (nextSelectionIndex >= 0) {
                    SelectionManager.select(
                        Selectable.GradientStep(parent, nextSelectionIndex),
                        single = true
                    )
                }
            }

            return true
        }
    }

    return false
}
