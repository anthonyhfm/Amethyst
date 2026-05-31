package dev.anthonyhfm.amethyst.devices.effects.group.editor

import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncCoordinator
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.flow.MutableStateFlow

private const val DEFAULT_GROUP_NAME = "Chain #"

internal class GroupEditorActionLayer<State : DeviceState>(
    private val device: GenericChainDevice<State>,
    private val stateFlow: MutableStateFlow<State>,
    private val groupsOf: (State) -> List<Group>,
    private val openedGroupIndexOf: (State) -> Int,
    private val buildState: (beforeState: State, groups: List<Group>, openedGroupIndex: Int) -> State,
) {
    fun openGroup(index: Int) {
        val currentState = stateFlow.value
        val groups = groupsOf(currentState)
        if (index !in groups.indices || openedGroupIndexOf(currentState) == index) return

        stateFlow.value = buildState(currentState, groups, index)
    }

    fun createGroup(atIndex: Int? = null) {
        insertGroups(
            newGroups = listOf(createEmptyGroup()),
            insertIndex = atIndex ?: groupsOf(stateFlow.value).size,
            desiredSelectedGroupIds = null,
            undoable = false,
        )
    }

    fun createGroupWithUndo(atIndex: Int? = null) {
        val newGroup = createEmptyGroup()
        insertGroups(
            newGroups = listOf(newGroup),
            insertIndex = atIndex ?: groupsOf(stateFlow.value).size,
            desiredSelectedGroupIds = listOf(newGroup.id),
            undoable = true,
        )
    }

    fun insertGroupWithUndo(
        group: Group,
        atIndex: Int? = null,
        selectInsertedGroup: Boolean = true,
    ) {
        insertGroups(
            newGroups = listOf(group),
            insertIndex = atIndex ?: groupsOf(stateFlow.value).size,
            desiredSelectedGroupIds = if (selectInsertedGroup) listOf(group.id) else null,
            undoable = true,
        )
    }

    fun pasteChainClipboardAsGroup(atIndex: Int? = null) {
        val clipData = ClipboardManager.clipboardData.value as? ClipboardData.ChainDevice ?: return
        val newGroup = Group(
            name = DEFAULT_GROUP_NAME,
            chain = Chain().apply {
                clipData.states.forEach { deviceState ->
                    add(StateChain.unpackDevice(deviceState))
                }
                signalExit = { signal ->
                    this@GroupEditorActionLayer.device.signalExit?.invoke(signal)
                }
            }
        )

        insertGroups(
            newGroups = listOf(newGroup),
            insertIndex = atIndex ?: groupsOf(stateFlow.value).size,
            desiredSelectedGroupIds = listOf(newGroup.id),
            undoable = true,
        )
    }

    fun removeGroup(index: Int) {
        removeGroups(listOf(index))
    }

    fun removeGroupById(id: String) {
        val index = groupsOf(stateFlow.value).indexOfFirst { it.id == id }
        if (index != -1) removeGroup(index)
    }

    fun removeGroups(indices: List<Int>) {
        val beforeState = stateFlow.value
        val beforeGroups = groupsOf(beforeState)
        val normalizedIndices = indices
            .mapNotNull { index -> index.takeIf { it in beforeGroups.indices } }
            .distinct()
            .sorted()

        if (normalizedIndices.isEmpty() || beforeGroups.size <= normalizedIndices.size) return

        val removedIds = normalizedIndices.map(beforeGroups::get).map(Group::id).toSet()
        val removedIndexSet = normalizedIndices.toSet()
        val afterGroups = beforeGroups.filterIndexed { index, _ -> index !in removedIndexSet }
        val previousOpenedId = beforeGroups.getOrNull(openedGroupIndexOf(beforeState))?.id
        val afterOpenedIndex = previousOpenedId
            ?.let { openedId -> afterGroups.indexOfFirst { group -> group.id == openedId } }
            ?.takeIf { it >= 0 }
            ?: normalizedIndices.first().coerceIn(0, afterGroups.lastIndex)

        val desiredSelectedGroupIds = if (hasGroupSelectionForDevice(device)) {
            selectedGroupIdsForDevice(device, beforeGroups)
                .filterNot(removedIds::contains)
                .ifEmpty {
                    afterGroups.getOrNull(afterOpenedIndex)?.let { listOf(it.id) } ?: emptyList()
                }
        } else {
            null
        }

        commitGroupState(
            beforeState = beforeState,
            afterGroups = afterGroups,
            afterOpenedGroupIndex = afterOpenedIndex,
            desiredSelectedGroupIds = desiredSelectedGroupIds,
            undoable = true,
        )
    }

    fun copyGroup(group: Group) {
        val groups = groupsOf(stateFlow.value)
        val targetIndex = groups.indexOfFirst { current -> current.id == group.id }
        if (targetIndex == -1) return

        val selectedIndices = SelectionManager.selections.value
            .filterIsInstance<Selectable.GroupChainItem>()
            .filter { selection -> selection.parent == device }
            .map(Selectable.GroupChainItem::groupIndex)
            .filter { index -> index in groups.indices }
            .distinct()
            .sorted()

        val indicesToCopy = if (targetIndex in selectedIndices) {
            selectedIndices
        } else {
            listOf(targetIndex)
        }

        ClipboardManager.copy(
            indicesToCopy.map { index ->
                Selectable.GroupChainItem(
                    parent = device,
                    groupIndex = index,
                )
            }
        )
    }

    fun pasteGroup(index: Int) {
        val clipData = ClipboardManager.clipboardData.value as? ClipboardData.GroupChainItem ?: return
        pasteGroups(
            groups = clipData.groups,
            targetIndex = (index + 1).coerceIn(0, groupsOf(stateFlow.value).size),
        )
    }

    fun renameGroup(index: Int, newName: String) {
        val beforeState = stateFlow.value
        val beforeGroups = groupsOf(beforeState)
        if (index !in beforeGroups.indices || beforeGroups[index].name == newName) return

        val afterGroups = beforeGroups.toMutableList().apply {
            this[index] = this[index].copy(name = newName)
        }

        commitGroupState(
            beforeState = beforeState,
            afterGroups = afterGroups,
            afterOpenedGroupIndex = openedGroupIndexOf(beforeState),
            desiredSelectedGroupIds = if (hasGroupSelectionForDevice(device)) {
                selectedGroupIdsForDevice(device, beforeGroups)
            } else {
                null
            },
            undoable = true,
        )
    }

    fun duplicateGroup(index: Int, toIndex: Int? = null) {
        val beforeState = stateFlow.value
        val beforeGroups = groupsOf(beforeState)
        val sourceGroup = beforeGroups.getOrNull(index) ?: return
        val duplicatedGroup = duplicateGroup(sourceGroup)

        insertGroups(
            newGroups = listOf(duplicatedGroup),
            insertIndex = toIndex?.coerceIn(0, beforeGroups.size) ?: (index + 1).coerceIn(0, beforeGroups.size),
            desiredSelectedGroupIds = listOf(duplicatedGroup.id),
            undoable = true,
        )
    }

    fun duplicateGroups(selectedIndices: List<Int>) {
        val beforeState = stateFlow.value
        val beforeGroups = groupsOf(beforeState)
        val normalizedIndices = selectedIndices
            .mapNotNull { index -> index.takeIf { it in beforeGroups.indices } }
            .distinct()
            .sorted()

        if (normalizedIndices.isEmpty()) return

        val afterGroups = beforeGroups.toMutableList()
        val duplicatedGroupIds = mutableListOf<String>()
        var insertionOffset = 0

        normalizedIndices.forEach { originalIndex ->
            val duplicatedGroup = duplicateGroup(beforeGroups[originalIndex])
            afterGroups.add(originalIndex + 1 + insertionOffset, duplicatedGroup)
            duplicatedGroupIds += duplicatedGroup.id
            insertionOffset += 1
        }

        val afterOpenedIndex = duplicatedGroupIds
            .firstNotNullOfOrNull { duplicatedId ->
                afterGroups.indexOfFirst { group -> group.id == duplicatedId }.takeIf { it >= 0 }
            }
            ?: openedGroupIndexOf(beforeState)

        commitGroupState(
            beforeState = beforeState,
            afterGroups = afterGroups,
            afterOpenedGroupIndex = afterOpenedIndex,
            desiredSelectedGroupIds = duplicatedGroupIds,
            undoable = true,
        )
    }

    fun pasteGroups(groups: List<Group>, targetIndex: Int?) {
        if (groups.isEmpty()) return

        val pastedGroups = groups.map(::duplicateGroup)
        insertGroups(
            newGroups = pastedGroups,
            insertIndex = (targetIndex ?: groupsOf(stateFlow.value).size).coerceIn(0, groupsOf(stateFlow.value).size),
            desiredSelectedGroupIds = pastedGroups.map(Group::id),
            undoable = true,
        )
    }

    fun moveGroup(fromIndex: Int, toIndex: Int) {
        val beforeState = stateFlow.value
        val beforeGroups = groupsOf(beforeState)
        val afterGroups = moveGroupList(
            groups = beforeGroups,
            fromIndex = fromIndex,
            toIndex = toIndex,
        )

        if (afterGroups == beforeGroups) return

        val previousOpenedId = beforeGroups.getOrNull(openedGroupIndexOf(beforeState))?.id
        val afterOpenedIndex = previousOpenedId
            ?.let { openedId -> afterGroups.indexOfFirst { group -> group.id == openedId } }
            ?.takeIf { it >= 0 }
            ?: normalizeOpenedGroupIndex(afterGroups, openedGroupIndexOf(beforeState))

        commitGroupState(
            beforeState = beforeState,
            afterGroups = afterGroups,
            afterOpenedGroupIndex = afterOpenedIndex,
            desiredSelectedGroupIds = if (hasGroupSelectionForDevice(device)) {
                selectedGroupIdsForDevice(device, beforeGroups)
            } else {
                null
            },
            undoable = true,
        )
    }

    private fun insertGroups(
        newGroups: List<Group>,
        insertIndex: Int,
        desiredSelectedGroupIds: List<String>?,
        undoable: Boolean,
    ) {
        if (newGroups.isEmpty()) return

        newGroups.forEach { group ->
            group.chain.signalExit = { signal ->
                device.signalExit?.invoke(signal)
            }
        }

        val beforeState = stateFlow.value
        val beforeGroups = groupsOf(beforeState)
        val safeInsertIndex = insertIndex.coerceIn(0, beforeGroups.size)
        val afterGroups = beforeGroups.toMutableList().apply {
            addAll(safeInsertIndex, newGroups)
        }

        commitGroupState(
            beforeState = beforeState,
            afterGroups = afterGroups,
            afterOpenedGroupIndex = safeInsertIndex,
            desiredSelectedGroupIds = desiredSelectedGroupIds,
            undoable = undoable,
        )
    }

    private fun commitGroupState(
        beforeState: State,
        afterGroups: List<Group>,
        afterOpenedGroupIndex: Int,
        desiredSelectedGroupIds: List<String>?,
        undoable: Boolean,
    ) {
        val beforeGroups = groupsOf(beforeState)
        val beforeSelectedGroupIds = selectedGroupIdsForDevice(device, beforeGroups)
        val afterSelectedGroupIds = desiredSelectedGroupIds ?: beforeSelectedGroupIds.filter { selectedGroupId ->
            afterGroups.any { group -> group.id == selectedGroupId }
        }
        val afterState = buildState(
            beforeState,
            afterGroups,
            normalizeOpenedGroupIndex(afterGroups, afterOpenedGroupIndex),
        )

        if (afterState == beforeState) return

        stateFlow.value = afterState
        AutomappingManager.clearTargetIfMissing(device)
        desiredSelectedGroupIds?.let { groupIds ->
            restoreGroupSelectionForDevice(
                device = device,
                groups = afterGroups,
                groupIds = groupIds,
            )
        }

        if (undoable) {
            UndoManager.addAction(
                UndoableAction.GroupEditorStateChange(
                    device = device,
                    beforeState = beforeState,
                    afterState = afterState,
                    beforeSelectedGroupIds = beforeSelectedGroupIds,
                    afterSelectedGroupIds = afterSelectedGroupIds,
                )
            )
            ChainSyncCoordinator.onGroupStateChanged(device, beforeState, afterState)
            ChainSyncCoordinator.onDeviceStateChanged(device, afterState)
        }
    }

    private fun createEmptyGroup(): Group {
        return Group(
            name = DEFAULT_GROUP_NAME,
            chain = Chain().apply {
                signalExit = { signal ->
                    device.signalExit?.invoke(signal)
                }
            }
        )
    }

    private fun duplicateGroup(group: Group): Group {
        return Group(
            name = group.name,
            chain = StateChain.pack(group.chain).unpack().apply {
                signalExit = { signal ->
                    device.signalExit?.invoke(signal)
                }
            }
        )
    }
}

