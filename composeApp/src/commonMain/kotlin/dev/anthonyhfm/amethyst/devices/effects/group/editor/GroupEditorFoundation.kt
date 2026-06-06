package dev.anthonyhfm.amethyst.devices.effects.group.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.composeunstyled.theme.Theme
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItemScope
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.modifier.hoverRevealEnterTransition
import dev.anthonyhfm.amethyst.ui.modifier.hoverRevealExitTransition
import dev.anthonyhfm.amethyst.ui.modifier.hoverTweenSpec
import dev.anthonyhfm.amethyst.ui.modifier.onFocusSelectAll
import dev.anthonyhfm.amethyst.ui.modifier.rememberDelayedHoverAsState
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurface
import dev.anthonyhfm.amethyst.ui.theme.chainSurfaceRaised
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.ui.AnimatedInsertedDevice
import dev.anthonyhfm.amethyst.workspace.chain.ui.ChainDeviceContextMenu
import dev.anthonyhfm.amethyst.workspace.chain.ui.DeviceInsertionAnimator
import dev.anthonyhfm.amethyst.workspace.chain.ui.ExpandingChainDevicePicker
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.chain.ui.SignalIndicatorManager
import dev.anthonyhfm.amethyst.workspace.chain.ui.TitleBarModifierProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal data class GroupEditorActions(
    val onAddGroup: (Int?) -> Unit,
    val onMoveGroup: (fromIndex: Int, toIndex: Int) -> Unit,
    val onOpenGroup: (Int) -> Unit,
    val onRenameGroup: (Int, String) -> Unit,
    val onCopyGroup: (Group) -> Unit,
    val onPasteGroup: (Int) -> Unit,
    val onDuplicateGroup: (Int) -> Unit,
    val onDuplicateGroups: (List<Int>) -> Unit,
    val onDeleteGroup: (Int) -> Unit,
    val onDeleteGroups: (List<Int>) -> Unit,
    val onPasteDevicesAsGroup: (Int?) -> Unit,
)

internal class GroupEditorUiState(
    val listFocusRequester: FocusRequester,
    val contentFocusRequester: FocusRequester,
) {
    var renamingGroupIndex by mutableStateOf<Int?>(null)
        private set

    var isListPaneFocused by mutableStateOf(false)
        private set

    var isContentPaneFocused by mutableStateOf(false)
        private set

    fun isRenaming(index: Int): Boolean = renamingGroupIndex == index

    fun setRenamingGroup(index: Int?) {
        renamingGroupIndex = index
    }

    fun updateListPaneFocus(focused: Boolean) {
        isListPaneFocused = focused
    }

    fun updateContentPaneFocus(focused: Boolean) {
        isContentPaneFocused = focused
    }

    fun requestListFocus() {
        listFocusRequester.requestFocus()
    }

    fun requestContentFocus() {
        contentFocusRequester.requestFocus()
    }
}

@Composable
internal fun rememberGroupEditorUiState(parentSelectionUUID: String): GroupEditorUiState {
    val uiState = remember(parentSelectionUUID) {
        GroupEditorUiState(
            listFocusRequester = FocusRequester(),
            contentFocusRequester = FocusRequester(),
        )
    }
    LaunchedEffect(parentSelectionUUID) {
        SelectionManager.renameRequest.collect { req ->
            val groupRequest = req as? SelectionManager.RenameTarget.GroupItem ?: return@collect
            if (groupRequest.parentUUID != parentSelectionUUID) return@collect

            uiState.setRenamingGroup(groupRequest.groupIndex)
        }
    }

    return uiState
}

