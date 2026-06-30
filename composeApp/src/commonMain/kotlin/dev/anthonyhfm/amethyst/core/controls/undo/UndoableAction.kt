package dev.anthonyhfm.amethyst.core.controls.undo

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.data.Macro

sealed interface UndoableAction {
    data class ChainDeviceCreation(
        val parent: Chain,
        val device: GenericChainDevice<*>,
        val creationIndex: Int,
    ) : UndoableAction

    data class ChainDeviceRemoval(
        val parent: Chain,
        val device: GenericChainDevice<*>,
        val originalIndex: Int,
    ) : UndoableAction

    data class MultiChainDeviceRemoval(
        val removals: List<ChainDeviceRemoval>
    ) : UndoableAction

    data class ChainDeviceGrouping(
        val parent: Chain,
        val groupDevice: GroupChainDevice,
        val insertionIndex: Int,
        val removedDevices: List<RemovedDeviceInfo>
    ) : UndoableAction

    data class ChainDeviceUngrouping(
        val parent: Chain,
        val groupDevice: GroupChainDevice,
        val groupIndex: Int,
        val extractedDevices: List<RemovedDeviceInfo>
    ) : UndoableAction

    data class RemovedDeviceInfo(
        val device: GenericChainDevice<*>,
        val originalIndex: Int
    )

    data class MovedChainDevice(
        val chainBefore: Chain,
        val chainAfter: Chain,
        val device: GenericChainDevice<*>,
        val fromIndex: Int,
        val toIndex: Int,
    ) : UndoableAction

    data class KeyframeCreation(
        val device: KeyframesChainDevice,
        val frameIndex: Int,
        val frame: KeyframesChainDeviceContract.Frame
    ) : UndoableAction

    data class KeyframeDeletion(
        val device: KeyframesChainDevice,
        val frameIndex: Int,
        val frame: KeyframesChainDeviceContract.Frame
    ) : UndoableAction

    data class KeyframeDuplication(
        val device: KeyframesChainDevice,
        val originalIndex: Int,
        val duplicatedIndex: Int,
        val duplicatedFrame: KeyframesChainDeviceContract.Frame
    ) : UndoableAction

    data class MultiKeyframeDuplication(
        val device: KeyframesChainDevice,
        val duplications: List<KeyframeDuplicationInfo>
    ) : UndoableAction

    data class KeyframeDuplicationInfo(
        val originalIndex: Int,
        val duplicatedIndex: Int,
        val duplicatedFrame: KeyframesChainDeviceContract.Frame
    )

    data class MultiKeyframeDeletion(
        val device: KeyframesChainDevice,
        val deletions: List<KeyframeDeletionInfo>
    ) : UndoableAction

    data class KeyframeDeletionInfo(
        val frameIndex: Int,
        val frame: KeyframesChainDeviceContract.Frame
    )

    data class KeyframePaste(
        val device: KeyframesChainDevice,
        val pastedFrames: List<KeyframePasteInfo>
    ) : UndoableAction

    data class KeyframePasteInfo(
        val frameIndex: Int,
        val frame: KeyframesChainDeviceContract.Frame
    )

    data class GroupCreation(
        val device: GroupChainDevice,
        val groupIndex: Int,
        val group: Group
    ) : UndoableAction

    data class GroupDeletion(
        val device: GroupChainDevice,
        val groupIndex: Int,
        val group: Group
    ) : UndoableAction

    data class GroupDuplication(
        val device: GroupChainDevice,
        val originalIndex: Int,
        val duplicatedIndex: Int,
        val duplicatedGroup: Group
    ) : UndoableAction

    data class MultiGroupDuplication(
        val device: GroupChainDevice,
        val duplications: List<GroupDuplicationInfo>
    ) : UndoableAction

    data class GroupDuplicationInfo(
        val originalIndex: Int,
        val duplicatedIndex: Int,
        val duplicatedGroup: Group
    )

    data class MultiGroupDeletion(
        val device: GenericChainDevice<*>, // Typsicherer als Any
        val deletions: List<GroupDeletionInfo>
    ) : UndoableAction

    data class GroupDeletionInfo(
        val groupIndex: Int,
        val group: Group
    )

    data class GroupPaste(
        val device: GroupChainDevice,
        val pastedGroups: List<GroupPasteInfo>
    ) : UndoableAction

    data class GroupPasteInfo(
        val groupIndex: Int,
        val group: Group
    )

    data class GroupEditorStateChange<State : DeviceState>(
        val device: GenericChainDevice<State>,
        val beforeState: State,
        val afterState: State,
        val beforeSelectedGroupIds: List<String>,
        val afterSelectedGroupIds: List<String>,
    ) : UndoableAction

    data class MultiGroupCreation(
        val device: MultiGroupChainDevice,
        val groupIndex: Int,
        val group: Group
    ) : UndoableAction

    data class MultiGroupDuplicationAction(
        val device: MultiGroupChainDevice,
        val originalIndex: Int,
        val duplicatedIndex: Int,
        val duplicatedGroup: Group
    ) : UndoableAction

    data class MultiGroupMultiDuplication(
        val device: MultiGroupChainDevice,
        val duplications: List<GroupDuplicationInfo>
    ) : UndoableAction

    data class MultiGroupPaste(
        val device: MultiGroupChainDevice,
        val pastedGroups: List<GroupPasteInfo>
    ) : UndoableAction

    data class TimelineChange(
        val trackIndex: Int,
        val beforeEntries: List<AudioEntry>,
        val afterEntries: List<AudioEntry>
    ) : UndoableAction

    data class MidiTimelineChange(
        val trackIndex: Int,
        val beforeEntries: List<MidiEntry>,
        val afterEntries: List<MidiEntry>
    ) : UndoableAction

