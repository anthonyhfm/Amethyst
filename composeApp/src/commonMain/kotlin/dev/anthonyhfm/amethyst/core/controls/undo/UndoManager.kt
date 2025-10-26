package dev.anthonyhfm.amethyst.core.controls.undo

import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.timeline.TimelineRepository

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
                    action.pastedFrames.sortedByDescending { it.frameIndex }.forEach { pasteInfo ->
                        action.device.removeFrameInternal(pasteInfo.frameIndex)
                    }
                    redoStack.add(action)
                }

                is UndoableAction.GroupCreation -> {
                    action.device.removeGroupInternal(action.groupIndex)
                    redoStack.add(action)
                }

                is UndoableAction.GroupDeletion -> {
                    action.device.addGroupInternal(action.groupIndex, action.group)
                    redoStack.add(action)
                }

                is UndoableAction.GroupDuplication -> {
                    action.device.removeGroupInternal(action.duplicatedIndex)
                    redoStack.add(action)
                }

                is UndoableAction.MultiGroupDuplication -> {
                    action.duplications.sortedByDescending { it.duplicatedIndex }.forEach { duplication ->
                        action.device.removeGroupInternal(duplication.duplicatedIndex)
                    }
                    redoStack.add(action)
                }

                is UndoableAction.MultiGroupDeletion -> {
                    action.deletions.sortedBy { it.groupIndex }.forEach { deletion ->
                        when (action.device) {
                            is GroupChainDevice -> action.device.addGroupInternal(deletion.groupIndex, deletion.group)
                            is MultiGroupChainDevice -> action.device.addGroupInternal(deletion.groupIndex, deletion.group)
                            else -> throw IllegalArgumentException("Unsupported device type for MultiGroupDeletion: ${action.device::class.simpleName}")
                        }
                    }
                    redoStack.add(action)
                }

                is UndoableAction.GroupPaste -> {
                    action.pastedGroups.sortedByDescending { it.groupIndex }.forEach { pasteInfo ->
                        action.device.removeGroupInternal(pasteInfo.groupIndex)
                    }
                    redoStack.add(action)
                }
                is UndoableAction.TimelineChange -> {
                    TimelineRepository.setTrackEntries(action.trackIndex, action.beforeEntries)

                    redoStack.add(action)
                }

                is UndoableAction.MultiGroupCreation -> {
                    action.device.removeGroupInternal(action.groupIndex)
                    redoStack.add(action)
                }

                is UndoableAction.MultiGroupDuplicationAction -> {
                    action.device.removeGroupInternal(action.duplicatedIndex)
                    redoStack.add(action)
                }

                is UndoableAction.MultiGroupMultiDuplication -> {
                    action.duplications.sortedByDescending { it.duplicatedIndex }.forEach { duplication ->
                        action.device.removeGroupInternal(duplication.duplicatedIndex)
                    }
                    redoStack.add(action)
                }

                is UndoableAction.MultiGroupPaste -> {
                    action.pastedGroups.sortedByDescending { it.groupIndex }.forEach { pasteInfo ->
                        action.device.removeGroupInternal(pasteInfo.groupIndex)
                    }
                    redoStack.add(action)
                }

                is UndoableAction.TimelineClipTrim -> {
                    val track = TimelineRepository.tracks.value.getOrNull(action.trackIndex) as? dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
                    track?.let {
                        it.entries.remove(action.trimmed.startTimeMs)
                        it.entries[action.original.startTimeMs] = action.original

                        TimelineRepository.setTrackEntries(action.trackIndex, it.entries.values.toList())
                    }
                    redoStack.add(action)
                }
                is UndoableAction.TimelineClipSplit -> {
                    val track = TimelineRepository.tracks.value.getOrNull(action.trackIndex) as? dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
                    track?.let {
                        action.left?.let { seg -> it.entries.remove(seg.startTimeMs) }
                        action.right?.let { seg -> it.entries.remove(seg.startTimeMs) }

                        it.entries[action.original.startTimeMs] = action.original
                        TimelineRepository.setTrackEntries(action.trackIndex, it.entries.values.toList())
                    }
                    redoStack.add(action)
                }

                is UndoableAction.TimelineClipDeletion -> {
                    val track = TimelineRepository.tracks.value.getOrNull(action.trackIndex) as? dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
                    track?.let {
                        it.entries[action.deleted.startTimeMs] = action.deleted
                        TimelineRepository.setTrackEntries(action.trackIndex, it.entries.values.toList())
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
                    action.pastedFrames.forEach { pasteInfo ->
                        action.device.addFrameInternal(pasteInfo.frameIndex, pasteInfo.frame)
                    }
                    undoStack.add(action)
                }

                is UndoableAction.GroupCreation -> {
                    action.device.addGroupInternal(action.groupIndex, action.group)
                    undoStack.add(action)
                }

                is UndoableAction.GroupDeletion -> {
                    action.device.removeGroupInternal(action.groupIndex)
                    undoStack.add(action)
                }

                is UndoableAction.GroupDuplication -> {
                    action.device.addGroupInternal(action.duplicatedIndex, action.duplicatedGroup)
                    undoStack.add(action)
                }

                is UndoableAction.MultiGroupDuplication -> {
                    action.duplications.forEach { duplication ->
                        action.device.addGroupInternal(duplication.duplicatedIndex, duplication.duplicatedGroup)
                    }
                    undoStack.add(action)
                }

                is UndoableAction.GroupPaste -> {
                    action.pastedGroups.forEach { pasteInfo ->
                        action.device.addGroupInternal(pasteInfo.groupIndex, pasteInfo.group)
                    }
                    undoStack.add(action)
                }

                is UndoableAction.MultiGroupCreation -> {
                    action.device.addGroupInternal(action.groupIndex, action.group)
                    undoStack.add(action)
                }

                is UndoableAction.MultiGroupDeletion -> {
                    action.deletions.sortedByDescending { it.groupIndex }.forEach { deletion ->
                        when (action.device) {
                            is GroupChainDevice -> action.device.removeGroupInternal(deletion.groupIndex)
                            is MultiGroupChainDevice -> action.device.removeGroupInternal(deletion.groupIndex)
                            else -> throw IllegalArgumentException("Unsupported device type for MultiGroupDeletion: ${action.device::class.simpleName}")
                        }
                    }
                    undoStack.add(action)
                }

                is UndoableAction.MultiGroupDuplicationAction -> {
                    action.device.addGroupInternal(action.duplicatedIndex, action.duplicatedGroup)
                    undoStack.add(action)
                }

                is UndoableAction.MultiGroupMultiDuplication -> {
                    action.duplications.forEach { duplication ->
                        action.device.addGroupInternal(duplication.duplicatedIndex, duplication.duplicatedGroup)
                    }
                    undoStack.add(action)
                }

                is UndoableAction.MultiGroupPaste -> {
                    action.pastedGroups.forEach { pasteInfo ->
                        action.device.addGroupInternal(pasteInfo.groupIndex, pasteInfo.group)
                    }
                    undoStack.add(action)
                }

                is UndoableAction.TimelineChange -> {
                    TimelineRepository.setTrackEntries(action.trackIndex, action.afterEntries)
                    undoStack.add(action)
                }

                is UndoableAction.TimelineClipTrim -> {
                    val track = TimelineRepository.tracks.value.getOrNull(action.trackIndex) as? dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
                    track?.let {
                        it.entries.remove(action.original.startTimeMs)
                        it.entries[action.trimmed.startTimeMs] = action.trimmed
                        TimelineRepository.setTrackEntries(action.trackIndex, it.entries.values.toList())
                    }
                    undoStack.add(action)
                }
                is UndoableAction.TimelineClipSplit -> {
                    val track = TimelineRepository.tracks.value.getOrNull(action.trackIndex) as? dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
                    track?.let {
                        it.entries.remove(action.original.startTimeMs)
                        action.left?.let { seg -> it.entries[seg.startTimeMs] = seg }
                        action.right?.let { seg -> it.entries[seg.startTimeMs] = seg }
                        TimelineRepository.setTrackEntries(action.trackIndex, it.entries.values.toList())
                    }
                    undoStack.add(action)
                }
                is UndoableAction.TimelineClipDeletion -> {
                    val track = TimelineRepository.tracks.value.getOrNull(action.trackIndex) as? dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
                    track?.let {
                        it.entries.remove(action.deleted.startTimeMs)
                        TimelineRepository.setTrackEntries(action.trackIndex, it.entries.values.toList())
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