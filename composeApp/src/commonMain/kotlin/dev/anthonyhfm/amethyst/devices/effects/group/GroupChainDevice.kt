package dev.anthonyhfm.amethyst.devices.effects.group

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.contextmenu.ContextMenuArea
import dev.anthonyhfm.amethyst.ui.contextmenu.ContextMenuItem
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.ui.HiddenDevicePickerButton
import dev.anthonyhfm.amethyst.workspace.chain.ui.TitleBarModifierProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableRow
import sh.calvin.reorderable.ReorderableScope
import sh.calvin.reorderable.rememberReorderableLazyListState

class GroupChainDevice(
    private val sampling: Boolean = false
) : ChainDevice<GroupChainDeviceState>() {
    override val state = MutableStateFlow(GroupChainDeviceState())

    init {
        createGroup()
    }

    companion object {
        private var copiedGroupName: String? = null
    }

    @Composable
    override fun Content() {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            AmethystDevice(
                title = "Group",
                deviceId = internalUUID,
                modifier = Modifier
                    .width(180.dp),
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(28.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    )

                    GroupList()
                }
            }

            key( // Trigger recomposition on selected group change
                state.collectAsState().value
            ) {
                GroupContent()
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .fillMaxHeight()
                    .width(28.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
            )
        }
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
    private fun GroupContent() {
        val groupsState by state.collectAsState()
        val effects by groupsState.groups[groupsState.selectionIndex].chain.devices
        var isDraggingAny by remember { mutableStateOf(false) }

        if (effects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(100.dp),

                contentAlignment = Alignment.Center
            ) {
                HiddenDevicePickerButton(
                    sampling = sampling,
                    expanded = true,
                    onAddComponent = {
                        groupsState.groups[groupsState.selectionIndex].chain.add(it)
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HiddenDevicePickerButton(
                    sampling = sampling,
                    onAddComponent = {
                        groupsState.groups[groupsState.selectionIndex].chain.add(it, 0)
                    }
                )

                ReorderableRow(
                    list = effects,
                    onSettle = { fromIndex, toIndex ->
                        isDraggingAny = false
                        reorderDeviceInGroup(groupsState.selectionIndex, fromIndex, toIndex)
                    },
                    onMove = {
                        isDraggingAny = true
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) { index, device, isDragging ->
                    key(device.internalUUID) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChainDeviceItem(
                                device = device,
                                isDragging = isDragging
                            )

                            HiddenDevicePickerButton(
                                sampling = sampling,
                                onAddComponent = {
                                    groupsState.groups[groupsState.selectionIndex].chain.add(it, index + 1)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ReorderableScope.ChainDeviceItem(
        device: ChainDevice<*>,
        isDragging: Boolean
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (isDragging) {
                        Modifier.shadow(8.dp, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    }
                )
        ) {
            TitleBarModifierProvider(Modifier.draggableHandle()) {
                device.Content()
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
        state.value.groups.forEach {
            it.chain.midiEnter(n)
        }
    }

    fun removeGroup(index: Int) {
        if (state.value.groups.size <= 1) {
            // Don't remove the last group
            return
        }

        state.update {
            val newGroups = it.groups.toMutableList().apply {
                removeAt(index)
            }

            // Adjust selection index if needed
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

            // Rename the newly created group
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
        createGroup(index + 1)

        state.update {
            it.copy(
                groups = it.groups.toMutableList().apply {
                    this[index + 1] = Group(
                        name = "Chain ${it.groups.size + 1}",
                        chain = StateChain.pack(group.chain).unpack()
                    )
                }
            )
        }
    }

    /**
     * Reorders a device within a group
     *
     * @param groupIndex the index of the group containing the devices
     * @param fromIndex the current index of the device to be moved
     * @param toIndex the target index to which the device will be moved
     */
    fun reorderDeviceInGroup(groupIndex: Int, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return

        val chain = state.value.groups[groupIndex].chain
        val devices = chain.devices.value.toMutableList()

        if (fromIndex < 0 || fromIndex >= devices.size || toIndex < 0 || toIndex >= devices.size) return

        val device = devices.removeAt(fromIndex)
        devices.add(toIndex, device)

        chain.devices.value = devices
    }

    fun loadFromState(state: GroupChainDeviceState) {
        val unpackedGroups = state.groups.map { group ->
            val unpackedChain = group.stateChain.unpack()
            unpackedChain.midiExit = {
                midiExit?.invoke(it)
            }

            // Erstelle die Gruppe mit der entpackten Chain
            Group(
                name = group.name,
                chain = unpackedChain
            )
        }

        // Aktualisiere den Zustand mit den entpackten Gruppen
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
}

@Serializable
data class GroupChainDeviceState(
    val selectionIndex: Int = 0,
    val groups: List<Group> = emptyList()
) : DeviceState()
