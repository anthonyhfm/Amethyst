package dev.anthonyhfm.amethyst.core.controls.undo

import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow

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
                is UndoableAction.WorkspaceModeChange -> {
                    WorkspaceRepository.switchMode(action.beforeMode, undoable = false)
                    redoStack.add(action)
                }

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
                    val safeIndex = action.originalIndex.coerceIn(0, action.parent.devices.value.size)
                    action.parent.add(action.device, atIndex = safeIndex, fromUser = false)
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

                is UndoableAction.MidiTimelineChange -> {
                    TimelineRepository.setMidiTrackEntries(action.trackIndex, action.beforeEntries)
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
                        action.original?.let { orig ->
                            it.entries[orig.startTimeMs] = orig
                        }
                        TimelineRepository.setTrackEntries(action.trackIndex, it.entries.values.toList())
                    }
                    redoStack.add(action)
                }

                is UndoableAction.MidiTimelineClipSplit -> {
                    val track = TimelineRepository.tracks.value.getOrNull(action.trackIndex)
                    if (track is MidiTimelineTrack) {
                        action.left?.let { seg -> track.entries.remove(seg.startTimeMs) }
                        action.right?.let { seg -> track.entries.remove(seg.startTimeMs) }
                        action.original?.let { orig ->
                            track.entries[orig.startTimeMs] = orig
                        }
                        TimelineRepository.setMidiTrackEntries(action.trackIndex, track.entries.values.toList())
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
                is UndoableAction.ChangeDeviceState<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val device = action.device as dev.anthonyhfm.amethyst.devices.GenericChainDevice<dev.anthonyhfm.amethyst.devices.DeviceState>
                    // Zustand wiederherstellen
                    (device.state as kotlinx.coroutines.flow.MutableStateFlow<dev.anthonyhfm.amethyst.devices.DeviceState>).value = action.beforeState
                    redoStack.add(action)
                }

                is UndoableAction.PianoRollNoteMove -> {
                    // Undo: Revert notes to their original state
                    action.notesAfter.zip(action.notesBefore).forEach { (after, before) ->
                        action.onNoteUpdate(after, before)
                    }

                    // Update local state
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(
                                notes = currentEntry.notes.map { note ->
                                    action.notesAfter.zip(action.notesBefore).find { it.first == note }?.second ?: note
                                }
                            )
                        )
                    }

                    // Restore selection
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    action.notesBefore.forEach { beforeNote ->
                        dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.select(
                            dev.anthonyhfm.amethyst.core.controls.selection.Selectable.PianoRollNote(
                                action.trackIndex,
                                action.entryStartMs,
                                beforeNote
                            ),
                            single = false
                        )
                    }

                    redoStack.add(action)
                }

                is UndoableAction.PianoRollNoteCreation -> {
                    // Undo: Delete the created note
                    action.onNoteDelete(action.note)
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(notes = currentEntry.notes.filter { it != action.note })
                        )
                    }
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    redoStack.add(action)
                }

                is UndoableAction.PianoRollNoteDeletion -> {
                    // Undo: Re-add deleted notes
                    action.notes.forEach { note ->
                        action.onNoteAdd(note)
                    }
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(notes = currentEntry.notes + action.notes)
                        )
                    }
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    action.notes.forEach { note ->
                        dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.select(
                            dev.anthonyhfm.amethyst.core.controls.selection.Selectable.PianoRollNote(
                                action.trackIndex,
                                action.entryStartMs,
                                note
                            ),
                            single = false
                        )
                    }
                    redoStack.add(action)
                }

                is UndoableAction.PianoRollNoteDuplication -> {
                    // Undo: Delete duplicated notes
                    action.duplicates.forEach { note ->
                        action.onNoteDelete(note)
                    }
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(notes = currentEntry.notes.filter { note ->
                                !action.duplicates.contains(note)
                            })
                        )
                    }
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    redoStack.add(action)
                }

                is UndoableAction.PianoRollNoteResize -> {
                    // Undo: Revert notes to original size
                    action.notesAfter.zip(action.notesBefore).forEach { (after, before) ->
                        action.onNoteUpdate(after, before)
                    }
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(
                                notes = currentEntry.notes.map { note ->
                                    action.notesAfter.zip(action.notesBefore).find { it.first == note }?.second ?: note
                                }
                            )
                        )
                    }
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    action.notesBefore.forEach { beforeNote ->
                        dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.select(
                            dev.anthonyhfm.amethyst.core.controls.selection.Selectable.PianoRollNote(
                                action.trackIndex,
                                action.entryStartMs,
                                beforeNote
                            ),
                            single = false
                        )
                    }
                    redoStack.add(action)
                }

                is UndoableAction.PianoRollNoteColorChange -> {
                    // Undo: Revert notes to original color
                    action.notesAfter.zip(action.notesBefore).forEach { (after, before) ->
                        action.onNoteUpdate(after, before)
                    }
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(
                                notes = currentEntry.notes.map { note ->
                                    action.notesAfter.zip(action.notesBefore).find { it.first == note }?.second ?: note
                                }
                            )
                        )
                    }
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    action.notesBefore.forEach { beforeNote ->
                        dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.select(
                            dev.anthonyhfm.amethyst.core.controls.selection.Selectable.PianoRollNote(
                                action.trackIndex,
                                action.entryStartMs,
                                beforeNote
                            ),
                            single = false
                        )
                    }
                    redoStack.add(action)
                }

                is UndoableAction.TrackAddition -> {
                    TimelineRepository.removeTrack(action.trackIndex)
                    redoStack.add(action)
                }

                is UndoableAction.TrackRemoval -> {
                    TimelineRepository.insertTrack(action.trackIndex, action.track)
                    redoStack.add(action)
                }

                is UndoableAction.TrackDuplication -> {
                    TimelineRepository.removeTrack(action.duplicatedIndex)
                    redoStack.add(action)
                }
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.removeAt(redoStack.lastIndex)

            when (action) {
                is UndoableAction.WorkspaceModeChange -> {
                    WorkspaceRepository.switchMode(action.afterMode, undoable = false)
                    undoStack.add(action)
                }

                is UndoableAction.ChainDeviceCreation -> {
                    action.parent.add(action.device, atIndex = action.creationIndex, fromUser = false)
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
                    action.device.addFrameInternal(action.duplicatedIndex, action.duplicatedFrame)
                    undoStack.add(action)
                }

                is UndoableAction.MultiKeyframeDuplication -> {
                    action.duplications.sortedBy { it.duplicatedIndex }.forEach { duplication ->
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

                is UndoableAction.MidiTimelineChange -> {
                    TimelineRepository.setMidiTrackEntries(action.trackIndex, action.afterEntries)
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
                        action.original?.let { orig -> it.entries.remove(orig.startTimeMs) }
                        action.left?.let { seg -> it.entries[seg.startTimeMs] = seg }
                        action.right?.let { seg -> it.entries[seg.startTimeMs] = seg }
                        TimelineRepository.setTrackEntries(action.trackIndex, it.entries.values.toList())
                    }
                    undoStack.add(action)
                }
                is UndoableAction.MidiTimelineClipSplit -> {
                    val track = TimelineRepository.tracks.value.getOrNull(action.trackIndex)
                    if (track is MidiTimelineTrack) {
                        action.original?.let { orig -> track.entries.remove(orig.startTimeMs) }
                        action.left?.let { seg -> track.entries[seg.startTimeMs] = seg }
                        action.right?.let { seg -> track.entries[seg.startTimeMs] = seg }
                        TimelineRepository.setMidiTrackEntries(action.trackIndex, track.entries.values.toList())
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
                is UndoableAction.ChangeDeviceState<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val device = action.device as dev.anthonyhfm.amethyst.devices.GenericChainDevice<DeviceState>
                    device.state.value = action.afterState
                    undoStack.add(action)
                }

                is UndoableAction.PianoRollNoteMove -> {
                    // Redo: Apply notes to their new state
                    action.notesBefore.zip(action.notesAfter).forEach { (before, after) ->
                        action.onNoteUpdate(before, after)
                    }

                    // Update local state
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(
                                notes = currentEntry.notes.map { note ->
                                    action.notesBefore.zip(action.notesAfter).find { it.first == note }?.second ?: note
                                }
                            )
                        )
                    }

                    // Restore selection
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    action.notesAfter.forEach { afterNote ->
                        dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.select(
                            dev.anthonyhfm.amethyst.core.controls.selection.Selectable.PianoRollNote(
                                action.trackIndex,
                                action.entryStartMs,
                                afterNote
                            ),
                            single = false
                        )
                    }

                    undoStack.add(action)
                }

                is UndoableAction.PianoRollNoteCreation -> {
                    // Redo: Re-add the created note
                    action.onNoteAdd(action.note)
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(notes = currentEntry.notes + action.note)
                        )
                    }
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.select(
                        dev.anthonyhfm.amethyst.core.controls.selection.Selectable.PianoRollNote(
                            action.trackIndex,
                            action.entryStartMs,
                            action.note
                        ),
                        single = true
                    )
                    undoStack.add(action)
                }

                is UndoableAction.PianoRollNoteDeletion -> {
                    // Redo: Delete notes again
                    action.notes.forEach { note ->
                        action.onNoteDelete(note)
                    }
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(notes = currentEntry.notes.filter { note ->
                                !action.notes.contains(note)
                            })
                        )
                    }
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    undoStack.add(action)
                }

                is UndoableAction.PianoRollNoteDuplication -> {
                    // Redo: Re-add duplicated notes
                    action.duplicates.forEach { note ->
                        action.onNoteAdd(note)
                    }
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(notes = currentEntry.notes + action.duplicates)
                        )
                    }
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    action.duplicates.forEach { note ->
                        dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.select(
                            dev.anthonyhfm.amethyst.core.controls.selection.Selectable.PianoRollNote(
                                action.trackIndex,
                                action.entryStartMs,
                                note
                            ),
                            single = false
                        )
                    }
                    undoStack.add(action)
                }

                is UndoableAction.PianoRollNoteResize -> {
                    // Redo: Apply resize again
                    action.notesBefore.zip(action.notesAfter).forEach { (before, after) ->
                        action.onNoteUpdate(before, after)
                    }
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(
                                notes = currentEntry.notes.map { note ->
                                    action.notesBefore.zip(action.notesAfter).find { it.first == note }?.second ?: note
                                }
                            )
                        )
                    }
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    action.notesAfter.forEach { afterNote ->
                        dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.select(
                            dev.anthonyhfm.amethyst.core.controls.selection.Selectable.PianoRollNote(
                                action.trackIndex,
                                action.entryStartMs,
                                afterNote
                            ),
                            single = false
                        )
                    }
                    undoStack.add(action)
                }

                is UndoableAction.PianoRollNoteColorChange -> {
                    // Redo: Apply color change again
                    action.notesBefore.zip(action.notesAfter).forEach { (before, after) ->
                        action.onNoteUpdate(before, after)
                    }
                    val currentEntry = action.currentEntryGetter()
                    if (currentEntry != null) {
                        action.currentEntrySetter(
                            currentEntry.copy(
                                notes = currentEntry.notes.map { note ->
                                    action.notesBefore.zip(action.notesAfter).find { it.first == note }?.second ?: note
                                }
                            )
                        )
                    }
                    dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.clear()
                    action.notesAfter.forEach { afterNote ->
                        dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.select(
                            dev.anthonyhfm.amethyst.core.controls.selection.Selectable.PianoRollNote(
                                action.trackIndex,
                                action.entryStartMs,
                                afterNote
                            ),
                            single = false
                        )
                    }
                    undoStack.add(action)
                }

                is UndoableAction.TrackAddition -> {
                    TimelineRepository.insertTrack(action.trackIndex, action.track)
                    undoStack.add(action)
                }

                is UndoableAction.TrackRemoval -> {
                    TimelineRepository.removeTrack(action.trackIndex)
                    undoStack.add(action)
                }

                is UndoableAction.TrackDuplication -> {
                    TimelineRepository.insertTrack(action.duplicatedIndex, action.duplicatedTrack)
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