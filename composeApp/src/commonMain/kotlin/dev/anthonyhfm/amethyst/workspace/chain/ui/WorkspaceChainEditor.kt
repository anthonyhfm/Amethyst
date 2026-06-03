package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import com.composables.icons.lucide.BookOpenText
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Replace
import com.composables.icons.lucide.Trash2
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.network.presence.CollaborationPresence
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollArea
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollAreaState
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberScrollAreaState
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollBarOrientation
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainCanvas
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.help.GetHelpWorkspaceMode

@Composable
fun WorkspaceChainEditor(
    devices: List<GenericChainDevice<*>>,
    scrollState: ScrollAreaState = rememberScrollAreaState(),
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    val density = LocalDensity.current.density
    val dragAndDropState = rememberDragAndDropState<GenericChainDevice<*>>()
    val remoteFocuses by CollaborationPresence.remoteFocuses.collectAsState()
    val remoteCursors by CollaborationPresence.remoteCursors.collectAsState()
    var chain: Chain? by remember { mutableStateOf(null) }

    LaunchedEffect(WorkspaceRepository.mode.collectAsState().value) {
        chain = when (WorkspaceRepository.mode.value) {
            is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspaceRepository.samplingChain
            else -> WorkspaceRepository.lightsChain
        }
    }

    Column(
        modifier = Modifier
            .padding(12.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    SelectionManager.clear()
                }
            ),

        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MacroControls()

        ScrollArea(
            modifier = Modifier
                .clip(DefaultShape)
                .height(280.dp)
                .fillMaxWidth()
                .background(Theme[chainColorTokens][chainCanvas], DefaultShape)
                .border(1.dp, Theme[chainColorTokens][chainBorder], DefaultShape)
                .padding(bottom = 10.dp),
            orientation = ScrollBarOrientation.Horizontal,
            state = scrollState,
        ) {
            Row(
                modifier = Modifier.padding(top = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                key(devices) {
                    if (devices.isNotEmpty()) {
                        DragAndDropContainer(
                            state = dragAndDropState,
                        ) {
                            Row {
                                ExpandingChainDevicePicker(
                                    destinationChain = when (WorkspaceRepository.mode.value) {
                                        is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspaceRepository.samplingChain
                                        else -> WorkspaceRepository.lightsChain
                                    },
                                    slotIndex = 0,
                                    dragAndDropState = dragAndDropState,
                                    expanded = false,
                                    onAddComponent = {
                                        onEvent(WorkspaceContract.Event.AddChainDevice(it, 0))
                                    },
                                    onDropDevice = { device, (originalIndex, _), originChain ->
                                        DeviceInsertionAnimator.register(device.selectionUUID)
                                        val insertionIndex = 0
                                        val finalIndex = if (originChain === chain) {
                                            if (originalIndex < insertionIndex) insertionIndex - 1 else insertionIndex
                                        } else insertionIndex
                                        val safeIndex = finalIndex.coerceIn(0, chain!!.devices.value.size)
                                        chain!!.add(device, safeIndex, fromUser = false)

                                        UndoManager.addAction(
                                            UndoableAction.MovedChainDevice(
                                                chainBefore = originChain,
                                                chainAfter = chain!!,
                                                device = device,
                                                fromIndex = originalIndex,
                                                toIndex = chain!!.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
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
                                        var showRightClickMenu: Boolean by remember { mutableStateOf(false) }
                                        var rightClickMenuOffset: DpOffset by remember { mutableStateOf(DpOffset.Zero) }

                                        TitleBarModifierProvider(
                                            Modifier
                                                .clickable {
                                                    val chainDeviceSelectable = Selectable.ChainDevice(
                                                        parent = when (WorkspaceRepository.mode.value) {
                                                            is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspaceRepository.samplingChain
                                                            else -> WorkspaceRepository.lightsChain
                                                        },
                                                        device = device
                                                    )

                                                    when {
                                                        ModifierKeysState.isShiftPressed -> {
                                                            SelectionManager.selectRangeInChain(
                                                                targetDevice = chainDeviceSelectable,
                                                                devicesInChain = devices
                                                            )
                                                        }
                                                        ModifierKeysState.isMetaPressed || ModifierKeysState.isAltPressed -> {
                                                            SelectionManager.select(
                                                                chainDeviceSelectable,
                                                                single = false
                                                            )
                                                        }
                                                        else -> {
                                                            SelectionManager.select(chainDeviceSelectable)
                                                        }
                                                    }
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
                                                chain = when (WorkspaceRepository.mode.value) {
                                                    is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspaceRepository.samplingChain
                                                    else -> WorkspaceRepository.lightsChain
                                                },
                                                device = device,
                                                visible = showRightClickMenu,
                                                offset = rightClickMenuOffset,
                                                onDismiss = {
                                                    showRightClickMenu = false
                                                }
                                            )

                                            AnimatedInsertedDevice(id = device.selectionUUID) {
                                                val hasRemoteFocus = remoteFocuses.values.any { it == device.selectionUUID }
                                                val remoteFocusColor = remoteFocuses.entries
                                                    .firstOrNull { it.value == device.selectionUUID }
                                                    ?.key
                                                    ?.let { userId -> remoteCursors[userId]?.user?.color }
                                                    ?.let { Color(it) }
                                                    ?: Color(0xFF7C3AED)

                                                Box(
                                                    modifier = if (hasRemoteFocus) {
                                                        Modifier.border(2.dp, remoteFocusColor, DefaultShape)
                                                    } else {
                                                        Modifier
                                                    }
                                                ) {
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
                                        }
                                    }

                                    val insertionIndex = index + 1
                                    ExpandingChainDevicePicker(
                                        destinationChain = when (WorkspaceRepository.mode.value) {
                                            is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspaceRepository.samplingChain
                                            else -> WorkspaceRepository.lightsChain
                                        },
                                        slotIndex = insertionIndex,
                                        dragAndDropState = dragAndDropState,
                                        expanded = index == devices.lastIndex,
                                        onAddComponent = {
                                            onEvent(WorkspaceContract.Event.AddChainDevice(it, insertionIndex))
                                        },
                                        onDropDevice = { device, (originalIndex, _), originChain ->
                                            DeviceInsertionAnimator.register(device.selectionUUID)
                                            val finalIndex = if (originChain === chain) {
                                                if (originalIndex < insertionIndex) insertionIndex - 1 else insertionIndex
                                            } else insertionIndex
                                            val safeIndex = finalIndex.coerceIn(0, chain!!.devices.value.size)
                                            chain!!.add(device, safeIndex, fromUser = false)

                                            UndoManager.addAction(
                                                UndoableAction.MovedChainDevice(
                                                    chainBefore = originChain,
                                                    chainAfter = chain!!,
                                                    device = device,
                                                    fromIndex = originalIndex,
                                                    toIndex = chain!!.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        ExpandingChainDevicePicker(
                            destinationChain = when (WorkspaceRepository.mode.value) {
                                is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspaceRepository.samplingChain
                                else -> WorkspaceRepository.lightsChain
                            },
                            slotIndex = 0,
                            dragAndDropState = dragAndDropState,
                            expanded = true,
                            onAddComponent = {
                                onEvent(WorkspaceContract.Event.AddChainDevice(it))
                            },
                            onDropDevice = { device, (originalIndex, _), originChain ->
                                DeviceInsertionAnimator.register(device.selectionUUID)
                                val insertionIndex = 0
                                val finalIndex = if (originChain === chain) {
                                    if (originalIndex < insertionIndex) insertionIndex - 1 else insertionIndex
                                } else insertionIndex
                                val safeIndex = finalIndex.coerceIn(0, chain!!.devices.value.size)
                                chain!!.add(device, safeIndex, fromUser = false)

                                UndoManager.addAction(
                                    UndoableAction.MovedChainDevice(
                                        chainBefore = originChain,
                                        chainAfter = chain!!,
                                        device = device,
                                        fromIndex = originalIndex,
                                        toIndex = chain!!.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
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

@Composable
fun ChainDeviceContextMenu(
    chain: Chain,
    device: GenericChainDevice<*>,
    visible: Boolean,
    offset: DpOffset,
    onDismiss: () -> Unit
) {
    val currentClipboard by ClipboardManager.clipboardData.collectAsState()

    ChainContextMenu(
        expanded = visible,
        onDismissRequest = onDismiss,
        offset = offset
    ) {
        ChainContextMenuItem(
            label = "Copy",
            icon = Lucide.Copy,
            onClick = {
                ClipboardManager.setClipboardData(
                    ClipboardData.ChainDevice(
                        states = listOf(device.state.value),
                        type = when (WorkspaceRepository.mode.value) {
                            is WorkspaceContract.WorkspaceMode.SamplingChain -> ClipboardData.ChainDevice.ChainType.Sampling
                            else -> ClipboardData.ChainDevice.ChainType.Lights
                        }
                    )
                )
                onDismiss()
            }
        )

        ChainContextMenuItem(
            label = "Duplicate",
            icon = Lucide.CopyPlus,
            onClick = {
                val index = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }

                chain.add(
                    device = StateChain.unpackDevice(StateChain.packDevice(device)),
                    atIndex = index + 1
                )
                onDismiss()
            }
        )

        if (device.helpRef != null) {
            ChainContextMenuItem(
                label = "Get Help",
                icon = Lucide.BookOpenText,
                onClick = {
                    WorkspaceRepository.switchMode(
                        GetHelpWorkspaceMode(helpRef = device.helpRef!!),
                        undoable = false
                    )
                    onDismiss()
                }
            )
        }

        if (currentClipboard is ClipboardData.ChainDevice) {
            ChainContextMenuItem(
                label = "Paste",
                icon = Lucide.ClipboardPaste,
                onClick = {
                    val index = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }

                    (currentClipboard as ClipboardData.ChainDevice).states.map {
                        StateChain.unpackDevice(it)
                    }.fastForEachReversed {
                        chain.add(
                            device = it,
                            atIndex = index + 1
                        )
                    }
                    onDismiss()
                }
            )

            ChainContextMenuItem(
                label = "Paste Replace",
                icon = Lucide.Replace,
                onClick = {
                    val index = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }

                    chain.remove(device.selectionUUID)

                    (currentClipboard as ClipboardData.ChainDevice).states.map {
                        StateChain.unpackDevice(it)
                    }.fastForEachReversed {
                        chain.add(
                            device = it,
                            atIndex = index
                        )
                    }
                    onDismiss()
                }
            )
        }

        ContextMenuSeparator()

        ChainContextMenuItem(
            label = "Delete",
            icon = Lucide.Trash2,
            variant = ContextMenuItemVariant.Destructive,
            onClick = {
                chain.remove(device.selectionUUID)
                onDismiss()
            }
        )
    }
}
