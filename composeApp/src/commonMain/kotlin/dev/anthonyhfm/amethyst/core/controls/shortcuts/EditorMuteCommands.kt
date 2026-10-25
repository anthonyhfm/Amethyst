package dev.anthonyhfm.amethyst.core.controls.shortcuts

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.GenericChainDevice

object EditorMuteCommands {
    fun setDevices(devices: List<GenericChainDevice<*>>, muted: Boolean): Boolean {
        return commit(changes = devices.distinct().filter { it.isMuted != muted }.map { device ->
            UndoableAction.MuteChange(
                before = device.isMuted,
                after = muted,
                apply = { device.setMuted(muted = it) },
            )
        })
    }

    fun toggleChains(chains: List<Chain>): Boolean {
        val muted = chains.any { !it.isMuted }
        return commit(changes = chains.distinct().filter { it.isMuted != muted }.map { chain ->
            UndoableAction.MuteChange(
                before = chain.isMuted,
                after = muted,
                apply = { chain.setMuted(muted = it) },
            )
        })
    }

    fun toggleSelection(): Boolean {
        val selections = SelectionManager.selections.value
        val chains = selections.filterIsInstance<Selectable.GroupChainItem>().mapNotNull { selection ->
            ChainNavigator.getGroupsInfo(device = selection.parent)?.second?.getOrNull(selection.groupIndex)?.chain
        }
        if (chains.isNotEmpty()) {
            return toggleChains(chains = chains)
        }
        val devices = selections.filterIsInstance<Selectable.ChainDevice>().map { it.device }
        return setDevices(devices = devices, muted = devices.any { !it.isMuted })
    }

    private fun commit(changes: List<UndoableAction.MuteChange>): Boolean {
        if (changes.isEmpty()) {
            return false
        }
        changes.forEach { it.apply(it.after) }
        UndoManager.addAction(action = UndoableAction.MuteSelection(changes = changes))
        return true
    }
}
