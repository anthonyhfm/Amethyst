package dev.anthonyhfm.amethyst.editor.plugins.group

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
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.editor.plugins.EffectPlugin
import dev.anthonyhfm.amethyst.editor.trackeditor.ui.AddComponentSpacer
import dev.anthonyhfm.amethyst.ui.components.AmethystPlugin
import dev.anthonyhfm.amethyst.ui.contextmenu.ContextMenuArea
import dev.anthonyhfm.amethyst.ui.contextmenu.ContextMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.exp

/**
 * # The Group Plugin
 *
 * The Group Plugin is very different to the other plugins because of its ability to contain other plugins
 */
class GroupPlugin : EffectPlugin() {
    private val groups: MutableStateFlow<List<GroupData>> = MutableStateFlow(
        value = listOf(
            GroupData(
                name = "Group 1",
            ),
        )
    )

    private val selectedGroupIndex: MutableStateFlow<Int> = MutableStateFlow(0)

    @Composable
    override fun Content() {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AmethystPlugin(
                title = "Group",
                modifier = Modifier
                    .width(180.dp),
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.2.dp))
                    )

                    GroupList()
                }
            }

            key( // Trigger recomposition on selected group change
                selectedGroupIndex.collectAsState().value
            ) {
                GroupContent()
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .fillMaxHeight()
                    .width(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp), RoundedCornerShape(6.dp))
            )
        }
    }

    @Composable
    private fun GroupList() {
        val scope = rememberCoroutineScope()
        val groupsState by groups.collectAsState()
        val selectionIndex by selectedGroupIndex.collectAsState()
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 8.dp),
        ) {
            groupsState.forEachIndexed { index, groupData ->
                AddGroupButton(
                    onAddGroup = {
                        createGroup(index)
                    }
                )

                ContextMenuArea(
                    items = listOf(
                        ContextMenuItem("Copy") { },
                        ContextMenuItem("Paste") { },
                        ContextMenuItem("Duplicate") { },
                        ContextMenuItem("Remove") { },
                    )
                ) {
                    GroupItem(
                        groupData = groupData,
                        selected = selectionIndex == index,
                        onSelect = {
                            scope.launch {
                                selectedGroupIndex.emit(index)
                            }
                        }
                    )
                }
            }

            AddGroupButton(
                expanded = true,
                onAddGroup = {
                    createGroup()
                }
            )
        }
    }

    @Composable
    private fun GroupItem(
        groupData: GroupData,
        selected: Boolean,
        onSelect: () -> Unit
    ) {
        Box(
            modifier = Modifier
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
                text = groupData.name,
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
    private fun ColumnScope.AddGroupButton(
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
        val groupsState by groups.collectAsState()
        val selectionIndex by selectedGroupIndex.collectAsState()
        val effects by groupsState[selectionIndex].effects.collectAsState()

        if (effects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(100.dp),

                contentAlignment = Alignment.Center
            ) {
                AddComponentSpacer(
                    expanded = true,
                    onAddComponent = {
                        addEffectToGroup(selectionIndex, it)
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxHeight(),
            ) {
                groupsState[selectionIndex].effects.value.forEachIndexed { index, effectPlugin ->
                    AddComponentSpacer(
                        onAddComponent = {
                            addEffectToGroup(selectionIndex, it, index)
                        }
                    )

                    effectPlugin.Content()
                }

                AddComponentSpacer(
                    onAddComponent = {
                        addEffectToGroup(selectionIndex, it)
                    }
                )
            }
        }
    }

    fun createGroup(atIndex: Int? = null) {
        CoroutineScope(Dispatchers.Main).launch {
            if (atIndex == null) {
                groups.emit(
                    groups.value.plus(
                        GroupData(
                            name = "Group ${groups.value.size + 1}"
                        )
                    )
                )
            } else {
                val mutableList = groups.value.toMutableList()

                mutableList.add(
                    index = atIndex,
                    element = GroupData(
                        name = "Group ${groups.value.size + 1}"
                    )
                )

                groups.emit(mutableList)
            }
        }
    }

    fun deleteGroup(index: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val mutableList = groups.value.toMutableList()
            mutableList.removeAt(index)

            groups.emit(mutableList)
        }
    }

    fun addEffectToGroup(groupIndex: Int, effect: EffectPlugin, atIndex: Int? = null) {
        CoroutineScope(Dispatchers.Main).launch {
            if (atIndex == null) {
                groups.value[groupIndex].effects.emit(
                    value = groups.value[groupIndex].effects.value.plus(effect)
                )
            } else {
                val mutableList = groups.value[groupIndex].effects.value.toMutableList()

                mutableList.add(atIndex, effect)

                groups.value[groupIndex].effects.emit(
                    value = mutableList
                )
            }

            groups.value[groupIndex].effects.emit(
                groups.value[groupIndex].effects.value.mapIndexed { index, effectPlugin ->
                    if (index + 1 < groups.value[groupIndex].effects.value.size) {
                        effectPlugin.midiOutput = {
                            CoroutineScope(Dispatchers.IO).launch {
                                groups.value[groupIndex].effects.value[index + 1].passData(it)
                            }
                        }

                        return@mapIndexed effectPlugin
                    } else {
                        effectPlugin.midiOutput = {
                            midiOutput(it)
                        }

                        return@mapIndexed effectPlugin
                    }
                }
            )
        }
    }

    override suspend fun passData(data: MidiEffectData) {
        groups.value.forEach {
            val effect = it.effects.value.getOrNull(0)

            if (effect != null) {
                effect.passData(data)
            } else {
                midiOutput(data)
            }
        }
    }
}