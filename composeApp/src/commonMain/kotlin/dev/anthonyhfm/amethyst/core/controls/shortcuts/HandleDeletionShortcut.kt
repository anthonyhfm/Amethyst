package dev.anthonyhfm.amethyst.core.controls.shortcuts

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import kotlinx.coroutines.flow.update

fun handleDeletionShortcut(): Boolean {
    val selections = SelectionManager.selections.value

    when {
        selections.any { it is Selectable.GroupChainItem } -> {
            val groupItems = selections.filterIsInstance<Selectable.GroupChainItem>()

            // Group by parent device — handle each compound device independently
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

            // Auto-select nearest remaining group after deletion
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

            sortedDevicesToDelete.forEach { (chainDevice, chain, _) ->
                chain.remove(chainDevice.device.selectionUUID)
            }

            SelectionManager.clear()

            // After deletion, auto-select the nearest remaining device
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
            selections.filterIsInstance<Selectable.GradientStep>().forEach { step ->
                if (step.parent.state.value.gradientData.size - 1 < 1) return@forEach

                step.parent.state.update {
                    it.copy(
                        gradientData = it.gradientData.toMutableList().apply {
                            removeAll { it.selectionUUID == step.selectionUUID }
                        }
                    )
                }
            }

            return true
        }
    }

    return false
}
