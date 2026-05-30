package dev.anthonyhfm.amethyst.devices.effects.group

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.group.editor.AutomappingToggleButton
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorActions
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorActionLayer
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorContentHost
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorList
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorScaffold
import dev.anthonyhfm.amethyst.devices.effects.group.editor.rememberGroupEditorUiState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.NestedChainDevice


class GroupChainDevice : GenericChainDevice<GroupChainDeviceState>(), NestedChainDevice {
    override val state = MutableStateFlow(GroupChainDeviceState())

    private val actionLayer = GroupEditorActionLayer(
        device = this,
        stateFlow = state,
        groupsOf = GroupChainDeviceState::groups,
        openedGroupIndexOf = GroupChainDeviceState::openedGroupIndex,
        buildState = { beforeState, groups, openedGroupIndex ->
            beforeState.copy(
                groups = groups,
                openedGroupIndex = openedGroupIndex,
            )
        },
    )

    init {
        createGroup()
    }

    @Composable
    fun Content(
        dragAndDropState: DragAndDropState<GenericChainDevice<*>> = rememberDragAndDropState()
    ) {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }
        val editorUiState = rememberGroupEditorUiState(parentSelectionUUID = selectionUUID)
        val editorActions = remember(this) {
            GroupEditorActions(
                onAddGroup = { index -> createGroupWithUndo(index) },
                onMoveGroup = ::moveGroup,
                onOpenGroup = ::openGroup,
                onRenameGroup = ::renameGroup,
                onCopyGroup = ::copyGroup,
                onPasteGroup = ::pasteGroup,
                onDuplicateGroup = { index -> duplicateGroup(index) },
                onDuplicateGroups = ::duplicateGroups,
                onDeleteGroup = ::removeGroup,
                onDeleteGroups = ::removeGroups,
                onPasteDevicesAsGroup = ::pasteChainClipboardAsGroup,
            )
        }

