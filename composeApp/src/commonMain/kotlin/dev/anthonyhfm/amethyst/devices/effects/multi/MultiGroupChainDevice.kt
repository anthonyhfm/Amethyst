package dev.anthonyhfm.amethyst.devices.effects.multi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.contextmenu.ContextMenuArea
import dev.anthonyhfm.amethyst.ui.contextmenu.ContextMenuItem
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.ui.ExpandingChainDevicePicker
import dev.anthonyhfm.amethyst.workspace.chain.ui.TitleBarModifierProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class MultiGroupChainDevice : ChainDevice<MultiGroupChainDeviceState>() {
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
        dragAndDropState: DragAndDropState<ChainDevice<*>> = rememberDragAndDropState()
    ) {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

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

                    GroupList()
                }
            }

            key( // Trigger recomposition on selected group change
                deviceState.groups, deviceState.selectionIndex
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
                            ContextMenuItem("Remove") { removeGroup(index) },
                        )
                    ) {
                        GroupItem(
                            group = group,
                            selected = groupsState.selectionIndex == index,
                            onSelect = {
                                state.update {
                                    it.copy(
                                        selectionIndex = index
                                    )
                                }
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

    @Composable
    private fun ReorderableCollectionItemScope.GroupItem(
        group: Group,
        selected: Boolean,
        onSelect: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .draggableHandle()
                .clip(RoundedCornerShape(2.dp))
                .fillMaxWidth()
                .height(28.dp)
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    }
                )
                .clickable {
                    onSelect()
                }
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.labelLarge,
                lineHeight = MaterialTheme.typography.labelLarge.fontSize,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp),

                color = if (selected) {
                    MaterialTheme.colorScheme.onTertiary
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
            )
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
    private fun GroupContent(dragAndDropState: DragAndDropState<ChainDevice<*>>) {
        val groupsState by state.collectAsState()
        val devices by groupsState.groups[groupsState.selectionIndex].chain.devices

        if (devices.isEmpty()) {
            ExpandingChainDevicePicker(
                dragAndDropState = dragAndDropState,
                expanded = true,
                expandedWidth = 100.dp,
                onAddComponent = {
                    groupsState.groups[groupsState.selectionIndex].chain.add(it)
                }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpandingChainDevicePicker(
                    dragAndDropState = dragAndDropState,
                    onAddComponent = {
                        groupsState.groups[groupsState.selectionIndex].chain.add(it, 0)
                    }
                )

                devices.forEachIndexed { index, device ->
                    DraggableItem(
                        state = dragAndDropState,
                        key = device.selectionUUID,
                        dragAfterLongPress = true,
                        data = device,
                    ) {
                        TitleBarModifierProvider(
                            Modifier
                                .clickable {
                                    SelectionManager.select(
                                        Selectable.ChainDevice(
                                            parent = groupsState.groups[groupsState.selectionIndex].chain,
                                            device = device
                                        )
                                    )
                                }
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
                        dragAndDropState = dragAndDropState,
                        onAddComponent = {
                            groupsState.groups[groupsState.selectionIndex].chain.add(it, index + 1)
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
                        add(Group("Chain ${it.groups.size + 1}"))
                    } else {
                        add(atIndex, Group("Chain ${it.groups.size + 1}"))
                    }
                }
            )

            if (atIndex != null) {
                out.groups[atIndex].chain.midiExit = {
                    midiExit?.invoke(it)
                }
            } else {
                out.groups.last().chain.midiExit = {
                    midiExit?.invoke(it)
                }
            }

            out
        }
    }

    override fun midiEnter(n: List<Signal>) {
        n.forEach {
            when (state.value.type) {
                MultiGroupChainDeviceState.TYPE.FORWARD -> {
                    if (it.color != Color.Black) {
                        multiMap[Pair(it.x, it.y)] = state.value.currentMultiIndex

                        if (state.value.currentMultiIndex < state.value.groups.size - 1) {
                            state.update {
                                it.copy(currentMultiIndex = it.currentMultiIndex + 1)
                            }
                        } else {
                            state.update {
                                it.copy(currentMultiIndex = 0)
                            }
                        }

                        multiMap[Pair(it.x, it.y)]?.let { index ->
                            state.value.groups[index].chain.midiEnter(listOf(it))
                        }
                    } else {
                        multiMap[Pair(it.x, it.y)]?.let { index ->
                            state.value.groups[index].chain.midiEnter(listOf(it))
                        }

                        multiMap.remove(Pair(it.x, it.y))
                    }
                }

                MultiGroupChainDeviceState.TYPE.BACKWARD -> {
                    if (it.color != Color.Black) {
                        multiMap[Pair(it.x, it.y)] = state.value.currentMultiIndex

                        if (state.value.currentMultiIndex > 0) {
                            state.update {
                                it.copy(currentMultiIndex = it.currentMultiIndex - 1)
                            }
                        } else {
                            state.update {
                                it.copy(currentMultiIndex = state.value.groups.size - 1)
                            }
                        }

                        multiMap[Pair(it.x, it.y)]?.let { index ->
                            state.value.groups[index].chain.midiEnter(listOf(it))
                        }
                    } else {
                        multiMap[Pair(it.x, it.y)]?.let { index ->
                            state.value.groups[index].chain.midiEnter(listOf(it))
                        }

                        multiMap.remove(Pair(it.x, it.y))
                    }
                }

                MultiGroupChainDeviceState.TYPE.RANDOM -> {
                    if (state.value.groups.isNotEmpty()) {
                        val randomIndex = (state.value.currentMultiIndex + (0..state.value.groups.size - 1).random()) % state.value.groups.size
                        state.value.groups[randomIndex].chain.midiEnter(listOf(it))

                        if (it.color != Color.Black) {
                            multiMap[Pair(it.x, it.y)] = randomIndex

                            multiMap[Pair(it.x, it.y)]?.let { index ->
                                state.value.groups[index].chain.midiEnter(listOf(it))
                            }
                        } else {
                            multiMap[Pair(it.x, it.y)]?.let { index ->
                                state.value.groups[index].chain.midiEnter(listOf(it))
                            }

                            multiMap.remove(Pair(it.x, it.y))
                        }
                    }
                }
            }
        }
    }

    fun removeGroup(index: Int) {
        if (state.value.groups.size <= 1) {
            return
        }

        state.update {
            val newGroups = it.groups.toMutableList().apply {
                removeAt(index)
            }

            val newSelectionIndex = when {
                it.selectionIndex >= newGroups.size -> newGroups.size - 1
                it.selectionIndex > index -> it.selectionIndex - 1
                else -> it.selectionIndex
            }

            it.copy(
                groups = newGroups,
                selectionIndex = newSelectionIndex
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

    fun duplicateGroup(index: Int) {
        val group = state.value.groups[index]

        state.update {
            val out = it.copy(
                groups = it.groups.toMutableList().apply {
                    add(
                        index = index,
                        element = Group(
                            name = "Chain ${it.groups.size + 1}",
                            chain = StateChain.pack(group.chain).unpack()
                        )
                    )
                }
            )

            out.groups[index].chain.midiExit = {
                midiExit?.invoke(it)
            }

            out
        }
    }

    fun loadFromState(state: MultiGroupChainDeviceState) {
        val unpackedGroups = state.groups.map { group ->
            val unpackedChain = group.stateChain.unpack()
            unpackedChain.midiExit = {
                midiExit?.invoke(it)
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

        if (this.state.value.selectionIndex >= this.state.value.groups.size || this.state.value.selectionIndex < 0) {
            this.state.update {
                it.copy(selectionIndex = 0)
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
}

@Serializable
data class MultiGroupChainDeviceState(
    val selectionIndex: Int = 0,
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
