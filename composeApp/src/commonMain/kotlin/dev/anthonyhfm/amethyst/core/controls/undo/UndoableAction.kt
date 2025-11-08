package dev.anthonyhfm.amethyst.core.controls.undo

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

sealed interface UndoableAction {
    data class ChainDeviceCreation(
        val parent: Chain,
        val device: dev.anthonyhfm.amethyst.devices.GenericChainDevice<*>,
        val creationIndex: Int,
    ) : UndoableAction

    data class ChainDeviceRemoval(
        val parent: Chain,
        val device: dev.anthonyhfm.amethyst.devices.GenericChainDevice<*>,
        val originalIndex: Int,
    ) : UndoableAction

    data class MovedChainDevice(
        val chainBefore: Chain,
        val chainAfter: Chain,
        val device: dev.anthonyhfm.amethyst.devices.GenericChainDevice<*>,
        val fromIndex: Int,
        val toIndex: Int,
    ) : UndoableAction

    data class KeyframeCreation(
        val device: KeyframesChainDevice,
        val frameIndex: Int,
        val frame: Frame
    ) : UndoableAction

    data class KeyframeDeletion(
        val device: KeyframesChainDevice,
        val frameIndex: Int,
        val frame: Frame
    ) : UndoableAction

    data class KeyframeDuplication(
        val device: KeyframesChainDevice,
        val originalIndex: Int,
        val duplicatedIndex: Int,
        val duplicatedFrame: Frame
    ) : UndoableAction

    data class MultiKeyframeDuplication(
        val device: KeyframesChainDevice,
        val duplications: List<KeyframeDuplicationInfo>
    ) : UndoableAction

    data class KeyframeDuplicationInfo(
        val originalIndex: Int,
        val duplicatedIndex: Int,
        val duplicatedFrame: Frame
    )

    data class MultiKeyframeDeletion(
        val device: KeyframesChainDevice,
        val deletions: List<KeyframeDeletionInfo>
    ) : UndoableAction

    data class KeyframeDeletionInfo(
        val frameIndex: Int,
        val frame: Frame
    )

    data class KeyframePaste(
        val device: KeyframesChainDevice,
        val pastedFrames: List<KeyframePasteInfo>
    ) : UndoableAction

    data class KeyframePasteInfo(
        val frameIndex: Int,
        val frame: Frame
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
        val device: dev.anthonyhfm.amethyst.devices.GenericChainDevice<*>, // Typsicherer als Any
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
        val beforeEntries: List<dev.anthonyhfm.amethyst.timeline.data.AudioEntry>,
        val afterEntries: List<dev.anthonyhfm.amethyst.timeline.data.AudioEntry>
    ) : UndoableAction

    data class TimelineClipTrim(
        val trackIndex: Int,
        val original: dev.anthonyhfm.amethyst.timeline.data.AudioEntry,
        val trimmed: dev.anthonyhfm.amethyst.timeline.data.AudioEntry
    ) : UndoableAction

    data class TimelineClipSplit(
        val trackIndex: Int,
        val original: dev.anthonyhfm.amethyst.timeline.data.AudioEntry?,
        val left: dev.anthonyhfm.amethyst.timeline.data.AudioEntry?,
        val right: dev.anthonyhfm.amethyst.timeline.data.AudioEntry?
    ) : UndoableAction

    data class TimelineClipDeletion(
        val trackIndex: Int,
        val deleted: dev.anthonyhfm.amethyst.timeline.data.AudioEntry
    ) : UndoableAction

    data class ChangeDeviceState<State: DeviceState>(
        val device: GenericChainDevice<State>,
        val beforeState: State,
        val afterState: State
    ) : UndoableAction

    data class WorkspaceModeChange(
        val beforeMode: WorkspaceContract.WorkspaceMode,
        val afterMode: WorkspaceContract.WorkspaceMode
    ) : UndoableAction
}