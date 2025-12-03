package dev.anthonyhfm.amethyst.workspace.modes.chain

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
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
                                        Selectable.ChainDevice(
                                            parent = parentChain,
                                            device = group
                                        ),
                                        single = true
                                    )
                                    
                                    return true
                                }
                            }
                        }
                    }
                }
                
                Key.Backspace, Key.Delete -> {
                    val selections = SelectionManager.selections.value.filter { it is Selectable.ChainDevice }

                    if (selections.isNotEmpty()) {
                        val devicesToDelete = selections.map { selection ->
                            val chainDevice = selection as Selectable.ChainDevice
                            val chain = chainDevice.parent
                            val deviceIndex = chain.devices.value.indexOfFirst { it.selectionUUID == chainDevice.device.selectionUUID }
                            Triple(chainDevice, chain, deviceIndex)
                        }.filter { it.third >= 0 }

                        val sortedDevicesToDelete = devicesToDelete.sortedByDescending { it.third }

                        sortedDevicesToDelete.forEach { (chainDevice, chain, _) ->
                            chain.remove(chainDevice.device.selectionUUID)
                        }

                        SelectionManager.clear()

                        if (sortedDevicesToDelete.isNotEmpty()) {
                            val (_, firstChain, firstIndex) = sortedDevicesToDelete.last() // Nimm das Device mit dem niedrigsten Index

                            val newSelectionIndex = when {
                                firstChain.devices.value.isEmpty() -> -1 // Keine Devices mehr vorhanden
                                firstIndex >= firstChain.devices.value.size -> firstChain.devices.value.size - 1 // Letztes Device wurde gelöscht
                                else -> firstIndex
                            }

                            if (newSelectionIndex >= 0 && newSelectionIndex < firstChain.devices.value.size) {
                                val newSelectedDevice = firstChain.devices.value[newSelectionIndex]
                                SelectionManager.select(
                                    Selectable.ChainDevice(
                                        parent = firstChain,
                                        device = newSelectedDevice
                                    ),
                                    single = true
                                )
                            }
                        }

                        if (devicesToDelete.isNotEmpty()) {
                            return true
                        }
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
}