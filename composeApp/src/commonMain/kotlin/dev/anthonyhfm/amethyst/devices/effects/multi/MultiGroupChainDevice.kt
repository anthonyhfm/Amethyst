package dev.anthonyhfm.amethyst.devices.effects.multi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorActionLayer
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorActions
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorContentHost
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorList
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorRail
import dev.anthonyhfm.amethyst.devices.effects.group.editor.rememberGroupEditorUiState
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurface
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.ui.AnimatedInsertedDevice
import dev.anthonyhfm.amethyst.workspace.chain.ui.DeviceInsertionAnimator
import dev.anthonyhfm.amethyst.workspace.chain.ui.ExpandingChainDevicePicker
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.chain.ui.TitleBarModifierProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.NestedChainDevice

class MultiGroupChainDevice : GenericChainDevice<MultiGroupChainDeviceState>(), NestedChainDevice {
    override val state = MutableStateFlow(MultiGroupChainDeviceState())

    private val actionLayer = GroupEditorActionLayer(
        device = this,
        stateFlow = state,
        groupsOf = MultiGroupChainDeviceState::groups,
        openedGroupIndexOf = MultiGroupChainDeviceState::openedGroupIndex,
        buildState = { beforeState, groups, openedGroupIndex ->
            beforeState.copy(
                groups = groups,
                openedGroupIndex = openedGroupIndex,
                currentMultiIndex = resolveCurrentMultiIndex(
                    beforeGroups = beforeState.groups,
                    beforeIndex = beforeState.currentMultiIndex,
                    afterGroups = groups,
                ),
            )
        },
    )

    init {
        createGroup()
    }

    val multiMap: MutableMap<Pair<Int, Int>, Int> = mutableMapOf()

    internal var preprocessChain: Chain = Chain()
    @Composable
    fun Content(
        dragAndDropState: DragAndDropState<GenericChainDevice<*>> = rememberDragAndDropState(),
    ) {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == selectionUUID }
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
        val openedGroup = deviceState.groups.getOrNull(deviceState.openedGroupIndex)