        GroupEditorScaffold(
            title = "Group",
            isSelected = isSelected,
            leadingStripContent = {
                val automappingState by AutomappingManager.state.collectAsState()
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                ) {
                    AutomappingToggleButton(
                        active = automappingState.activeTarget?.parentDeviceSelectionUUID == selectionUUID,
                        onClick = {
                            AutomappingManager.toggleTarget(
                                parentDevice = this@GroupChainDevice,
                            )
                        },
                    )
                }
            },
            groupList = {
                GroupEditorList(
                    parentDevice = this@GroupChainDevice,
                    groups = deviceState.groups,
                    openedGroupIndex = deviceState.openedGroupIndex,
                    uiState = editorUiState,
                    actions = editorActions,
                )
            },
        ) {
            val openedGroup = deviceState.groups.getOrNull(deviceState.openedGroupIndex)
            if (openedGroup != null) {
                key(openedGroup.id) {
                    GroupEditorContentHost(
                        parentSelectionUUID = selectionUUID,
                        group = openedGroup,
                        dragAndDropState = dragAndDropState,
                        uiState = editorUiState,
                    )
                }
            }
        }
    }

    @Composable
    override fun Content() {
        Content(rememberDragAndDropState())
    }

    override fun onRemovedFromChain() {
        AutomappingManager.clearTargetForDevice(selectionUUID)
    }

    override fun onStateRestored() {
        AutomappingManager.clearTargetIfMissing(this)
    }

    private fun moveGroup(fromIndex: Int, toIndex: Int) {
        actionLayer.moveGroup(fromIndex, toIndex)
    }

    private fun openGroup(index: Int) {
        actionLayer.openGroup(index)
    }

    private fun pasteChainClipboardAsGroup(atIndex: Int? = null) {
        actionLayer.pasteChainClipboardAsGroup(atIndex)
    }

    fun createGroup(atIndex: Int? = null) {
        actionLayer.createGroup(atIndex)
    }

    override fun signalEnter(n: List<Signal>) {
        state.value.groups.forEach {
            it.chain.signalEnter(n)
        }
    }

    fun removeGroup(index: Int) {
        actionLayer.removeGroup(index)
    }

    fun copyGroup(group: Group) {
        actionLayer.copyGroup(group)
    }

    fun pasteGroup(index: Int) {
        actionLayer.pasteGroup(index)
    }

    fun renameGroup(index: Int, newName: String) {
        actionLayer.renameGroup(index, newName)
    }

    fun duplicateGroup(index: Int, toIndex: Int? = null) {
        actionLayer.duplicateGroup(index, toIndex)
    }

    fun loadFromState(state: GroupChainDeviceState) {
        val unpackedGroups = state.groups.map { group ->
            val unpackedChain = group.stateChain.unpack()
            unpackedChain.signalExit = {
                signalExit?.invoke(it)
            }

            Group(
                name = group.name,
                chain = unpackedChain,
                stateChain = group.stateChain,
                id = group.id
            )
        }

        this.state.update {
            val openedGroupIndex = if (unpackedGroups.isEmpty()) {
                0
            } else {
                state.openedGroupIndex.coerceIn(0, unpackedGroups.lastIndex)
            }

            state.copy(
                groups = unpackedGroups,
                openedGroupIndex = openedGroupIndex,
            )
        }

        if (this.state.value.groups.isEmpty()) {
            createGroup()
        }
    }

    // Internal methods for undo/redo operations without triggering UndoManager
    fun addGroupInternal(index: Int, group: Group) {
        state.update {
            val newGroups = it.groups.toMutableList().apply {
                add(index, group.copy(chain = StateChain.pack(group.chain).unpack().apply {
                    signalExit = { signal -> this@GroupChainDevice.signalExit?.invoke(signal) }
                }))
            }

            it.copy(
                groups = newGroups,
                openedGroupIndex = index
            )
        }
    }

    fun removeGroupInternal(index: Int) {
        if (state.value.groups.size <= 1) return

        state.update {
            val newGroups = it.groups.toMutableList().apply {
                removeAt(index)
            }

            val newOpenedGroupIndex = when {
                it.openedGroupIndex >= newGroups.size -> newGroups.size - 1
                it.openedGroupIndex > index -> it.openedGroupIndex - 1
                else -> it.openedGroupIndex
            }

            it.copy(
                groups = newGroups,
                openedGroupIndex = newOpenedGroupIndex
            )
        }
        AutomappingManager.clearTargetIfMissing(this)
    }

    fun duplicateGroups(selectedIndices: List<Int>) {
        actionLayer.duplicateGroups(selectedIndices)
    }

    fun pasteGroups(groups: List<Group>, targetIndex: Int?) {
        actionLayer.pasteGroups(groups, targetIndex)
    }

    fun removeGroups(indices: List<Int>) {
        actionLayer.removeGroups(indices)
    }

    fun createGroupWithUndo(index: Int? = null) {
        actionLayer.createGroupWithUndo(index)
    }

    fun addGroup(group: Group) {
        actionLayer.insertGroupWithUndo(group, selectInsertedGroup = false)
    }

    fun removeGroupById(id: String) {
        actionLayer.removeGroupById(id)
    }

    fun packState(): GroupChainDeviceState {
        return state.value.copy(
            groups = state.value.groups.map { group ->
                Group(
                    name = group.name,
                    stateChain = StateChain.pack(group.chain),
                    id = group.id
                )
            }
        )
    }

    private fun performRangeSelection(endIndex: Int) {
        val startIndex = state.value.openedGroupIndex
        val range = if (startIndex < endIndex) {
            startIndex..endIndex
        } else {
            endIndex..startIndex
        }

        SelectionManager.clear()

        range.forEach { index ->
            val groupChainItem = Selectable.GroupChainItem(
                parent = this,
                groupIndex = index
            )
            SelectionManager.select(groupChainItem, single = false)
        }
    }

    override fun nestedChains() = state.value.groups.map { it.chain }

    companion object : ChainDeviceFactory<GroupChainDeviceState> {
        override val stateClass = GroupChainDeviceState::class
        override val serializer = GroupChainDeviceState.serializer()
        override fun create() = GroupChainDevice()
        override fun pack(device: GenericChainDevice<GroupChainDeviceState>): GroupChainDeviceState =
            (device as GroupChainDevice).packState()
        override fun unpack(state: GroupChainDeviceState): GroupChainDevice =
            GroupChainDevice().apply { loadFromState(state) }
    }
}

@Serializable
data class GroupChainDeviceState(
    val openedGroupIndex: Int = 0,
    val groups: List<Group> = emptyList()
) : DeviceState()
