package dev.anthonyhfm.amethyst.workspace.modes.defaults

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

object ChainModeKeyHandler {
    fun handleKeyInput(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.G -> {
                    if (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) {
                        return handleGroup()
                    }
                }

                Key.U -> {
                    if (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) {
                        return handleUngroup()
                    }
                }

                Key.Escape -> {
                    SelectionManager.clear()
                    return true
                }
            }
        }

        return false
    }

    private fun handleGroup(): Boolean {
        val selections = SelectionManager.selections.value.filter { it is Selectable.ChainDevice }

        if (selections.isNotEmpty()) {
            val chainDevices = selections.map { it as Selectable.ChainDevice }
            val parentChain = chainDevices.firstOrNull()?.parent

            if (parentChain != null && chainDevices.all { it.parent == parentChain }) {
                val deviceIndices = chainDevices.map { chainDevice ->
                    val index = parentChain.devices.value.indexOfFirst {
                        it.selectionUUID == chainDevice.device.selectionUUID
                    }
                    Pair(chainDevice, index)
                }.filter { it.second >= 0 }.sortedBy { it.second }

                if (deviceIndices.isNotEmpty()) {
                    val firstIndex = deviceIndices.first().second

                    val group = StateChain.unpackDevice(
                        GroupChainDeviceState(
                            groups = listOf(
                                Group(
                                    name = "Group #",
                                    stateChain = StateChain(
                                        devices = deviceIndices.map {
                                            StateChain.packDevice(it.first.device)
                                        }
                                    )
                                )
                            ),
                        )
                    ) as GroupChainDevice

                    val removedDevices = deviceIndices.map { (chainDevice, index) ->
                        UndoableAction.RemovedDeviceInfo(
                            device = chainDevice.device,
                            originalIndex = index
                        )
                    }

                    deviceIndices.reversed().forEach { (chainDevice, _) ->
                        parentChain.remove(chainDevice.device.selectionUUID, fromUser = false)
                    }

                    parentChain.add(group, firstIndex, fromUser = false)

                    UndoManager.addAction(
                        UndoableAction.ChainDeviceGrouping(
                            parent = parentChain,
                            groupDevice = group,
                            insertionIndex = firstIndex,
                            removedDevices = removedDevices
                        )
                    )

                    SelectionManager.clear()
                    SelectionManager.select(
                        Selectable.ChainDevice(parent = parentChain, device = group),
                        single = true
                    )

                    return true
                }
            }
        }
        return false
    }

    private fun handleUngroup(): Boolean {
        val selection = SelectionManager.selections.value
            .filterIsInstance<Selectable.ChainDevice>()
            .firstOrNull { it.device is GroupChainDevice }
            ?: return false

        val parentChain = selection.parent
        val groupDevice = selection.device as GroupChainDevice
        val groupIndex = parentChain.devices.value.indexOfFirst {
            it.selectionUUID == groupDevice.selectionUUID
        }
        if (groupIndex < 0) return false

        // Extract devices from the first group entry
        val extractedDevices = groupDevice.state.value.groups
            .firstOrNull()?.stateChain?.devices
            ?.mapIndexed { i, stateDevice ->
                UndoableAction.RemovedDeviceInfo(
                    device = StateChain.unpackDevice(stateDevice),
                    originalIndex = groupIndex + i
                )
            } ?: emptyList()

        parentChain.remove(groupDevice.selectionUUID, fromUser = false)
        extractedDevices.sortedBy { it.originalIndex }.forEach { info ->
            val safeIndex = info.originalIndex.coerceIn(0, parentChain.devices.value.size)
            parentChain.add(info.device, atIndex = safeIndex, fromUser = false)
        }

        UndoManager.addAction(
            UndoableAction.ChainDeviceUngrouping(
                parent = parentChain,
                groupDevice = groupDevice,
                groupIndex = groupIndex,
                extractedDevices = extractedDevices
            )
        )

        SelectionManager.clear()
        if (extractedDevices.isNotEmpty()) {
            SelectionManager.select(
                Selectable.ChainDevice(
                    parent = parentChain,
                    device = extractedDevices.first().device
                ),
                single = true
            )
        }

        return true
    }
}