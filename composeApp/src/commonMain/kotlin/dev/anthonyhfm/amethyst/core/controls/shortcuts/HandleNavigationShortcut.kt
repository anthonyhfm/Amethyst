package dev.anthonyhfm.amethyst.core.controls.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.update

fun handleNavigationShortcut(keyEvent: KeyEvent): Boolean {
    val currentSelections = SelectionManager.selections.value
    val chainDeviceSelections = currentSelections.filterIsInstance<Selectable.ChainDevice>()
    val groupChainItemSelections = currentSelections.filterIsInstance<Selectable.GroupChainItem>()

    if (keyEvent.isAltPressed && WorkspaceRepository.mode.value.selectable) {
        val selectableModes = listOf(
            WorkspaceContract.WorkspaceMode.Layout(),
            WorkspaceContract.WorkspaceMode.Preview(),
            WorkspaceContract.WorkspaceMode.LightsChain(),
            WorkspaceContract.WorkspaceMode.SamplingChain(),
            WorkspaceContract.WorkspaceMode.Timeline(),
        )

        if (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight) {
            val currentMode = WorkspaceRepository.mode.value
            val currentIndex = selectableModes.indexOfFirst { it::class == currentMode::class }
            val nextIndex = if (keyEvent.key == Key.DirectionLeft) {
                if (currentIndex - 1 < 0) selectableModes.size - 1 else currentIndex - 1
            } else {
                if (currentIndex + 1 >= selectableModes.size) 0 else currentIndex + 1
            }

            WorkspaceRepository.switchMode(selectableModes[nextIndex])

            return true
        }

        return true
    }

    if (chainDeviceSelections.size == 1) {
        val currentSelection = chainDeviceSelections.first()
        val chain = currentSelection.parent
        val currentIndex = chain.devices.value.indexOfFirst { it.selectionUUID == currentSelection.device.selectionUUID }
        
        if (currentIndex >= 0) {
            when (keyEvent.key) {
                Key.DirectionLeft -> {
                    val previousIndex = currentIndex - 1
                    if (previousIndex >= 0) {
                        val previousDevice = chain.devices.value[previousIndex]
                        SelectionManager.select(
                            Selectable.ChainDevice(
                                parent = chain,
                                device = previousDevice
                            ),
                            single = true
                        )
                        return true
                    }
                }

                Key.DirectionRight -> {
                    val nextIndex = currentIndex + 1
                    if (nextIndex < chain.devices.value.size) {
                        val nextDevice = chain.devices.value[nextIndex]
                        SelectionManager.select(
                            Selectable.ChainDevice(
                                parent = chain,
                                device = nextDevice
                            ),
                            single = true
                        )
                        return true
                    }
                }
            }
        }
    }

    // Handle GroupChainItem navigation (up/down)
    if (groupChainItemSelections.size == 1) {
        val currentSelection = groupChainItemSelections.first()
        val parentDevice = currentSelection.parent
        val currentGroupIndex = currentSelection.groupIndex

        when (keyEvent.key) {
            Key.DirectionUp -> {
                // Navigate to previous group (up)
                val previousGroupIndex = currentGroupIndex - 1
                if (previousGroupIndex >= 0) {
                    // Update the openedGroupIndex in the parent device's state
                    when (parentDevice) {
                        is dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice -> {
                            parentDevice.state.update { it.copy(openedGroupIndex = previousGroupIndex) }
                        }
                        is dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice -> {
                            parentDevice.state.update { it.copy(openedGroupIndex = previousGroupIndex) }
                        }
                    }

                    SelectionManager.select(
                        Selectable.GroupChainItem(
                            parent = parentDevice,
                            groupIndex = previousGroupIndex
                        ),
                        single = true
                    )
                    return true
                }
            }

            Key.DirectionDown -> {
                // Navigate to next group (down)
                val nextGroupIndex = currentGroupIndex + 1
                val maxGroupIndex = when (parentDevice) {
                    is dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice ->
                        parentDevice.state.value.groups.size - 1
                    is dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice ->
                        parentDevice.state.value.groups.size - 1
                    else -> -1
                }

                if (nextGroupIndex <= maxGroupIndex) {
                    // Update the openedGroupIndex in the parent device's state
                    when (parentDevice) {
                        is dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice -> {
                            parentDevice.state.update { it.copy(openedGroupIndex = nextGroupIndex) }
                        }
                        is dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice -> {
                            parentDevice.state.update { it.copy(openedGroupIndex = nextGroupIndex) }
                        }
                    }

                    SelectionManager.select(
                        Selectable.GroupChainItem(
                            parent = parentDevice,
                            groupIndex = nextGroupIndex
                        ),
                        single = true
                    )
                    return true
                }
            }
        }
    }

    // Original navigation logic for other cases (fallback)
    return false
}