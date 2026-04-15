package dev.anthonyhfm.amethyst.devices.effects.group

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.group.editor.AutomappingToggleButton
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorActions
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorActionLayer
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorContentHost
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorList
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorScaffold
import dev.anthonyhfm.amethyst.devices.effects.group.editor.moveGroupList
import dev.anthonyhfm.amethyst.devices.effects.group.editor.rememberGroupEditorUiState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.ui.modifier.onFocusSelectAll
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.ui.AnimatedInsertedDevice
import dev.anthonyhfm.amethyst.workspace.chain.ui.ChainDeviceContextMenu
import dev.anthonyhfm.amethyst.workspace.chain.ui.DeviceInsertionAnimator
import dev.anthonyhfm.amethyst.workspace.chain.ui.ExpandingChainDevicePicker
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.chain.ui.SignalIndicatorManager
import dev.anthonyhfm.amethyst.workspace.chain.ui.TitleBarModifierProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState


class GroupChainDevice : GenericChainDevice<GroupChainDeviceState>() {
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
                val openedGroup = deviceState.groups.getOrNull(deviceState.openedGroupIndex)
                if (openedGroup != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                    ) {
                        AutomappingToggleButton(
                            active = automappingState.activeTarget?.parentDeviceSelectionUUID == selectionUUID &&
                                automappingState.activeTarget?.groupId == openedGroup.id,
                            onClick = {
                                AutomappingManager.toggleTarget(
                                    parentDevice = this@GroupChainDevice,
                                    groupId = openedGroup.id,
                                )
                            },
                        )
                    }
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
        AutomappingManager.clearTargetIfMissing(this, state.value.groups)
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
        AutomappingManager.clearTargetIfMissing(this, state.value.groups)
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
}

@Serializable
data class GroupChainDeviceState(
    val openedGroupIndex: Int = 0,
    val groups: List<Group> = emptyList()
) : DeviceState()
