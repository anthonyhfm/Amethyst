package dev.anthonyhfm.amethyst.devices.effects.multi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.contextmenu.ContextMenuArea
import dev.anthonyhfm.amethyst.ui.contextmenu.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.modifier.onFocusSelectAll
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.ui.ExpandingChainDevicePicker
import dev.anthonyhfm.amethyst.workspace.chain.ui.TitleBarModifierProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class MultiGroupChainDevice : GenericChainDevice<MultiGroupChainDeviceState>() {
    override val state = MutableStateFlow(MultiGroupChainDeviceState())

    init {
        createGroup()
    }

    companion object {
        private var copiedGroupName: String? = null
    }

    val multiMap: MutableMap<Pair<Int, Int>, Int> = mutableMapOf()

    @Composable
    fun Content(
        dragAndDropState: DragAndDropState<GenericChainDevice<*>> = rememberDragAndDropState()
    ) {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }
        var showTypePicker: Boolean by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            AmethystDevice(
                title = "Multi",
                isSelected = isSelected,
                modifier = Modifier
                    .width(180.dp),
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(28.dp)
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(MaterialTheme.colorScheme.primary)
                                } else {
                                    Modifier
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                }
                            )
                    )

                    Column {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            GroupList()
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            DropdownMenu(
                                expanded = showTypePicker,
                                onDismissRequest = {
                                    showTypePicker = false
                                }
                            ) {
                                MultiGroupChainDeviceState.TYPE.entries.forEach { type ->
                                    DropdownMenuItem(
                                        onClick = {
                                            state.update {
                                                it.copy(type = type)
                                            }

                                            showTypePicker = false
                                        },
                                        text = {
                                            Text(
                                                text = when (type) {
                                                    MultiGroupChainDeviceState.TYPE.FORWARD -> "Forwards"
                                                    MultiGroupChainDeviceState.TYPE.BACKWARD -> "Backwards"
                                                    MultiGroupChainDeviceState.TYPE.RANDOM -> "Random"
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = when (type) {
                                                    MultiGroupChainDeviceState.TYPE.FORWARD -> Icons.Default.FastForward
                                                    MultiGroupChainDeviceState.TYPE.BACKWARD -> Icons.Default.FastRewind
                                                    MultiGroupChainDeviceState.TYPE.RANDOM -> Icons.Default.Shuffle
                                                },
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                            }

                            AssistChip(
                                onClick = {
                                    showTypePicker = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                label = {
                                    Text(
                                        text = when (deviceState.type) {
                                            MultiGroupChainDeviceState.TYPE.FORWARD -> "Forwards"
                                            MultiGroupChainDeviceState.TYPE.BACKWARD -> "Backwards"
                                            MultiGroupChainDeviceState.TYPE.RANDOM -> "Random"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                },
                            )
                        }
                    }
                }
            }

            key( // Trigger recomposition on selected group change
                deviceState.groups, deviceState.openedGroupIndex
            ) {
                GroupContent(dragAndDropState)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .fillMaxHeight()
                    .width(28.dp)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        } else {
                            Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                        }
                    )
            )
        }
    }

    @Composable
    override fun Content() {
        Content(rememberDragAndDropState())
    }

    @Composable
    private fun GroupList() {
        val groupsState by state.collectAsState()

        val lazyListState = rememberLazyListState()

        val renamingGroupIndex = remember { mutableStateOf<Int?>(null) }

        // React to external rename requests via SelectionManager
        val renameRequest = SelectionManager.renameRequest.collectAsState().value
        LaunchedEffect(renameRequest) {
            renameRequest?.let { req ->
                if (req.parentUUID == this@MultiGroupChainDevice.selectionUUID) {
                    renamingGroupIndex.value = req.groupIndex
                    // Clear the request so it doesn't retrigger
                    SelectionManager.renameRequest.value = null
                }
            }
        }

        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
            state.update {
                it.copy(
                    groups = it.groups.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                )
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .padding(horizontal = 8.dp)
        ) {
            itemsIndexed(groupsState.groups, key = { _, group -> group }) { index, group ->
                AddGroupButton(
                    onAddGroup = {
                        createGroup(index)
                    }
                )

                ReorderableItem(reorderableLazyListState, key = group) {
                    ContextMenuArea(
                        items = listOf(
                            ContextMenuItem("Copy") { copyGroup(group) },
                            ContextMenuItem("Paste") { pasteGroup(index) },
                            ContextMenuItem("Duplicate") { duplicateGroup(index) },
                            ContextMenuItem("Rename") { renamingGroupIndex.value = index },
                            ContextMenuItem("Remove") { removeGroup(index) }
                        )
                    ) {
                        GroupItem(
                            group = group,
                            index = index,
                            selected = groupsState.openedGroupIndex == index,
                            onSelect = { shiftPressed, ctrlPressed ->
                                val groupChainItem = Selectable.GroupChainItem(
                                    parent = this@MultiGroupChainDevice,
                                    groupIndex = index
                                )

                                when {
                                    shiftPressed -> {
                                        performRangeSelection(index)
                                    }
                                    ctrlPressed -> {
                                        SelectionManager.select(groupChainItem, single = false)
                                    }
                                    else -> {
                                        SelectionManager.select(groupChainItem, single = true)

                                        state.update {
                                            it.copy(
                                                openedGroupIndex = index
                                            )
                                        }
                                    }
                                }
                            },
                            renameEnabled = renamingGroupIndex.value == index,
                            onRenameChange = { enabled ->
                                renamingGroupIndex.value = if (enabled) index else null
                            }
                        )
                    }
                }
            }

            item {
                AddGroupButton(
                    expanded = true,
                    onAddGroup = {
                        createGroup()
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ReorderableCollectionItemScope.GroupItem(
        group: Group,
        index: Int,
        selected: Boolean,
        onSelect: (shiftPressed: Boolean, ctrlPressed: Boolean) -> Unit,
        renameEnabled: Boolean = false,
        onRenameChange: (Boolean) -> Unit,
    ) {
        val selections by SelectionManager.selections.collectAsState()
        val isSelectedInManager = selections.any {
            it is Selectable.GroupChainItem &&
                    it.parent == this@MultiGroupChainDevice &&
                    it.groupIndex == index
        }

        val textValue = remember { mutableStateOf(TextFieldValue(group.name)) }
        val focusRequester = FocusRequester()

        LaunchedEffect(renameEnabled) {
            if (renameEnabled) {
                // Sync text with current group name when starting rename and focus immediately
                textValue.value = TextFieldValue(group.name)
                focusRequester.requestFocus()
            } else {
                focusRequester.freeFocus()
            }
        }

        LaunchedEffect(isSelectedInManager) {
            if (!isSelectedInManager && renameEnabled) {
                onRenameChange(false)
                textValue.value = TextFieldValue(group.name)
            }
        }

        Box(
            modifier = Modifier
                .draggableHandle()
                .clip(RoundedCornerShape(2.dp))
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    color = when {
                        isSelectedInManager -> MaterialTheme.colorScheme.primary
                        selected -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                )
                .combinedClickable(
                    onClick = {
                        if (isSelectedInManager && !renameEnabled) {
                            onRenameChange(true)
                        } else {
                            onSelect(
                                ModifierKeysState.isShiftPressed,
                                ModifierKeysState.isCtrlPressed
                            )
                        }
                    }
                )
        ) {
            if (!renameEnabled) {
                Text(
                    text = group.name.replace("#", "${index + 1}"),
                    style = MaterialTheme.typography.labelLarge,
                    lineHeight = MaterialTheme.typography.labelLarge.fontSize,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 6.dp),
                    color = when {
                        isSelectedInManager -> MaterialTheme.colorScheme.onPrimary
                        selected -> MaterialTheme.colorScheme.onTertiary
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    }
                )
            } else {
                val customTextSelectionColors = TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.secondaryContainer,
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                )

                CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                    BasicTextField(
                        value = textValue.value,
                        onValueChange = { textValue.value = it },
                        singleLine = true,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onFocusSelectAll(textValue)
                            .align(Alignment.CenterStart)
                            .padding(start = 6.dp)
                            .onKeyEvent { ev ->
                                if (ev.key == Key.Enter) {
                                    renameGroup(index, textValue.value.text)
                                    onRenameChange(false)
                                    return@onKeyEvent true
                                }
                                if (ev.key == Key.Escape) {
                                    onRenameChange(false)
                                    textValue.value = TextFieldValue(group.name)
                                    return@onKeyEvent true
                                }

                                false
                            },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Unspecified,
                            imeAction = ImeAction.Done
                        ),
                        textStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onPrimary),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
        }
    }

    @Composable
    private fun AddGroupButton(
        expanded: Boolean = false,
        onAddGroup: () -> Unit
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovering: Boolean by interaction.collectIsHoveredAsState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    height = animateDpAsState(
                        targetValue = if (expanded || hovering) {
                            56.dp
                        } else {
                            8.dp
                        }
                    ).value
                )
                .hoverable(interaction),

            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = expanded || hovering,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(
                    onClick = {
                        onAddGroup()
                    }
                ) {
                    Icon(Icons.Default.Add, null)
                }
            }
        }
    }

    @Composable
    private fun GroupContent(dragAndDropState: DragAndDropState<GenericChainDevice<*>>) {
        val groupsState by state.collectAsState()
        val devices by groupsState.groups[groupsState.openedGroupIndex].chain.devices

        if (devices.isEmpty()) {
            ExpandingChainDevicePicker(
                destinationChain = groupsState.groups[groupsState.openedGroupIndex].chain,
                dragAndDropState = dragAndDropState,
                expanded = true,
                expandedWidth = 100.dp,
                onAddComponent = {
                    groupsState.groups[groupsState.openedGroupIndex].chain.add(it)
                },
                onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                    if (originalUUID == selectionUUID) return@ExpandingChainDevicePicker

                    groupsState.groups[groupsState.openedGroupIndex].chain.add(
                        device,
                        fromUser = false
                    )

                    UndoManager.addAction(
                        UndoableAction.MovedChainDevice(
                            chainBefore = originChain,
                            chainAfter = groupsState.groups[groupsState.openedGroupIndex].chain,
                            device = device,
                            fromIndex = originalIndex,
                            toIndex = groupsState.groups[groupsState.openedGroupIndex].chain.devices.value.indexOfFirst {
                                it.selectionUUID == device.selectionUUID
                            },
                        )
                    )
                }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpandingChainDevicePicker(
                    destinationChain = groupsState.groups[groupsState.openedGroupIndex].chain,
                    dragAndDropState = dragAndDropState,
                    onAddComponent = {
                        groupsState.groups[groupsState.openedGroupIndex].chain.add(it, 0)
                    },
                    onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                        if (originalUUID == selectionUUID) return@ExpandingChainDevicePicker

                        groupsState.groups[groupsState.openedGroupIndex].chain.add(
                            device,
                            fromUser = false
                        )

                        UndoManager.addAction(
                            UndoableAction.MovedChainDevice(
                                chainBefore = originChain,
                                chainAfter = groupsState.groups[groupsState.openedGroupIndex].chain,
                                device = device,
                                fromIndex = originalIndex,
                                toIndex = groupsState.groups[groupsState.openedGroupIndex].chain.devices.value.indexOfFirst {
                                    it.selectionUUID == device.selectionUUID
                                },
                            )
                        )
                    }
                )

                devices.forEachIndexed { index, device ->
                    DraggableItem(
                        state = dragAndDropState,
                        key = device.selectionUUID,
                        data = device,
                        useDragAnchor = true, // Enable drag anchor mode
                    ) {
                        TitleBarModifierProvider(
                            Modifier
                                .clickable {
                                    SelectionManager.select(
                                        Selectable.ChainDevice(
                                            parent = groupsState.groups[groupsState.openedGroupIndex].chain,
                                            device = device
                                        )
                                    )
                                }
                                .dragAnchor() // Add drag anchor to title bar
                        ) {
                            LaunchedEffect(dragAndDropState.draggedItem) {
                                device.isDragging.value = device.selectionUUID == dragAndDropState.draggedItem?.key
                            }

                            when (device) {
                                is GroupChainDevice -> {
                                    device.Content(
                                        dragAndDropState = dragAndDropState
                                    )
                                }

                                is MultiGroupChainDevice -> {
                                    device.Content(
                                        dragAndDropState = dragAndDropState
                                    )
                                }

                                else -> {
                                    device.Content()
                                }
                            }
                        }
                    }

                    ExpandingChainDevicePicker(
                        destinationChain = groupsState.groups[groupsState.openedGroupIndex].chain,
                        dragAndDropState = dragAndDropState,
                        onAddComponent = {
                            groupsState.groups[groupsState.openedGroupIndex].chain.add(it, index + 1)
                        },
                        onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                            if (originalUUID == selectionUUID) return@ExpandingChainDevicePicker

                            groupsState.groups[groupsState.openedGroupIndex].chain.add(
                                device,
                                fromUser = false
                            )

                            UndoManager.addAction(
                                UndoableAction.MovedChainDevice(
                                    chainBefore = originChain,
                                    chainAfter = groupsState.groups[groupsState.openedGroupIndex].chain,
                                    device = device,
                                    fromIndex = originalIndex,
                                    toIndex = groupsState.groups[groupsState.openedGroupIndex].chain.devices.value.indexOfFirst {
                                        it.selectionUUID == device.selectionUUID
                                    },
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    fun createGroup(atIndex: Int? = null) {
        state.update {
            val out = it.copy(
                groups = it.groups.toMutableList().apply {
                    if (atIndex == null) {
                        add(Group("Chain #"))
                    } else {
                        add(atIndex, Group("Chain #"))
                    }
                }
            )

            if (atIndex != null) {
                out.groups[atIndex].chain.signalExit = {
                    signalExit?.invoke(it)
                }
            } else {
                out.groups.last().chain.signalExit = {
                    signalExit?.invoke(it)
                }
            }

            out
        }
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
                            state.value.groups[index].chain.signalEnter(listOf(it))
                        }
                    } else {
                        multiMap[coords]?.let { index ->
                            state.value.groups[index].chain.signalEnter(listOf(it))
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
                            state.value.groups[index].chain.signalEnter(listOf(it))
                        }
                    } else {
                        multiMap[coords]?.let { index ->
                            state.value.groups[index].chain.signalEnter(listOf(it))
                        }

                        multiMap.remove(coords)
                    }
                }

                MultiGroupChainDeviceState.TYPE.RANDOM -> {
                    if (state.value.groups.isNotEmpty()) {
                        val randomIndex = (state.value.currentMultiIndex + (0..state.value.groups.size - 1).random()) % state.value.groups.size
                        state.value.groups[randomIndex].chain.signalEnter(listOf(it))

                        if (down) {
                            multiMap[coords] = randomIndex

                            multiMap[coords]?.let { index ->
                                state.value.groups[index].chain.signalEnter(listOf(it))
                            }
                        } else {
                            multiMap[coords]?.let { index ->
                                state.value.groups[index].chain.signalEnter(listOf(it))
                            }

                            multiMap.remove(coords)
                        }
                    }
                }
            }
        }
    }

    fun removeGroup(index: Int) {
        if (state.value.groups.size - 1 <= 0) {
            return
        }

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
    }

    fun copyGroup(group: Group) {
        copiedGroupName = group.name
    }

    fun pasteGroup(index: Int) {
        copiedGroupName?.let { name ->
            createGroup(index)

            state.update {
                it.copy(
                    groups = it.groups.toMutableList().apply {
                        this[index] = this[index].copy(name = name)
                    }
                )
            }
        }
    }

    fun renameGroup(index: Int, newName: String) {
        state.update {
            it.copy(
                groups = it.groups.toMutableList().apply {
                    this[index] = this[index].copy(name = newName)
                }
            )
        }
    }

    fun duplicateGroup(index: Int, toIndex: Int? = null) {
        val group = state.value.groups[index]

        state.update {
            val out = it.copy(
                groups = it.groups.toMutableList().apply {
                    add(
                        index = toIndex ?: index,
                        element = Group(
                            name = "Chain #",
                            chain = StateChain.pack(group.chain).unpack()
                        )
                    )
                },
                openedGroupIndex = toIndex ?: index
            )

            out.groups[toIndex ?: index].chain.signalExit = {
                signalExit?.invoke(it)
            }

            out
        }
    }

    fun loadFromState(state: MultiGroupChainDeviceState) {
        val unpackedGroups = state.groups.map { group ->
            val unpackedChain = group.stateChain.unpack()
            unpackedChain.signalExit = {
                signalExit?.invoke(it)
            }

            Group(
                name = group.name,
                chain = unpackedChain
            )
        }

        this.state.update {
            state.copy(
                groups = unpackedGroups
            )
        }

        if (this.state.value.groups.isEmpty()) {
            createGroup()
        }

        if (this.state.value.openedGroupIndex >= this.state.value.groups.size || this.state.value.openedGroupIndex < 0) {
            this.state.update {
                it.copy(openedGroupIndex = 0)
            }
        }
    }

    fun packState(): MultiGroupChainDeviceState {
        return state.value.copy(
            groups = state.value.groups.map { group ->
                Group(
                    name = group.name,
                    stateChain = StateChain.pack(group.chain)
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

    // Internal methods for undo/redo operations without triggering UndoManager
    fun addGroupInternal(index: Int, group: Group) {
        state.update {
            val newGroups = it.groups.toMutableList().apply {
                add(index, group.copy(chain = StateChain.pack(group.chain).unpack().apply {
                    signalExit = { signal -> this@MultiGroupChainDevice.signalExit?.invoke(signal) }
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
    }

    // Enhanced copy/paste methods with undo support
    fun duplicateGroups(selectedIndices: List<Int>) {
        if (selectedIndices.isEmpty()) return

        val duplications = mutableListOf<UndoableAction.GroupDuplicationInfo>()
        val sortedIndices = selectedIndices.sortedDescending()

        // Calculate insertion points from the highest selected index
        var insertionOffset = 0

        sortedIndices.forEach { originalIndex ->
            val group = state.value.groups[originalIndex]
            val duplicatedIndex = originalIndex + 1 + insertionOffset

            val duplicatedGroup = Group(
                name = "Chain #",
                chain = StateChain.pack(group.chain).unpack().apply {
                    signalExit = { signal -> this@MultiGroupChainDevice.signalExit?.invoke(signal) }
                }
            )

            addGroupInternal(duplicatedIndex, duplicatedGroup)

            duplications.add(
                UndoableAction.GroupDuplicationInfo(
                    originalIndex = originalIndex,
                    duplicatedIndex = duplicatedIndex,
                    duplicatedGroup = duplicatedGroup
                )
            )

            insertionOffset++
        }

        // Add single or multi duplication action to undo stack
        if (duplications.size == 1) {
            val duplication = duplications.first()
            UndoManager.addAction(
                UndoableAction.MultiGroupDuplicationAction(
                    device = this,
                    originalIndex = duplication.originalIndex,
                    duplicatedIndex = duplication.duplicatedIndex,
                    duplicatedGroup = duplication.duplicatedGroup
                )
            )
        } else {
            UndoManager.addAction(
                UndoableAction.MultiGroupMultiDuplication(
                    device = this,
                    duplications = duplications
                )
            )
        }
    }

    fun pasteGroups(groups: List<dev.anthonyhfm.amethyst.devices.effects.group.data.Group>, targetIndex: Int?) {
        if (groups.isEmpty()) return

        val pastedGroups = mutableListOf<UndoableAction.GroupPasteInfo>()
        val insertIndex = targetIndex ?: state.value.groups.size

        groups.forEachIndexed { offset, group ->
            val pasteIndex = insertIndex + offset
            val pastedGroup = Group(
                name = group.name,
                chain = StateChain.pack(group.chain).unpack().apply {
                    signalExit = { signal -> this@MultiGroupChainDevice.signalExit?.invoke(signal) }
                }
            )

            addGroupInternal(pasteIndex, pastedGroup)

            pastedGroups.add(
                UndoableAction.GroupPasteInfo(
                    groupIndex = pasteIndex,
                    group = pastedGroup
                )
            )
        }

        UndoManager.addAction(
            UndoableAction.MultiGroupPaste(
                device = this,
                pastedGroups = pastedGroups
            )
        )
    }

    // Enhanced removeGroup with undo support for multi-selection
    fun removeGroups(indices: List<Int>) {
        if (indices.isEmpty() || state.value.groups.size <= indices.size) return

        val deletions = mutableListOf<UndoableAction.GroupDeletionInfo>()
        val sortedIndices = indices.sortedDescending()

        sortedIndices.forEach { index ->
            val group = state.value.groups[index]
            deletions.add(
                UndoableAction.GroupDeletionInfo(
                    groupIndex = index,
                    group = group
                )
            )
            removeGroupInternal(index)
        }

        // Add single or multi deletion action to undo stack
        if (deletions.size == 1) {
            val deletion = deletions.first()
            UndoManager.addAction(
                UndoableAction.MultiGroupDeletion(
                    device = this,
                    deletions = listOf(deletion)
                )
            )
        } else {
            UndoManager.addAction(
                UndoableAction.MultiGroupDeletion(
                    device = this,
                    deletions = deletions
                )
            )
        }
    }

    // Enhanced createGroup with undo support
    fun createGroupWithUndo(index: Int? = null) {
        val insertIndex = index ?: state.value.groups.size
        val newGroup = Group(
            name = "Chain #",
            chain = Chain().apply {
                signalExit = { signal -> this@MultiGroupChainDevice.signalExit?.invoke(signal) }
            }
        )

        addGroupInternal(insertIndex, newGroup)

        UndoManager.addAction(
            UndoableAction.MultiGroupCreation(
                device = this,
                groupIndex = insertIndex,
                group = newGroup
            )
        )
    }
}

@Serializable
data class MultiGroupChainDeviceState(
    val openedGroupIndex: Int = 0,
    val type: TYPE = TYPE.FORWARD,
    val currentMultiIndex: Int = 0,
    val groups: List<Group> = emptyList()
) : DeviceState() {
    enum class TYPE {
        FORWARD,
        BACKWARD,
        RANDOM
    }
}
