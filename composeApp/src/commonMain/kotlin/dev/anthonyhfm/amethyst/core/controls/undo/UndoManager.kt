package dev.anthonyhfm.amethyst.core.controls.undo

object UndoManager {
    private val undoStack: MutableList<UndoableAction> = mutableListOf()
    private val redoStack: MutableList<UndoableAction> = mutableListOf()

    fun addAction(action: UndoableAction) {
        undoStack.add(action)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeAt(undoStack.lastIndex)

            when (action) {
                is UndoableAction.ChainDeviceCreation -> {
                    val deviceIndex = action.parent.devices.value.indexOfFirst {
                        it.selectionUUID == action.device.selectionUUID
                    }
                    if (deviceIndex != -1) {
                        action.parent.remove(deviceIndex, fromUser = false)
                    }
                    redoStack.add(action)
                }

                is UndoableAction.ChainDeviceRemoval -> {
                    action.parent.add(action.device, fromUser = false)
                    redoStack.add(action)
                }

                is UndoableAction.MovedChainDevice -> {
                    action.chainAfter.remove(action.device.selectionUUID, fromUser = false)

                    if (action.fromIndex >= 0 && action.fromIndex <= action.chainBefore.devices.value.size) {
                        action.chainBefore.add(action.device, action.fromIndex, fromUser = false)
                    } else {
                        action.chainBefore.add(action.device, fromUser = false)
                    }

                    redoStack.add(action)
                }

                is UndoableAction.KeyframeCreation -> {
                    action.device.removeFrameInternal(action.frameIndex)
                    redoStack.add(action)
                }

                is UndoableAction.KeyframeDeletion -> {
                    action.device.addFrameInternal(action.frameIndex, action.frame)
                    redoStack.add(action)
                }

                is UndoableAction.KeyframeDuplication -> {
                    action.device.removeFrameInternal(action.duplicatedIndex)
                    redoStack.add(action)
                }

                is UndoableAction.MultiKeyframeDuplication -> {
                    action.duplications.sortedByDescending { it.duplicatedIndex }.forEach { duplication ->
                        action.device.removeFrameInternal(duplication.duplicatedIndex)
                    }
                    redoStack.add(action)
                }

                is UndoableAction.MultiKeyframeDeletion -> {
                    action.deletions.sortedBy { it.frameIndex }.forEach { deletion ->
                        action.device.addFrameInternal(deletion.frameIndex, deletion.frame)
                    }
                    redoStack.add(action)
                }

                is UndoableAction.KeyframePaste -> {
                    // Remove pasted frames in reverse order to maintain correct indices
                    action.pastedFrames.sortedByDescending { it.frameIndex }.forEach { pasteInfo ->
                        action.device.removeFrameInternal(pasteInfo.frameIndex)
                    }
                    redoStack.add(action)
                }
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.removeAt(redoStack.lastIndex)

            when (action) {
                is UndoableAction.ChainDeviceCreation -> {
                    action.parent.add(action.device, fromUser = false)
                    undoStack.add(action)
                }

                is UndoableAction.ChainDeviceRemoval -> {
                    val deviceIndex = action.parent.devices.value.indexOfFirst {
                        it.selectionUUID == action.device.selectionUUID
                    }
                    if (deviceIndex != -1) {
                        action.parent.remove(deviceIndex, fromUser = false)
                    }
                    undoStack.add(action)
                }

                is UndoableAction.MovedChainDevice -> {
                    action.chainBefore.remove(action.device.selectionUUID, fromUser = false)
                    action.chainAfter.add(action.device, action.toIndex, fromUser = false)
                    undoStack.add(action)
                }

                is UndoableAction.KeyframeCreation -> {
                    action.device.addFrameInternal(action.frameIndex, action.frame)
                    undoStack.add(action)
                }

                is UndoableAction.KeyframeDeletion -> {
                    action.device.removeFrameInternal(action.frameIndex)
                    undoStack.add(action)
                }

                is UndoableAction.KeyframeDuplication -> {
                    action.device.duplicateFrameInternal(action.originalIndex, action.duplicatedIndex)
                    undoStack.add(action)
                }

                is UndoableAction.MultiKeyframeDuplication -> {
                    action.duplications.forEach { duplication ->
                        action.device.addFrameInternal(duplication.duplicatedIndex, duplication.duplicatedFrame)
                    }
                    undoStack.add(action)
                }

                is UndoableAction.MultiKeyframeDeletion -> {
                    action.deletions.sortedByDescending { it.frameIndex }.forEach { deletion ->
                        action.device.removeFrameInternal(deletion.frameIndex)
                    }
                    undoStack.add(action)
                }

                is UndoableAction.KeyframePaste -> {
                    // Re-paste frames in original order
                    action.pastedFrames.forEach { pasteInfo ->
                        action.device.addFrameInternal(pasteInfo.frameIndex, pasteInfo.frame)
                    }
                    undoStack.add(action)
                }
            }
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}