@Composable
internal fun GroupEditorScaffold(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    leadingStripContent: @Composable BoxScope.() -> Unit = {},
    groupList: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(DefaultShape)
            .fillMaxHeight()
            .background(Theme[chainColorTokens][chainSurface])
    ) {
        ChainDeviceShell(
            title = title,
            isSelected = isSelected,
            modifier = Modifier.width(180.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Row {
                GroupEditorRail(
                    isSelected = isSelected,
                    content = leadingStripContent,
                )

                Column {
                    Box(
                        modifier = Modifier.weight(1f),
                        content = groupList,
                    )

                    if (footer != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Theme[chainColorTokens][chainSurface])
                        ) {
                            footer()
                        }
                    }
                }
            }
        }

        content()

        GroupEditorRail(
            isSelected = isSelected,
            modifier = Modifier.clip(DefaultShape),
            bordered = true,
        )
    }
}

@Composable
internal fun GroupEditorRail(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    bordered: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .then(
                if (bordered) {
                    if (isSelected) {
                        Modifier
                            .background(Theme[colors][selectionSurface], DefaultShape)
                            .border(1.dp, Theme[colors][selectionSurface], DefaultShape)
                    } else {
                        Modifier
                            .background(Theme[chainColorTokens][chainSurfaceRaised], DefaultShape)
                            .border(1.dp, Theme[chainColorTokens][chainBorder], DefaultShape)
                    }
                } else {
                    if (isSelected) {
                        Modifier.background(Theme[colors][selectionSurface])
                    } else {
                        Modifier.background(Theme[chainColorTokens][chainSurfaceRaised])
                    }
                }
            ),
        content = content,
    )
}

