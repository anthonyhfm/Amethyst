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
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

object ChainModeKeyHandler {
    fun handleKeyInput(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.G -> {
                    // CTRL+G / CMD+G to wrap selected devices in a GroupChainDevice
                    if (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) {
                        val selections = SelectionManager.selections.value.filter { it is Selectable.ChainDevice }
                        
                        if (selections.size >= 2) {
                            val chainDevices = selections.map { it as Selectable.ChainDevice }
                            val parentChain = chainDevices.firstOrNull()?.parent
                            
                            // Check all devices are from the same parent chain
                            if (parentChain != null && chainDevices.all { it.parent == parentChain }) {
                                // Get indices and sort devices by their position in the chain
                                val deviceIndices = chainDevices.map { chainDevice ->
                                    val index = parentChain.devices.value.indexOfFirst { 
                                        it.selectionUUID == chainDevice.device.selectionUUID 
                                    }
                                    Pair(chainDevice, index)
                                }.filter { it.second >= 0 }.sortedBy { it.second }
                                
                                if (deviceIndices.isNotEmpty()) {
                                    val firstIndex = deviceIndices.first().second
                                    
                                    // Create a new GroupChainDevice
                                    val newGroupDevice = GroupChainDevice()
                                    
                                    // Create a group with the selected devices
                                    val newGroup = Group(
                                        name = "Chain #",
                                        chain = Chain().apply {
                                            // Add devices to the new group's chain
                                            deviceIndices.forEach { (chainDevice, _) ->
                                                // Pack and unpack to create a copy
                                                val deviceState = StateChain.packDevice(chainDevice.device)
                                                val deviceCopy = StateChain.unpackDevice(deviceState)
                                                add(deviceCopy)
                                            }
                                            
                                            signalExit = newGroupDevice.signalExit
                                        }
                                    )
                                    
                                    // Set the group in the GroupChainDevice
                                    newGroupDevice.state.value = newGroupDevice.state.value.copy(
                                        groups = listOf(newGroup),
                                        openedGroupIndex = 0
                                    )
                                    
                                    // Remove selected devices from parent chain (in reverse order to maintain indices)
                                    deviceIndices.reversed().forEach { (chainDevice, _) ->
                                        parentChain.remove(chainDevice.device.selectionUUID, fromUser = false)
                                    }
                                    
                                    // Add the GroupChainDevice at the position of the first selected device
                                    parentChain.add(newGroupDevice, firstIndex, fromUser = false)
                                    
                                    // Select the new GroupChainDevice
                                    SelectionManager.clear()
                                    SelectionManager.select(
                                        Selectable.ChainDevice(
                                            parent = parentChain,
                                            device = newGroupDevice
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