    data class TimelineClipTrim(
        val trackIndex: Int,
        val original: AudioEntry,
        val trimmed: AudioEntry
    ) : UndoableAction

    data class TimelineClipSplit(
        val trackIndex: Int,
        val original: AudioEntry?,
        val left: AudioEntry?,
        val right: AudioEntry?
    ) : UndoableAction

    data class MidiTimelineClipSplit(
        val trackIndex: Int,
        val original: MidiEntry?,
        val left: MidiEntry?,
        val right: MidiEntry?
    ) : UndoableAction

    data class TimelineClipDeletion(
        val trackIndex: Int,
        val deleted: AudioEntry
    ) : UndoableAction

    data class ChangeDeviceState<State: DeviceState>(
        val device: GenericChainDevice<State>,
        val beforeState: State,
        val afterState: State
    ) : UndoableAction

    data class WorkspaceModeChange(
        val beforeMode: dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode,
        val afterMode: dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
    ) : UndoableAction

    data class WorkspaceBpmChange(
        val beforeBpm: Double,
        val afterBpm: Double
    ) : UndoableAction

    data class WorkspaceMacrosChange(
        val beforeMacros: List<Macro>,
        val afterMacros: List<Macro>
    ) : UndoableAction

    data class PianoRollNoteMove(
        val trackIndex: Int,
        val entryStartMs: Long,
        val notesBefore: List<MidiNote>,
        val notesAfter: List<MidiNote>,
        val onNoteUpdate: (MidiNote, MidiNote) -> Unit,
        val currentEntryGetter: () -> MidiEntry?,
        val currentEntrySetter: (MidiEntry) -> Unit
    ) : UndoableAction

    data class PianoRollNoteCreation(
        val trackIndex: Int,
        val entryStartMs: Long,
        val note: MidiNote,
        val onNoteAdd: (MidiNote) -> Unit,
        val onNoteDelete: (MidiNote) -> Unit,
        val currentEntryGetter: () -> MidiEntry?,
        val currentEntrySetter: (MidiEntry) -> Unit
    ) : UndoableAction

    data class PianoRollNoteDeletion(
        val trackIndex: Int,
        val entryStartMs: Long,
        val notes: List<MidiNote>,
        val onNoteAdd: (MidiNote) -> Unit,
        val onNoteDelete: (MidiNote) -> Unit,
        val currentEntryGetter: () -> MidiEntry?,
        val currentEntrySetter: (MidiEntry) -> Unit
    ) : UndoableAction

    data class PianoRollNoteDuplication(
        val trackIndex: Int,
        val entryStartMs: Long,
        val duplicates: List<MidiNote>,
        val onNoteAdd: (MidiNote) -> Unit,
        val onNoteDelete: (MidiNote) -> Unit,
        val currentEntryGetter: () -> MidiEntry?,
        val currentEntrySetter: (MidiEntry) -> Unit
    ) : UndoableAction

    data class PianoRollNoteResize(
        val trackIndex: Int,
        val entryStartMs: Long,
        val notesBefore: List<MidiNote>,
        val notesAfter: List<MidiNote>,
        val onNoteUpdate: (MidiNote, MidiNote) -> Unit,
        val currentEntryGetter: () -> MidiEntry?,
        val currentEntrySetter: (MidiEntry) -> Unit
    ) : UndoableAction

    data class PianoRollNoteColorChange(
        val trackIndex: Int,
        val entryStartMs: Long,
        val notesBefore: List<MidiNote>,
        val notesAfter: List<MidiNote>,
        val onNoteUpdate: (MidiNote, MidiNote) -> Unit,
        val currentEntryGetter: () -> MidiEntry?,
        val currentEntrySetter: (MidiEntry) -> Unit
    ) : UndoableAction

    data class PianoRollNoteGradientChange(
        val trackIndex: Int,
        val entryStartMs: Long,
        val notesBefore: List<MidiNote>,
        val notesAfter: List<MidiNote>,
        val onNoteUpdate: (MidiNote, MidiNote) -> Unit,
        val currentEntryGetter: () -> MidiEntry?,
        val currentEntrySetter: (MidiEntry) -> Unit
    ) : UndoableAction

    data class PianoRollNoteTransform(
        val trackIndex: Int,
        val entryStartMs: Long,
        val notesBefore: List<MidiNote>,
        val notesAfter: List<MidiNote>,
        val onNoteUpdate: (MidiNote, MidiNote) -> Unit,
        val currentEntryGetter: () -> MidiEntry?,
        val currentEntrySetter: (MidiEntry) -> Unit
    ) : UndoableAction

    data class PianoRollNoteMultiCreation(
        val trackIndex: Int,
        val entryStartMs: Long,
        val notes: List<MidiNote>,
        val onNoteAdd: (MidiNote) -> Unit,
        val onNoteDelete: (MidiNote) -> Unit,
        val currentEntryGetter: () -> MidiEntry?,
        val currentEntrySetter: (MidiEntry) -> Unit
    ) : UndoableAction

    data class TrackAddition(
        val trackIndex: Int,
        val track: TimelineTrack<*>
    ) : UndoableAction

    data class TrackRemoval(
        val trackIndex: Int,
        val track: TimelineTrack<*>
    ) : UndoableAction

    data class TrackDuplication(
        val originalIndex: Int,
        val duplicatedIndex: Int,
        val duplicatedTrack: TimelineTrack<*>
    ) : UndoableAction

    data class TrackRename(
        val trackIndex: Int,
        val oldName: String,
        val newName: String
    ) : UndoableAction

    data class TrackStateChange(
        val trackIndex: Int,
        val beforeTrack: TimelineTrack<*>,
        val afterTrack: TimelineTrack<*>,
        val mergeable: Boolean = true
    ) : UndoableAction
}