@Composable
internal fun GroupEditorList(
    parentDevice: GenericChainDevice<*>,
    groups: List<Group>,
    openedGroupIndex: Int,
    uiState: GroupEditorUiState,
    actions: GroupEditorActions,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderState<Group>()
    val clipboard by ClipboardManager.clipboardData.collectAsState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .focusRequester(uiState.listFocusRequester)
            .focusGroup()
            .focusable()
            .onFocusChanged { focusState ->
                uiState.updateListPaneFocus(focusState.isFocused)
                if (focusState.isFocused) {
                    uiState.updateContentPaneFocus(false)
                    val openedGroup = groups.getOrNull(openedGroupIndex.coerceAtLeast(0))
                    if (openedGroup != null && !hasGroupSelectionForDevice(parentDevice)) {
                        restoreGroupSelectionForDevice(
                            device = parentDevice,
                            groups = groups,
                            groupIds = listOf(openedGroup.id),
                            clearWhenEmpty = false,
                        )
                    }
                }
            }
            .pointerInput(parentDevice.selectionUUID) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    uiState.requestListFocus()
                }
            }
            .onKeyEvent { keyEvent ->
                handleGroupListKeyEvent(
                    keyEvent = keyEvent,
                    parentDevice = parentDevice,
                    groups = groups,
                    openedGroupIndex = openedGroupIndex,
                    uiState = uiState,
                    actions = actions,
                    clipboard = clipboard,
                    lazyListState = lazyListState,
                    scrollScope = scope,
                )
            }
    ) {
        ReorderContainer(
            state = reorderState,
            modifier = Modifier.fillMaxHeight(),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxHeight()
            ) {
                itemsIndexed(groups, key = { _, group -> group.id }) { index, group ->
                    GroupEditorInsertButton(
                        onAddGroup = {
                            actions.onAddGroup(index)
                        },
                        onPasteDevicesAsGroup = {
                            actions.onPasteDevicesAsGroup(index)
                        },
                    )

                    ReorderableItem(
                        state = reorderState,
                        key = group.id,
                        data = group,
                        enabled = groups.size > 1,
                        useDragAnchor = true,
                        onDragEnter = { draggedState ->
                            val fromIndex = groups.indexOfFirst { currentGroup ->
                                currentGroup.id == draggedState.data.id
                            }

                            if (fromIndex != -1 && fromIndex != index) {
                                actions.onMoveGroup(fromIndex, index)
                            }
                        },
                    ) {
                        GroupEditorListItem(
                            parentDevice = parentDevice,
                            group = group,
                            index = index,
                            opened = openedGroupIndex == index,
                            openedGroupIndex = openedGroupIndex,
                            renameEnabled = uiState.isRenaming(index),
                            onRenameChange = { enabled ->
                                uiState.setRenamingGroup(if (enabled) index else null)
                            },
                            actions = actions,
                        )
                    }
                }

                item {
                    GroupEditorInsertButton(
                        expanded = true,
                        onAddGroup = {
                            actions.onAddGroup(null)
                        },
                        onPasteDevicesAsGroup = {
                            actions.onPasteDevicesAsGroup(null)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupEditorContextMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    variant: ContextMenuItemVariant = ContextMenuItemVariant.Default,
) {
    val contentColor = when {
        !enabled -> Theme[colors][mutedForeground]
        variant == ContextMenuItemVariant.Destructive -> Theme[colors][destructive]
        else -> Theme[colors][popoverForeground]
    }

    ContextMenuItem(
        onClick = onClick,
        enabled = enabled,
        variant = variant,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = Theme[typography][small],
            color = contentColor,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableItemScope.GroupEditorListItem(
    parentDevice: GenericChainDevice<*>,
    group: Group,
    index: Int,
    opened: Boolean,
    openedGroupIndex: Int,
    renameEnabled: Boolean,
    onRenameChange: (Boolean) -> Unit,
    actions: GroupEditorActions,
) {
    val selections by SelectionManager.selections.collectAsState()
    val isSelectedInManager = selections.any {
        it is Selectable.GroupChainItem &&
            it.parent == parentDevice &&
            it.groupIndex == index
    }

    val clipboard by ClipboardManager.clipboardData.collectAsState()
    val hasGroupsInClipboard = clipboard is ClipboardData.GroupChainItem

    val textValue = remember { mutableStateOf(TextFieldValue(group.name)) }
    val focusRequester = remember { FocusRequester() }
    val pulseAlpha = remember { Animatable(0f) }
    val itemBackgroundColor = when {
        isSelectedInManager -> Theme[colors][selectionSurface]
        opened -> Theme[colors][secondary]
        else -> Theme[chainColorTokens][chainSurfaceRaised]
    }
    val itemBorderColor = when {
        isSelectedInManager -> Theme[colors][selectionSurface]
        opened -> Theme[chainColorTokens][chainBorder]
        else -> Theme[chainColorTokens][chainBorder].copy(alpha = 0.9f)
    }
    val itemTextColor = when {
        isSelectedInManager -> Theme[colors][selectionForeground]
        opened -> Theme[colors][secondaryForeground]
        else -> Theme[colors][mutedForeground]
    }
    val pulseBaseColor = if (isSelectedInManager) {
        Theme[colors][selectionForeground].copy(alpha = 0.18f)
    } else {
        Theme[colors][secondary]
    }
    val pulseColor = if (isSelectedInManager) {
        Theme[colors][selectionForeground].copy(alpha = pulseAlpha.value)
    } else {
        Theme[colors][selectionSurface].copy(alpha = pulseAlpha.value)
    }

    val endSlotIndex = group.chain.devices.value.size
    val pulseEvents = remember(group.chain, endSlotIndex) {
        SignalIndicatorManager.events(group.chain, endSlotIndex)
    }

    LaunchedEffect(pulseEvents) {
        pulseEvents.collectLatest {
            pulseAlpha.stop()
            pulseAlpha.snapTo(1f)
            pulseAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 600, easing = LinearEasing)
            )
        }
    }

    LaunchedEffect(renameEnabled) {
        if (renameEnabled) {
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

    ContextMenu(
        modifier = Modifier.fillMaxWidth(),
        trigger = {
            Row(
                modifier = Modifier
                    .dragAnchor()
                    .clip(DefaultShape)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(itemBackgroundColor)
                    .border(1.dp, itemBorderColor, DefaultShape)
                    .combinedClickable(
                        onClick = {
                            if (isSelectedInManager && !renameEnabled) {
                                onRenameChange(true)
                            } else {
                                handleGroupItemSelection(
                                    parentDevice = parentDevice,
                                    index = index,
                                    openedGroupIndex = openedGroupIndex,
                                    shiftPressed = ModifierKeysState.isShiftPressed,
                                    ctrlPressed = ModifierKeysState.isCtrlPressed,
                                    onOpenGroup = actions.onOpenGroup,
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
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                            .padding(start = 6.dp),
                        color = itemTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(end = 4.dp)
                            .size(10.dp)
                            .background(pulseBaseColor, CircleShape)
                            .padding(2.dp)
                            .background(
                                pulseColor,
                                CircleShape
                            )
                    )
                } else {
                    val customTextSelectionColors = TextSelectionColors(
                        handleColor = if (isSelectedInManager) itemTextColor else Theme[colors][selectionSurface],
                        backgroundColor = if (isSelectedInManager) {
                            itemTextColor.copy(alpha = 0.28f)
                        } else {
                            Theme[colors][selectionSurface].copy(alpha = 0.28f)
                        }
                    )

                    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                        BasicTextField(
                            value = textValue.value,
                            onValueChange = { textValue.value = it },
                            singleLine = true,
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusSelectAll(textValue)
                                .align(Alignment.CenterVertically)
                                .padding(start = 6.dp)
                                .onKeyEvent { event ->
                                    when (event.key) {
                                        Key.Enter -> {
                                            actions.onRenameGroup(index, textValue.value.text)
                                            onRenameChange(false)
                                            true
                                        }

                                        Key.Escape -> {
                                            onRenameChange(false)
                                            textValue.value = TextFieldValue(group.name)
                                            true
                                        }

                                        else -> false
                                    }
                                },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Unspecified,
                                imeAction = ImeAction.Done
                            ),
                            textStyle = MaterialTheme.typography.labelLarge.copy(color = itemTextColor),
                            cursorBrush = SolidColor(itemTextColor),
                        )
                    }
                }
            }
        }
    ) {
        GroupEditorContextMenuItem(
            label = "Copy",
            icon = Lucide.Copy,
            onClick = { actions.onCopyGroup(group) },
        )

        if (hasGroupsInClipboard) {
            GroupEditorContextMenuItem(
                label = "Paste",
                icon = Lucide.ClipboardPaste,
                onClick = { actions.onPasteGroup(index) },
            )
        }

        GroupEditorContextMenuItem(
            label = "Duplicate",
            icon = Lucide.CopyPlus,
            onClick = { actions.onDuplicateGroup(index) },
        )

        GroupEditorContextMenuItem(
            label = "Rename",
            icon = Lucide.Pencil,
            onClick = { onRenameChange(true) },
        )

        ContextMenuSeparator()

        GroupEditorContextMenuItem(
            label = "Delete",
            icon = Lucide.Trash2,
            variant = ContextMenuItemVariant.Destructive,
            onClick = { actions.onDeleteGroup(index) },
        )
    }
}

@Composable
internal fun AutomappingToggleButton(
    active: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor = if (active) Theme[colors][selectionSurface] else Theme[colors][secondary]
    val contentColor = if (active) Theme[colors][selectionForeground] else Theme[colors][secondaryForeground]

    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "A",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun GroupEditorInsertButton(
    expanded: Boolean = false,
    onAddGroup: () -> Unit,
    onPasteDevicesAsGroup: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovering by rememberDelayedHoverAsState(interaction)
    val clipboard by ClipboardManager.clipboardData.collectAsState()
    val hasDevicesInClipboard = clipboard is ClipboardData.ChainDevice

    val trigger: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    animateDpAsState(
                        targetValue = if (expanded || hovering) {
                            56.dp
                        } else {
                            8.dp
                        },
                        animationSpec = hoverTweenSpec(durationMillis = 100),
                    ).value
                )
                .hoverable(interaction),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = expanded || hovering,
                enter = hoverRevealEnterTransition(),
                exit = hoverRevealExitTransition(),
            ) {
                IconButton(
                    onClick = onAddGroup,
                ) {
                    Icon(Lucide.Plus, null)
                }
            }
        }
    }

    if (hasDevicesInClipboard) {
        ContextMenu(
            modifier = Modifier.fillMaxWidth(),
            trigger = trigger,
        ) {
            GroupEditorContextMenuItem(
                label = "Paste as Group",
                icon = Lucide.ClipboardPaste,
                onClick = onPasteDevicesAsGroup,
            )
        }
    } else {
        trigger()
    }
}

@Composable
internal fun GroupEditorContentHost(
    parentSelectionUUID: String,
    group: Group,
    dragAndDropState: DragAndDropState<GenericChainDevice<*>>,
    uiState: GroupEditorUiState,
) {
    val density = LocalDensity.current.density
    val chain = group.chain
    val devices by chain.devices

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .focusRequester(uiState.contentFocusRequester)
            .focusGroup()
            .focusable()
            .onFocusChanged { focusState ->
                uiState.updateContentPaneFocus(focusState.isFocused)
                if (focusState.isFocused) {
                    uiState.updateListPaneFocus(false)
                }
            }
            .pointerInput(group.id) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    uiState.requestContentFocus()
                }
            }
            // onPreviewKeyEvent is intentional here: the content pane must intercept
            // Escape and Shift+Tab before the chain editor's own key handlers consume them.
            .onPreviewKeyEvent { keyEvent ->
                handleGroupContentKeyEvent(
                    keyEvent = keyEvent,
                    uiState = uiState,
                )
            }
    ) {
        key(devices) {
            if (devices.isEmpty()) {
                ExpandingChainDevicePicker(
                    destinationChain = chain,
                    slotIndex = 0,
                    dragAndDropState = dragAndDropState,
                    expanded = true,
                    expandedWidth = 100.dp,
                    onAddComponent = {
                        chain.add(it)
                    },
                    onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                        dropChainDeviceIntoGroup(
                            parentSelectionUUID = parentSelectionUUID,
                            targetChain = chain,
                            insertionIndex = 0,
                            device = device,
                            originalIndex = originalIndex,
                            originalUUID = originalUUID,
                            originChain = originChain,
                            animateInsertion = false,
                        )
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExpandingChainDevicePicker(
                        destinationChain = chain,
                        slotIndex = 0,
                        dragAndDropState = dragAndDropState,
                        onAddComponent = {
                            chain.add(it, 0)
                        },
                        onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                            dropChainDeviceIntoGroup(
                                parentSelectionUUID = parentSelectionUUID,
                                targetChain = chain,
                                insertionIndex = 0,
                                device = device,
                                originalIndex = originalIndex,
                                originalUUID = originalUUID,
                                originChain = originChain,
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
                            var showRightClickMenu by remember { mutableStateOf(false) }
                            var rightClickMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

                            TitleBarModifierProvider(
                                Modifier
                                    .clickable {
                                        handleNestedChainDeviceSelection(
                                            targetChain = chain,
                                            device = device,
                                            devices = devices,
                                        )
                                    }
                                    .rightClickable {
                                        rightClickMenuOffset = DpOffset((it.x / density).dp, (it.y / density).dp)
                                        showRightClickMenu = true
                                    }
                                    .dragAnchor()
                            ) {
                                LaunchedEffect(dragAndDropState.draggedItem) {
                                    showRightClickMenu = false
                                    device.isDragging.value = device.selectionUUID == dragAndDropState.draggedItem?.key
                                }

                                ChainDeviceContextMenu(
                                    chain = chain,
                                    device = device,
                                    visible = showRightClickMenu,
                                    offset = rightClickMenuOffset,
                                    onDismiss = {
                                        showRightClickMenu = false
                                    }
                                )

                                AnimatedInsertedDevice(device.selectionUUID) {
                                    when (device) {
                                        is GroupChainDevice -> device.Content(
                                            dragAndDropState = dragAndDropState
                                        )

                                        is MultiGroupChainDevice -> device.Content(
                                            dragAndDropState = dragAndDropState
                                        )

                                        else -> device.Content()
                                    }
                                }
                            }
                        }

                        ExpandingChainDevicePicker(
                            destinationChain = chain,
                            slotIndex = index + 1,
                            dragAndDropState = dragAndDropState,
                            expanded = index == devices.lastIndex,
                            onAddComponent = {
                                chain.add(it, index + 1)
                            },
                            onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                                dropChainDeviceIntoGroup(
                                    parentSelectionUUID = parentSelectionUUID,
                                    targetChain = chain,
                                    insertionIndex = index + 1,
                                    device = device,
                                    originalIndex = originalIndex,
                                    originalUUID = originalUUID,
                                    originChain = originChain,
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun handleGroupListKeyEvent(
    keyEvent: KeyEvent,
    parentDevice: GenericChainDevice<*>,
    groups: List<Group>,
    openedGroupIndex: Int,
    uiState: GroupEditorUiState,
    actions: GroupEditorActions,
    clipboard: ClipboardData?,
    lazyListState: LazyListState,
    scrollScope: CoroutineScope,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown || !uiState.isListPaneFocused || uiState.renamingGroupIndex != null || groups.isEmpty()) {
        return false
    }

    val isCommandPressed = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
    val currentIndex = openedGroupIndex.coerceIn(0, groups.lastIndex)
    val selectedIndices = selectedGroupIndicesForDevice(
        parentDevice = parentDevice,
        groups = groups,
    )
    val targetIndex = (selectedIndices.maxOrNull() ?: currentIndex).coerceIn(0, groups.lastIndex)

    fun openAndReveal(index: Int) {
        actions.onOpenGroup(index)
        scrollScope.launch {
            lazyListState.animateScrollToItem(groupListLazyItemIndex(index))
        }
    }

    when {
        !isCommandPressed && !keyEvent.isAltPressed && keyEvent.key == Key.DirectionUp -> {
            val nextIndex = (currentIndex - 1).coerceAtLeast(0)
            if (nextIndex == currentIndex) return true

            if (keyEvent.isShiftPressed) {
                performGroupRangeSelection(
                    parentDevice = parentDevice,
                    startIndex = selectedIndices.maxOrNull() ?: currentIndex,
                    endIndex = nextIndex,
                )
            } else {
                SelectionManager.select(
                    Selectable.GroupChainItem(
                        parent = parentDevice,
                        groupIndex = nextIndex,
                    )
                )
            }

            openAndReveal(nextIndex)
            return true
        }

        !isCommandPressed && !keyEvent.isAltPressed && keyEvent.key == Key.DirectionDown -> {
            val nextIndex = (currentIndex + 1).coerceAtMost(groups.lastIndex)
            if (nextIndex == currentIndex) return true

            if (keyEvent.isShiftPressed) {
                performGroupRangeSelection(
                    parentDevice = parentDevice,
                    startIndex = selectedIndices.minOrNull() ?: currentIndex,
                    endIndex = nextIndex,
                )
            } else {
                SelectionManager.select(
                    Selectable.GroupChainItem(
                        parent = parentDevice,
                        groupIndex = nextIndex,
                    )
                )
            }

            openAndReveal(nextIndex)
            return true
        }

        !isCommandPressed && !keyEvent.isAltPressed && !keyEvent.isShiftPressed &&
            (keyEvent.key == Key.DirectionRight || keyEvent.key == Key.Tab) -> {
            uiState.requestContentFocus()
            return true
        }

        !isCommandPressed && !keyEvent.isAltPressed &&
            (keyEvent.key == Key.F2 || keyEvent.key == Key.Enter) -> {
            beginGroupRename(
                parentDevice = parentDevice,
                index = targetIndex,
                uiState = uiState,
                actions = actions,
            )
            return true
        }

        !isCommandPressed && !keyEvent.isAltPressed &&
            (keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace) -> {
            actions.onDeleteGroups(selectedIndices.ifEmpty { listOf(targetIndex) })
            return true
        }

        !isCommandPressed && !keyEvent.isAltPressed && keyEvent.key == Key.MoveHome -> {
            if (groups.isNotEmpty()) {
                if (keyEvent.isShiftPressed) {
                    performGroupRangeSelection(
                        parentDevice = parentDevice,
                        startIndex = selectedIndices.maxOrNull() ?: currentIndex,
                        endIndex = 0,
                    )
                } else {
                    SelectionManager.select(
                        Selectable.GroupChainItem(parent = parentDevice, groupIndex = 0)
                    )
                }
                openAndReveal(0)
                return true
            }
        }

        !isCommandPressed && !keyEvent.isAltPressed && keyEvent.key == Key.MoveEnd -> {
            if (groups.isNotEmpty()) {
                val lastIndex = groups.lastIndex
                if (keyEvent.isShiftPressed) {
                    performGroupRangeSelection(
                        parentDevice = parentDevice,
                        startIndex = selectedIndices.minOrNull() ?: currentIndex,
                        endIndex = lastIndex,
                    )
                } else {
                    SelectionManager.select(
                        Selectable.GroupChainItem(parent = parentDevice, groupIndex = lastIndex)
                    )
                }
                openAndReveal(lastIndex)
                return true
            }
        }

        isCommandPressed && keyEvent.key == Key.A -> {
            if (groups.isNotEmpty()) {
                SelectionManager.clear()
                groups.indices.forEach { index ->
                    SelectionManager.select(
                        Selectable.GroupChainItem(parent = parentDevice, groupIndex = index),
                        single = false
                    )
                }
                return true
            }
        }

        isCommandPressed && keyEvent.key == Key.C -> {
            groups.getOrNull(targetIndex)?.let(actions.onCopyGroup)
            return true
        }

        isCommandPressed && keyEvent.key == Key.D -> {
            actions.onDuplicateGroups(selectedIndices.ifEmpty { listOf(targetIndex) })
            return true
        }

        isCommandPressed && keyEvent.key == Key.V -> {
            when (clipboard) {
                is ClipboardData.GroupChainItem -> {
                    actions.onPasteGroup(targetIndex)
                    return true
                }

                is ClipboardData.ChainDevice -> {
                    actions.onPasteDevicesAsGroup((targetIndex + 1).coerceAtMost(groups.size))
                    return true
                }

                else -> return false
            }
        }
    }

    return false
}

private fun beginGroupRename(
    parentDevice: GenericChainDevice<*>,
    index: Int,
    uiState: GroupEditorUiState,
    actions: GroupEditorActions,
) {
    SelectionManager.select(
        Selectable.GroupChainItem(
            parent = parentDevice,
            groupIndex = index,
        )
    )
    actions.onOpenGroup(index)
    uiState.setRenamingGroup(index)
}

private fun selectedGroupIndicesForDevice(
    parentDevice: GenericChainDevice<*>,
    groups: List<Group>,
): List<Int> {
    return SelectionManager.selections.value
        .filterIsInstance<Selectable.GroupChainItem>()
        .filter { selection -> selection.parent == parentDevice }
        .map(Selectable.GroupChainItem::groupIndex)
        .filter { index -> index in groups.indices }
        .distinct()
        .sorted()
}

private fun groupListLazyItemIndex(groupIndex: Int): Int {
    return (groupIndex * 2) + 1
}

private fun handleGroupContentKeyEvent(
    keyEvent: KeyEvent,
    uiState: GroupEditorUiState,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown || !uiState.isContentPaneFocused) {
        return false
    }

    return when {
        keyEvent.key == Key.Escape -> {
            uiState.requestListFocus()
            true
        }

        keyEvent.key == Key.Tab && keyEvent.isShiftPressed -> {
            uiState.requestListFocus()
            true
        }

        else -> false
    }
}

internal fun moveGroupList(
    groups: List<Group>,
    fromIndex: Int,
    toIndex: Int,
): List<Group> {
    if (fromIndex == toIndex || fromIndex !in groups.indices || toIndex !in 0..groups.size) {
        return groups
    }

    return groups.toMutableList().apply {
        val movedGroup = removeAt(fromIndex)
        add(toIndex.coerceIn(0, size), movedGroup)
    }
}

private fun handleGroupItemSelection(
    parentDevice: GenericChainDevice<*>,
    index: Int,
    openedGroupIndex: Int,
    shiftPressed: Boolean,
    ctrlPressed: Boolean,
    onOpenGroup: (Int) -> Unit,
) {
    val groupItem = Selectable.GroupChainItem(
        parent = parentDevice,
        groupIndex = index,
    )

    when {
        shiftPressed -> performGroupRangeSelection(
            parentDevice = parentDevice,
            startIndex = openedGroupIndex,
            endIndex = index,
        )

        ctrlPressed -> SelectionManager.select(groupItem, single = false)
        else -> {
            SelectionManager.select(groupItem, single = true)
            onOpenGroup(index)
        }
    }
}

private fun performGroupRangeSelection(
    parentDevice: GenericChainDevice<*>,
    startIndex: Int,
    endIndex: Int,
) {
    val range = if (startIndex < endIndex) {
        startIndex..endIndex
    } else {
        endIndex..startIndex
    }

    SelectionManager.clear()

    range.forEach { index ->
        SelectionManager.select(
            Selectable.GroupChainItem(
                parent = parentDevice,
                groupIndex = index,
            ),
            single = false,
        )
    }
}

private fun handleNestedChainDeviceSelection(
    targetChain: Chain,
    device: GenericChainDevice<*>,
    devices: List<GenericChainDevice<*>>,
) {
    val chainDeviceSelectable = Selectable.ChainDevice(
        parent = targetChain,
        device = device,
    )

    when {
        ModifierKeysState.isShiftPressed -> {
            SelectionManager.selectRangeInChain(
                targetDevice = chainDeviceSelectable,
                devicesInChain = devices,
            )
        }

        ModifierKeysState.isMetaPressed || ModifierKeysState.isAltPressed -> {
            SelectionManager.select(
                chainDeviceSelectable,
                single = false,
            )
        }

        else -> SelectionManager.select(chainDeviceSelectable)
    }
}

private fun dropChainDeviceIntoGroup(
    parentSelectionUUID: String,
    targetChain: Chain,
    insertionIndex: Int,
    device: GenericChainDevice<*>,
    originalIndex: Int,
    originalUUID: String,
    originChain: Chain,
    animateInsertion: Boolean = true,
) {
    if (originalUUID == parentSelectionUUID) {
        return
    }

    if (animateInsertion) {
        DeviceInsertionAnimator.register(device.selectionUUID)
    }

    val finalIndex = if (originChain === targetChain) {
        if (originalIndex < insertionIndex) insertionIndex - 1 else insertionIndex
    } else {
        insertionIndex
    }
    val safeIndex = finalIndex.coerceIn(0, targetChain.devices.value.size)

    targetChain.add(
        device,
        safeIndex,
        fromUser = false,
    )

    UndoManager.addAction(
        UndoableAction.MovedChainDevice(
            chainBefore = originChain,
            chainAfter = targetChain,
            device = device,
            fromIndex = originalIndex,
            toIndex = targetChain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
        )
    )
}
