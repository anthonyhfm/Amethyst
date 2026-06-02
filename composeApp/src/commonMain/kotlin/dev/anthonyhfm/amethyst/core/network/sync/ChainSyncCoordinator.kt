package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice

object ChainSyncCoordinator {
    private var broadcaster: ChainSyncBroadcaster? = null

    fun attach(broadcaster: ChainSyncBroadcaster) {
        this.broadcaster = broadcaster
    }

    fun detach(broadcaster: ChainSyncBroadcaster) {
        if (this.broadcaster === broadcaster) {
            this.broadcaster = null
        }
    }

    fun onDevicePlaced(chain: Chain, device: GenericChainDevice<*>, atIndex: Int) {
        broadcaster?.onDevicePlaced(chain, device, atIndex)
    }

    fun onDeviceRemoved(chain: Chain, deviceId: String) {
        broadcaster?.onDeviceRemoved(chain, deviceId)
    }

    fun onDeviceMoved(
        chainBefore: Chain,
        chainAfter: Chain,
        device: GenericChainDevice<*>,
        fromIndex: Int,
        toIndex: Int
    ) {
        broadcaster?.onDeviceMoved(chainBefore, chainAfter, device, fromIndex, toIndex)
    }

    fun onDeviceStateChanged(device: GenericChainDevice<*>, state: DeviceState) {
        broadcaster?.onDeviceStateChanged(device, state)
    }

    fun onGroupStateChanged(device: GenericChainDevice<*>, before: DeviceState, after: DeviceState) {
        broadcaster?.onGroupStateChanged(device, before, after)
    }

    fun onUndoAction(action: UndoableAction, isUndo: Boolean) {
        when (action) {
            is UndoableAction.ChainDeviceCreation -> {
                if (isUndo) {
                    onDeviceRemoved(action.parent, action.device.selectionUUID)
                } else {
                    onDevicePlaced(action.parent, action.device, action.creationIndex)
                }
            }

            is UndoableAction.ChainDeviceRemoval -> {
                if (isUndo) {
                    onDevicePlaced(action.parent, action.device, action.originalIndex)
                } else {
                    onDeviceRemoved(action.parent, action.device.selectionUUID)
                }
            }

            is UndoableAction.MovedChainDevice -> {
                if (isUndo) {
                    onDeviceMoved(
                        chainBefore = action.chainAfter,
                        chainAfter = action.chainBefore,
                        device = action.device,
                        fromIndex = action.toIndex,
                        toIndex = action.fromIndex
                    )
                } else {
                    onDeviceMoved(
                        chainBefore = action.chainBefore,
                        chainAfter = action.chainAfter,
                        device = action.device,
                        fromIndex = action.fromIndex,
                        toIndex = action.toIndex
                    )
                }
            }

            is UndoableAction.ChangeDeviceState<*> -> {
                onDeviceStateChanged(action.device, if (isUndo) action.beforeState else action.afterState)
            }

            is UndoableAction.GroupEditorStateChange<*> -> {
                onDeviceStateChanged(action.device, if (isUndo) action.beforeState else action.afterState)
            }

            is UndoableAction.WorkspaceBpmChange -> {
                WorkspaceSyncCoordinator.onBpmChanged(if (isUndo) action.beforeBpm else action.afterBpm)
            }

            is UndoableAction.WorkspaceMacrosChange -> {
                if (action.beforeMacros.size != action.afterMacros.size) {
                    WorkspaceSyncCoordinator.onMacrosChanged(if (isUndo) action.beforeMacros else action.afterMacros)
                }
            }

            else -> Unit
        }
    }
}