        Row(
            modifier = Modifier
                .clip(DefaultShape)
                .fillMaxHeight()
                .background(Theme[chainColorTokens][chainSurface])
        ) {
            GroupEditorRail(isSelected = isSelected, bordered = true)

            PreprocessChainContent(dragAndDropState)

            ChainDeviceShell(
                title = "Multi",
                isSelected = isSelected,
                modifier = Modifier.width(180.dp),
                titleBarModifier = LocalTitleBarModifier.current,
            ) {
                Row {
                    GroupEditorRail(
                        isSelected = isSelected,
                    ) {
                        val automappingState by AutomappingManager.state.collectAsState()
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp),
                        ) {
                            dev.anthonyhfm.amethyst.devices.effects.group.editor.AutomappingToggleButton(
                                active = automappingState.activeTarget?.parentDeviceSelectionUUID == selectionUUID,
                                onClick = {
                                    AutomappingManager.toggleTarget(
                                        parentDevice = this@MultiGroupChainDevice,
                                    )
                                },
                            )
                        }
                    }

                    Column {
                        Box(modifier = Modifier.weight(1f)) {
                            GroupEditorList(
                                parentDevice = this@MultiGroupChainDevice,
                                groups = deviceState.groups,
                                openedGroupIndex = deviceState.openedGroupIndex,
                                uiState = editorUiState,
                                actions = editorActions,
                            )
                        }

                        MultiGroupModeFooter(
                            deviceState = deviceState,
                            onModeSelected = ::setMode,
                        )
                    }
                }
            }

            if (openedGroup != null) {
                MultiGroupContentPane(
                    openedGroup = openedGroup,
                    dragAndDropState = dragAndDropState,
                    uiState = editorUiState,
                )
            }

            GroupEditorRail(isSelected = isSelected, bordered = true)
        }
    }

    @Composable
    override fun Content() {
        Content(rememberDragAndDropState())
    }

    @Composable
    private fun MultiGroupModeFooter(
        deviceState: MultiGroupChainDeviceState,
        onModeSelected: (MultiGroupChainDeviceState.TYPE) -> Unit,
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Mode",
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )

            Select(
                value = deviceState.type.dropdownLabel(),
                onValueChange = {},
                modifier = Modifier
                    .width(164.dp),
                shape = SmallShape,
                triggerHeight = 24.dp,
                triggerContentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                MultiGroupChainDeviceState.TYPE.entries.forEach { type ->
                    SelectItem(
                        text = type.dropdownLabel(),
                        selected = type == deviceState.type,
                        onClick = { onModeSelected(type) },
                    )
                }
            }
        }
    }

    @Composable
    private fun MultiGroupContentPane(
        openedGroup: Group,
        dragAndDropState: DragAndDropState<GenericChainDevice<*>>,
        uiState: dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorUiState,
    ) {
        key(openedGroup.id) {
            GroupEditorContentHost(
                parentSelectionUUID = selectionUUID,
                group = openedGroup,
                dragAndDropState = dragAndDropState,
                uiState = uiState,
            )
        }
    }

    @Composable
    private fun PreprocessChainContent(dragAndDropState: DragAndDropState<GenericChainDevice<*>>) {
        val devices by preprocessChain.devices

        Box(modifier = Modifier.fillMaxHeight()) {
            key(devices) {
                if (devices.isEmpty()) {
                    ExpandingChainDevicePicker(
                        destinationChain = preprocessChain,
                        slotIndex = 0,
                        dragAndDropState = dragAndDropState,
                        expanded = true,
                        expandedWidth = 100.dp,
                        onAddComponent = { preprocessChain.add(it) },
                        onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                            if (originalUUID == selectionUUID) return@ExpandingChainDevicePicker
                            val insertionIndex = 0
                            val safeIndex = insertionIndex.coerceIn(0, preprocessChain.devices.value.size)
                            preprocessChain.add(device, safeIndex, fromUser = false)
                            UndoManager.addAction(
                                UndoableAction.MovedChainDevice(
                                    chainBefore = originChain,
                                    chainAfter = preprocessChain,
                                    device = device,
                                    fromIndex = originalIndex,
                                    toIndex = preprocessChain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
                                )
                            )
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExpandingChainDevicePicker(
                            destinationChain = preprocessChain,
                            slotIndex = 0,
                            dragAndDropState = dragAndDropState,
                            onAddComponent = { preprocessChain.add(it, 0) },
                            onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                                if (originalUUID == selectionUUID) return@ExpandingChainDevicePicker
                                DeviceInsertionAnimator.register(device.selectionUUID)
                                val insertionIndex = 0
                                val finalIndex = if (originChain === preprocessChain) {
                                    if (originalIndex < insertionIndex) insertionIndex - 1 else insertionIndex
                                } else insertionIndex
                                val safeIndex = finalIndex.coerceIn(0, preprocessChain.devices.value.size)
                                preprocessChain.add(device, safeIndex, fromUser = false)
                                UndoManager.addAction(
                                    UndoableAction.MovedChainDevice(
                                        chainBefore = originChain,
                                        chainAfter = preprocessChain,
                                        device = device,
                                        fromIndex = originalIndex,
                                        toIndex = preprocessChain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
                                    )
                                )
                            }
                        )

                        devices.forEachIndexed { index, device ->
                            DraggableItem(
                                state = dragAndDropState,
                                key = device.selectionUUID,
                                data = device,
                                useDragAnchor = true,
                            ) {
                                TitleBarModifierProvider(
                                    Modifier
                                        .clickable {
                                            val selectable = Selectable.ChainDevice(
                                                parent = preprocessChain,
                                                device = device
                                            )
                                            when {
                                                ModifierKeysState.isShiftPressed -> SelectionManager.selectRangeInChain(
                                                    targetDevice = selectable,
                                                    devicesInChain = devices,
                                                )
                                                ModifierKeysState.isMetaPressed || ModifierKeysState.isAltPressed -> SelectionManager.select(selectable, single = false)
                                                else -> SelectionManager.select(selectable)
                                            }
                                        }
                                        .dragAnchor()
                                ) {
                                    LaunchedEffect(dragAndDropState.draggedItem) {
                                        device.isDragging.value = device.selectionUUID == dragAndDropState.draggedItem?.key
                                    }
                                    AnimatedInsertedDevice(id = device.selectionUUID) {
                                        when (device) {
                                            is GroupChainDevice -> device.Content(dragAndDropState = dragAndDropState)
                                            is MultiGroupChainDevice -> device.Content(dragAndDropState = dragAndDropState)
                                            is ChokeChainDevice -> device.Content(dragAndDropState = dragAndDropState)
                                            else -> device.Content()
                                        }
                                    }
                                }
                            }

                            ExpandingChainDevicePicker(
                                destinationChain = preprocessChain,
                                slotIndex = index + 1,
                                dragAndDropState = dragAndDropState,
                                expanded = index == devices.lastIndex,
                                onAddComponent = { preprocessChain.add(it, index + 1) },
                                onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                                    if (originalUUID == selectionUUID) return@ExpandingChainDevicePicker
                                    DeviceInsertionAnimator.register(device.selectionUUID)
                                    val insertionIndex = index + 1
                                    val finalIndex = if (originChain === preprocessChain) {
                                        if (originalIndex < insertionIndex) insertionIndex - 1 else insertionIndex
                                    } else insertionIndex
                                    val safeIndex = finalIndex.coerceIn(0, preprocessChain.devices.value.size)
                                    preprocessChain.add(device, safeIndex, fromUser = false)
                                    UndoManager.addAction(
                                        UndoableAction.MovedChainDevice(
                                            chainBefore = originChain,
                                            chainAfter = preprocessChain,
                                            device = device,
                                            fromIndex = originalIndex,
                                            toIndex = preprocessChain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setMode(type: MultiGroupChainDeviceState.TYPE) {
        val beforeState = state.value
        if (beforeState.type == type) return

        val afterState = beforeState.copy(type = type)
        state.value = afterState
        pushStateChange(beforeState, afterState)
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
        n.forEach {
            val down: Boolean = when (it) {
                is Signal.LED -> it.color != Color.Black
                is Signal.Midi -> it.velocity != 0
                else -> return
            }

            val coords: Pair<Int, Int> = when (it) {
                is Signal.LED -> Pair(it.x, it.y)
                is Signal.Midi -> Pair(it.x, it.y)
                else -> return
            }

            when (state.value.type) {
                MultiGroupChainDeviceState.TYPE.FORWARD -> {
                    if (down) {
                        multiMap[coords] = state.value.currentMultiIndex

                        if (state.value.currentMultiIndex < state.value.groups.size - 1) {
                            state.update {
                                it.copy(currentMultiIndex = it.currentMultiIndex + 1)
                            }
                        } else {
                            state.update {
                                it.copy(currentMultiIndex = 0)
                            }
                        }

                        multiMap[coords]?.let { index ->
                            routeThroughPreprocess(it, index)
                        }
                    } else {
                        multiMap[coords]?.let { index ->
                            routeThroughPreprocess(it, index)
                        }

                        multiMap.remove(coords)
                    }
                }

                MultiGroupChainDeviceState.TYPE.BACKWARD -> {
                    if (down) {
                        multiMap[coords] = state.value.currentMultiIndex

                        if (state.value.currentMultiIndex > 0) {
                            state.update {
                                it.copy(currentMultiIndex = it.currentMultiIndex - 1)
                            }
                        } else {
                            state.update {
                                it.copy(currentMultiIndex = state.value.groups.size - 1)
                            }
                        }

                        multiMap[coords]?.let { index ->
                            routeThroughPreprocess(it, index)
                        }
                    } else {
                        multiMap[coords]?.let { index ->
                            routeThroughPreprocess(it, index)
                        }

                        multiMap.remove(coords)
                    }
                }

                MultiGroupChainDeviceState.TYPE.RANDOM -> {
                    if (state.value.groups.isNotEmpty()) {
                        val randomIndex =
                            (state.value.currentMultiIndex + (0..state.value.groups.size - 1).random()) % state.value.groups.size
                        routeThroughPreprocess(it, randomIndex)

                        if (down) {
                            multiMap[coords] = randomIndex

                            multiMap[coords]?.let { index ->
                                routeThroughPreprocess(it, index)
                            }
                        } else {
                            multiMap[coords]?.let { index ->
                                routeThroughPreprocess(it, index)
                            }

                            multiMap.remove(coords)
                        }
                    }
                }
            }
        }
    }

    private fun routeThroughPreprocess(signal: Signal, targetGroupIndex: Int) {
        val group = state.value.groups.getOrNull(targetGroupIndex) ?: return
        if (preprocessChain.devices.value.isEmpty()) {
            group.chain.signalEnter(listOf(signal))
        } else {
            preprocessChain.signalExit = { preprocessed ->
                group.chain.signalEnter(preprocessed)
            }
            preprocessChain.signalEnter(listOf(signal))
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

    fun loadFromState(savedState: MultiGroupChainDeviceState) {
        val unpackedGroups = savedState.groups.map { group ->
            val unpackedChain = group.stateChain.unpack()
            unpackedChain.signalExit = {
                signalExit?.invoke(it)
            }

            Group(
                name = group.name,
                chain = unpackedChain,
                stateChain = group.stateChain,
                id = group.id,
            )
        }

        val unpackedPreprocess = savedState.preprocessChain.unpack()
        preprocessChain.devices.value = unpackedPreprocess.devices.value
        preprocessChain.reroute()

        state.update {
            it.copy(
                groups = unpackedGroups,
                openedGroupIndex = normalizeMultiIndex(unpackedGroups, savedState.openedGroupIndex),
                currentMultiIndex = normalizeMultiIndex(unpackedGroups, savedState.currentMultiIndex),
                type = savedState.type,
            )
        }

        if (state.value.groups.isEmpty()) {
            createGroup()
        }
    }

    fun packState(): MultiGroupChainDeviceState {
        return state.value.copy(
            groups = state.value.groups.map { group ->
                Group(
                    name = group.name,
                    stateChain = StateChain.pack(group.chain),
                    id = group.id,
                )
            },
            preprocessChain = StateChain.pack(preprocessChain),
        )
    }

    fun addGroupInternal(index: Int, group: Group) {
        state.update { currentState ->
            val newGroups = currentState.groups.toMutableList().apply {
                add(
                    index,
                    group.copy(
                        chain = StateChain.pack(group.chain).unpack().apply {
                            signalExit = { signal -> this@MultiGroupChainDevice.signalExit?.invoke(signal) }
                        },
                    ),
                )
            }

            currentState.copy(
                groups = newGroups,
                openedGroupIndex = index,
                currentMultiIndex = resolveCurrentMultiIndex(
                    beforeGroups = currentState.groups,
                    beforeIndex = currentState.currentMultiIndex,
                    afterGroups = newGroups,
                ),
            )
        }
    }

    fun removeGroupInternal(index: Int) {
        if (state.value.groups.size <= 1) return

        state.update { currentState ->
            val newGroups = currentState.groups.toMutableList().apply {
                removeAt(index)
            }

            val newOpenedGroupIndex = when {
                currentState.openedGroupIndex >= newGroups.size -> newGroups.size - 1
                currentState.openedGroupIndex > index -> currentState.openedGroupIndex - 1
                else -> currentState.openedGroupIndex
            }

            currentState.copy(
                groups = newGroups,
                openedGroupIndex = newOpenedGroupIndex,
                currentMultiIndex = resolveCurrentMultiIndex(
                    beforeGroups = currentState.groups,
                    beforeIndex = currentState.currentMultiIndex,
                    afterGroups = newGroups,
                ),
            )
        }
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

    fun insertGroupWithUndo(
        group: Group,
        atIndex: Int? = null,
        selectInsertedGroup: Boolean = true,
    ) {
        actionLayer.insertGroupWithUndo(
            group = group,
            atIndex = atIndex,
            selectInsertedGroup = selectInsertedGroup,
        )
    }

    override fun nestedChains() = state.value.groups.map { it.chain } + listOf(preprocessChain)

    companion object : ChainDeviceFactory<MultiGroupChainDeviceState> {
        override val stateClass = MultiGroupChainDeviceState::class
        override val serializer = MultiGroupChainDeviceState.serializer()
        override fun create() = MultiGroupChainDevice()
        override fun pack(device: GenericChainDevice<MultiGroupChainDeviceState>): MultiGroupChainDeviceState =
            (device as MultiGroupChainDevice).packState()
        override fun unpack(state: MultiGroupChainDeviceState): MultiGroupChainDevice =
            MultiGroupChainDevice().apply { loadFromState(state) }
    }
}

@Serializable
data class MultiGroupChainDeviceState(
    val openedGroupIndex: Int = 0,
    val type: TYPE = TYPE.FORWARD,
    val currentMultiIndex: Int = 0,
    val groups: List<Group> = emptyList(),
    val preprocessChain: StateChain = StateChain(emptyList()),
) : DeviceState() {
    enum class TYPE {
        FORWARD,
        BACKWARD,
        RANDOM,
    }
}

private fun MultiGroupChainDeviceState.TYPE.dropdownLabel(): String {
    return when (this) {
        MultiGroupChainDeviceState.TYPE.FORWARD -> "Forwards"
        MultiGroupChainDeviceState.TYPE.BACKWARD -> "Backwards"
        MultiGroupChainDeviceState.TYPE.RANDOM -> "Random"
    }
}

private fun normalizeMultiIndex(groups: List<Group>, index: Int): Int {
    return if (groups.isEmpty()) {
        0
    } else {
        index.coerceIn(0, groups.lastIndex)
    }
}

private fun resolveCurrentMultiIndex(
    beforeGroups: List<Group>,
    beforeIndex: Int,
    afterGroups: List<Group>,
): Int {
    val previousCurrentGroupId = beforeGroups.getOrNull(beforeIndex)?.id
    return previousCurrentGroupId
        ?.let { currentGroupId -> afterGroups.indexOfFirst { group -> group.id == currentGroupId } }
        ?.takeIf { it >= 0 }
        ?: normalizeMultiIndex(afterGroups, beforeIndex)
}
