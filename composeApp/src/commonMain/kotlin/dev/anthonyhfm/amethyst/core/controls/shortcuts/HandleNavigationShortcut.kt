package dev.anthonyhfm.amethyst.core.controls.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.defaults.PerformanceWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LightsChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LayoutWorkspaceMode

fun handleNavigationShortcut(keyEvent: KeyEvent): Boolean {
    // Alt+← / Alt+→ — switch workspace mode
    if (keyEvent.isAltPressed && WorkspaceRepository.mode.value.selectableMode) {
        val selectableModes = listOf(
            PerformanceWorkspaceMode(),
            TimelineWorkspaceMode(),
            LightsChainWorkspaceMode(),
            SamplingChainWorkspaceMode(),
            LayoutWorkspaceMode(),
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

        return false
    }

    val currentSelections = SelectionManager.selections.value
    val anchor = currentSelections.lastOrNull() ?: return false

    // ↑/↓ — navigate group list when a GroupChainItem is selected
    if (keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionDown) {
        if (anchor is Selectable.GroupChainItem) {
            val (openedIdx, groups) = ChainNavigator.getGroupsInfo(anchor.parent) ?: return false
            val newIdx = if (keyEvent.key == Key.DirectionUp) openedIdx - 1 else openedIdx + 1
            if (newIdx < 0 || newIdx > groups.lastIndex) return true // consumed, at boundary
            ChainNavigator.openGroupInDevice(anchor.parent, newIdx)
            SelectionManager.select(
                Selectable.GroupChainItem(parent = anchor.parent, groupIndex = newIdx),
                single = true
            )
            return true
        }
        return false
    }

    // ←/→ — hierarchical chain traversal
    if (keyEvent.key != Key.DirectionLeft && keyEvent.key != Key.DirectionRight) return false

    val goRight = keyEvent.key == Key.DirectionRight
    val isShift = keyEvent.isShiftPressed

    return when (anchor) {
        is Selectable.ChainDevice -> handleChainDeviceNavigation(anchor, goRight, isShift)
        is Selectable.GroupChainItem -> handleGroupChainItemNavigation(anchor, goRight)
        else -> false
    }
}

/**
 * Navigate from a ChainDevice selection.
 * - goRight=true:  enter compound device OR move to next sibling OR exit inner chain
 * - goRight=false: enter prev compound device from its end OR move to prev sibling OR exit inner chain
 */
private fun handleChainDeviceNavigation(
    anchor: Selectable.ChainDevice,
    goRight: Boolean,
    isShift: Boolean,
): Boolean {
    val chain = anchor.parent
    val device = anchor.device
    val devices = chain.devices.value
    val idx = devices.indexOfFirst { it.selectionUUID == device.selectionUUID }
    if (idx < 0) return false

    if (goRight) {
        // If current device is compound: enter it
        if (ChainNavigator.isGroupDevice(device)) {
            val (openedIdx, groups) = ChainNavigator.getGroupsInfo(device)!!
            if (groups.isNotEmpty()) {
                SelectionManager.select(
                    Selectable.GroupChainItem(parent = device, groupIndex = openedIdx),
                    single = true
                )
                return true
            }
            // Empty groups — fall through to normal next-sibling logic
        } else if (device is ChokeChainDevice) {
            val inner = ChainNavigator.getInnerChainOf(device)
            if (inner != null && inner.devices.value.isNotEmpty()) {
                SelectionManager.select(
                    Selectable.ChainDevice(parent = inner, device = inner.devices.value.first()),
                    single = true
                )
                return true
            }
            // Empty choke chain — fall through
        }

        // Normal device or compound with empty content: move to next sibling
        val nextIdx = idx + 1
        if (nextIdx < devices.size) {
            val targetDevice = devices[nextIdx]
            SelectionManager.select(
                Selectable.ChainDevice(parent = chain, device = targetDevice),
                single = !isShift
            )
            return true
        }

        // At the end of chain: try to exit upward
        return exitChainForward(chain)
    } else {
        // goLeft
        if (idx == 0) {
            // At beginning of chain: exit upward
            return exitChainBackward(chain)
        }

        val prevDevice = devices[idx - 1]

        // If Shift is held, just extend selection to previous sibling (no compound traversal)
        if (isShift) {
            SelectionManager.select(
                Selectable.ChainDevice(parent = chain, device = prevDevice),
                single = false
            )
            return true
        }

        // Enter prev compound device from its tail
        if (ChainNavigator.isGroupDevice(prevDevice)) {
            val (openedIdx, groups) = ChainNavigator.getGroupsInfo(prevDevice)!!
            if (groups.isNotEmpty()) {
                val inner = groups.getOrNull(openedIdx)?.chain
                if (inner != null && inner.devices.value.isNotEmpty()) {
                    SelectionManager.select(
                        Selectable.ChainDevice(parent = inner, device = inner.devices.value.last()),
                        single = true
                    )
                    return true
                }
                // Empty inner chain: land on GroupChainItem
                SelectionManager.select(
                    Selectable.GroupChainItem(parent = prevDevice, groupIndex = openedIdx),
                    single = true
                )
                return true
            }
            // No groups — fall through to normal prev
        } else if (prevDevice is ChokeChainDevice) {
            val inner = ChainNavigator.getInnerChainOf(prevDevice)
            if (inner != null && inner.devices.value.isNotEmpty()) {
                SelectionManager.select(
                    Selectable.ChainDevice(parent = inner, device = inner.devices.value.last()),
                    single = true
                )
                return true
            }
            // Empty choke — fall through to select choke device itself
        }

        SelectionManager.select(
            Selectable.ChainDevice(parent = chain, device = prevDevice),
            single = true
        )
        return true
    }
}

/**
 * Navigate from a GroupChainItem selection.
 * - goRight: enter the group's inner chain (first child)
 * - goLeft:  go back to the compound device itself in the outer chain
 */
private fun handleGroupChainItemNavigation(
    anchor: Selectable.GroupChainItem,
    goRight: Boolean,
): Boolean {
    if (goRight) {
        val inner = ChainNavigator.getInnerChainOf(anchor.parent)
        if (inner != null && inner.devices.value.isNotEmpty()) {
            SelectionManager.select(
                Selectable.ChainDevice(parent = inner, device = inner.devices.value.first()),
                single = true
            )
            return true
        }
        // Empty inner chain: exit to next sibling after compound device
        val outerChain = ChainNavigator.findOuterChainOf(anchor.parent) ?: return false
        val compoundIdx = outerChain.devices.value.indexOfFirst {
            it.selectionUUID == anchor.parent.selectionUUID
        }
        if (compoundIdx < 0) return false
        val nextIdx = compoundIdx + 1
        if (nextIdx < outerChain.devices.value.size) {
            SelectionManager.select(
                Selectable.ChainDevice(parent = outerChain, device = outerChain.devices.value[nextIdx]),
                single = true
            )
            return true
        }
        return exitChainForward(outerChain)
    } else {
        // goLeft: go back to compound device in outer chain
        val outerChain = ChainNavigator.findOuterChainOf(anchor.parent) ?: return false
        SelectionManager.select(
            Selectable.ChainDevice(parent = outerChain, device = anchor.parent),
            single = true
        )
        return true
    }
}

/**
 * Exit forward from the last position in an inner chain:
 * moves to the next sibling after the compound device in the outer chain.
 * Recurses upward through deeply nested chains.
 */
private fun exitChainForward(innerChain: Chain): Boolean {
    val outer = ChainNavigator.findOuterContextOf(innerChain) ?: return false
    val nextIdx = outer.compoundIndex + 1
    if (nextIdx < outer.outerChain.devices.value.size) {
        SelectionManager.select(
            Selectable.ChainDevice(parent = outer.outerChain, device = outer.outerChain.devices.value[nextIdx]),
            single = true
        )
        return true
    }
    return exitChainForward(outer.outerChain)
}

/**
 * Exit backward from the first position in an inner chain:
 * moves to the GroupChainItem (Group/Multi) or to the compound device itself (Choke).
 */
private fun exitChainBackward(innerChain: Chain): Boolean {
    val outer = ChainNavigator.findOuterContextOf(innerChain) ?: return false
    val compoundDevice = outer.compoundDevice
    if (ChainNavigator.isGroupDevice(compoundDevice)) {
        val (openedIdx, _) = ChainNavigator.getGroupsInfo(compoundDevice) ?: return false
        SelectionManager.select(
            Selectable.GroupChainItem(parent = compoundDevice, groupIndex = openedIdx),
            single = true
        )
    } else {
        // Choke: go to the compound device itself
        SelectionManager.select(
            Selectable.ChainDevice(parent = outer.outerChain, device = compoundDevice),
            single = true
        )
    }
    return true
}