internal fun currentGroupsForDevice(device: GenericChainDevice<*>): List<Group> {
    return when (device) {
        is GroupChainDevice -> device.state.value.groups
        is MultiGroupChainDevice -> device.state.value.groups
        else -> emptyList()
    }
}

internal fun hasGroupSelectionForDevice(device: GenericChainDevice<*>): Boolean {
    return SelectionManager.selections.value.any { selection ->
        selection is Selectable.GroupChainItem && selection.parent == device
    }
}

internal fun selectedGroupIdsForDevice(
    device: GenericChainDevice<*>,
    groups: List<Group>,
): List<String> {
    return SelectionManager.selections.value
        .filterIsInstance<Selectable.GroupChainItem>()
        .filter { selection -> selection.parent == device }
        .mapNotNull { selection -> groups.getOrNull(selection.groupIndex)?.id }
        .distinct()
}

internal fun restoreGroupSelectionForDevice(
    device: GenericChainDevice<*>,
    groups: List<Group>,
    groupIds: List<String>,
    clearWhenEmpty: Boolean = true,
) {
    val updatedSelections = groupIds.mapNotNull { groupId ->
        groups.indexOfFirst { group -> group.id == groupId }
            .takeIf { index -> index >= 0 }
            ?.let { index ->
                Selectable.GroupChainItem(
                    parent = device,
                    groupIndex = index,
                )
            }
    }

    if (updatedSelections.isNotEmpty()) {
        SelectionManager.replaceSelections(updatedSelections)
    } else if (clearWhenEmpty && hasGroupSelectionForDevice(device)) {
        SelectionManager.clear()
    }
}

private fun normalizeOpenedGroupIndex(groups: List<Group>, openedGroupIndex: Int): Int {
    return if (groups.isEmpty()) {
        0
    } else {
        openedGroupIndex.coerceIn(0, groups.lastIndex)
    }
}
