package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.network.presence.CollaborationPresence
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.LocalChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.mask.MaskChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.modifier.clickableWithDoubleTap
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlin.reflect.KClass

@Composable
fun ChainView(
    chain: Chain,
    modifier: Modifier = Modifier,
    dragAndDropState: DragAndDropState<GenericChainDevice<*>> = rememberDragAndDropState(),
    parentSelectionUUID: String? = null,
    showContextMenu: Boolean = true,
    showRemoteFocus: Boolean = true,
    dragAfterLongPress: Boolean = false,
    expandedEmptyPickerWidth: Dp = 100.dp,
    privateTimelineChain: Boolean = false,
    isDeviceTypeEnabled: (KClass<out GenericChainDevice<*>>) -> Boolean = { true },
    onAddDevice: ((GenericChainDevice<*>, Int) -> Unit)? = null,
    onMoveDevice: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    val windowInfo = LocalWindowInfo.current
    val devices by chain.devices
    val selections by SelectionManager.selections.collectAsState()
    val effectivePrivateTimelineChain = privateTimelineChain || !chain.collaborationSyncEnabled
    val remoteFocuses by CollaborationPresence.remoteFocuses.collectAsState()
    val remoteCursors by CollaborationPresence.remoteCursors.collectAsState()
    val draggedDevice = dragAndDropState.draggedItem?.data
    val draggingDeviceIds = draggedDevice
        ?.let { selectedChainDevicesForDrag(dragged = it, originChain = chain) }
        ?.mapTo(mutableSetOf()) { it.selectionUUID }
        ?: emptySet()
    fun addDevice(device: GenericChainDevice<*>, index: Int) {
        onAddDevice?.invoke(device, index) ?: chain.add(device, index)
    }

    Box(modifier = modifier) {
        key(devices) {
            if (devices.isEmpty()) {
                ExpandingChainDevicePicker(
                    destinationChain = chain,
                    slotIndex = 0,
                    dragAndDropState = dragAndDropState,
                    expanded = true,
                    expandedWidth = expandedEmptyPickerWidth,
                    allowExternalDrop = true,
                    privateDestination = effectivePrivateTimelineChain,
                    allowClipboardPaste = true,
                    samplingOverride = if (effectivePrivateTimelineChain) false else null,
                    isDeviceTypeEnabled = isDeviceTypeEnabled,
                    onAddComponent = { addDevice(it, 0) },
                    onDropDevice = { draggedDevices, originChain ->
                        moveChainDevices(
                            chain = chain,
                            parentSelectionUUID = parentSelectionUUID,
                            onAddDevice = onAddDevice,
                            onMoveDevice = onMoveDevice,
                            draggedDevices = draggedDevices,
                            originChain = originChain,
                            insertionIndex = 0,
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
                        expanded = false,
                        allowExternalDrop = true,
                        privateDestination = effectivePrivateTimelineChain,
                        allowClipboardPaste = true,
                        samplingOverride = if (effectivePrivateTimelineChain) false else null,
                        isDeviceTypeEnabled = isDeviceTypeEnabled,
                        onAddComponent = { addDevice(it, 0) },
                        onDropDevice = { draggedDevices, originChain ->
                            moveChainDevices(
                                chain = chain,
                                parentSelectionUUID = parentSelectionUUID,
                                onAddDevice = onAddDevice,
                                onMoveDevice = onMoveDevice,
                                draggedDevices = draggedDevices,
                                originChain = originChain,
                                insertionIndex = 0,
                            )
                        }
                    )

                    devices.forEachIndexed { index, device ->
                        val previewDevices = selectedChainDevicesForDrag(
                            dragged = device,
                            originChain = chain,
                            selections = selections,
                        )

                        DraggableItem(
                            state = dragAndDropState,
                            key = device.selectionUUID,
                            data = device,
                            useDragAnchor = true,
                            dragAfterLongPress = dragAfterLongPress,
                            isPartOfActiveDrag = device.selectionUUID in draggingDeviceIds,
                            animatePreviewOnStart = previewDevices.size == 1,
                            draggableContent = if (previewDevices.size > 1) {
                                {
                                    Row(
                                        modifier = Modifier.wrapContentWidth(unbounded = true),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        previewDevices.forEach { previewDevice ->
                                            key(previewDevice.selectionUUID) {
                                                ChainDeviceDragPreview(
                                                    device = previewDevice,
                                                    dragAndDropState = dragAndDropState,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        ) {
                            var showRightClickMenu by remember { mutableStateOf(false) }
                            var rightClickMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

                            TitleBarModifierProvider(
                                Modifier
                                    .clickableWithDoubleTap(
                                        onSingleClick = {
                                            val modifiers = windowInfo.keyboardModifiers
                                            val chainDeviceSelectable = Selectable.ChainDevice(
                                                parent = chain,
                                                device = device
                                            )
                                            when {
                                                modifiers.isShiftPressed -> {
                                                    SelectionManager.selectRangeInChain(
                                                        targetDevice = chainDeviceSelectable,
                                                        devicesInChain = devices
                                                    )
                                                }
                                                modifiers.isMetaPressed ||
                                                    modifiers.isCtrlPressed ||
                                                    modifiers.isAltPressed -> {
                                                    SelectionManager.select(chainDeviceSelectable, single = false)
                                                }
                                                else -> SelectionManager.select(chainDeviceSelectable)
                                            }
                                        },
                                        onDoubleClick = {
                                            device.setCollapsed(!device.isCollapsed)
                                        }
                                    )
                                    .then(
                                        if (showContextMenu) {
                                            Modifier.rightClickable {
                                                rightClickMenuOffset = DpOffset((it.x / density).dp, (it.y / density).dp)
                                                showRightClickMenu = true
                                            }
                                        } else Modifier
                                    )
                                    .dragAnchor()
                            ) {
                                LaunchedEffect(draggingDeviceIds) {
                                    showRightClickMenu = false
                                    device.isDragging.value = device.selectionUUID in draggingDeviceIds
                                }

                                if (showContextMenu) {
                                    ChainDeviceContextMenu(
                                        chain = chain,
                                        device = device,
                                        visible = showRightClickMenu,
                                        offset = rightClickMenuOffset,
                                        onDismiss = { showRightClickMenu = false }
                                    )
                                }

                                AnimatedInsertedDevice(id = device.selectionUUID) {
                                    val hasRemoteFocus = showRemoteFocus && remoteFocuses.values.any { it == device.selectionUUID }
                                    val remoteFocusColor = if (hasRemoteFocus) {
                                        remoteFocuses.entries
                                            .firstOrNull { it.value == device.selectionUUID }
                                            ?.key
                                            ?.let { userId -> remoteCursors[userId]?.user?.color }
                                            ?.let { Color(it) }
                                            ?: Color(0xFF7C3AED)
                                    } else Color.Unspecified

                                    val deviceState by device.state.collectAsState()
                                    val isCollapsed by device.isCollapsedState

                                    Box(
                                        modifier = Modifier
                                            .chainDeviceMuteEffect(isMuted = device.isMuted)
                                            .then(
                                                if (hasRemoteFocus) {
                                                    Modifier.border(2.dp, remoteFocusColor, DefaultShape)
                                                } else Modifier
                                            )
                                    ) {
                                        CompositionLocalProvider(LocalChainDevice provides device) {
                                            if (isCollapsed) {
                                                device.CollapsedContent()
                                            } else when (device) {
                                                is GroupChainDevice -> device.Content(dragAndDropState = dragAndDropState)
                                                is MultiGroupChainDevice -> device.Content(dragAndDropState = dragAndDropState)
                                                is ChokeChainDevice -> device.Content(dragAndDropState = dragAndDropState)
                                                is MaskChainDevice -> device.Content(dragAndDropState = dragAndDropState)

                                                else -> device.Content()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val insertionIndex = index + 1
                        ExpandingChainDevicePicker(
                            destinationChain = chain,
                            slotIndex = insertionIndex,
                            dragAndDropState = dragAndDropState,
                            expanded = index == devices.lastIndex,
                            allowExternalDrop = true,
                            privateDestination = effectivePrivateTimelineChain,
                            allowClipboardPaste = true,
                            samplingOverride = if (effectivePrivateTimelineChain) false else null,
                            isDeviceTypeEnabled = isDeviceTypeEnabled,
                            onAddComponent = { addDevice(it, insertionIndex) },
                            onDropDevice = { draggedDevices, originChain ->
                                moveChainDevices(
                                    chain = chain,
                                    parentSelectionUUID = parentSelectionUUID,
                                    onAddDevice = onAddDevice,
                                    onMoveDevice = onMoveDevice,
                                    draggedDevices = draggedDevices,
                                    originChain = originChain,
                                    insertionIndex = insertionIndex,
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
private fun ChainDeviceDragPreview(
    device: GenericChainDevice<*>,
    dragAndDropState: DragAndDropState<GenericChainDevice<*>>,
) {
    val deviceState by device.state.collectAsState()
    val isCollapsed by device.isCollapsedState

    Box(
        modifier = Modifier.chainDeviceMuteEffect(isMuted = device.isMuted)
    ) {
        CompositionLocalProvider(LocalChainDevice provides device) {
            if (isCollapsed) {
                device.CollapsedContent()
            } else {
                when (device) {
                    is GroupChainDevice -> device.Content(dragAndDropState = dragAndDropState)
                    is MultiGroupChainDevice -> device.Content(dragAndDropState = dragAndDropState)
                    is ChokeChainDevice -> device.Content(dragAndDropState = dragAndDropState)
                    is MaskChainDevice -> device.Content(dragAndDropState = dragAndDropState)
                    else -> device.Content()
                }
            }
        }
    }
}

internal fun moveChainDevices(
    chain: Chain,
    parentSelectionUUID: String?,
    onAddDevice: ((GenericChainDevice<*>, Int) -> Unit)?,
    onMoveDevice: ((Int, Int) -> Unit)?,
    draggedDevices: List<GenericChainDevice<*>>,
    originChain: Chain,
    insertionIndex: Int,
    copy: Boolean = ModifierKeysState.isAltPressed,
) {
    if (draggedDevices.isEmpty() || draggedDevices.any { it.selectionUUID == parentSelectionUUID }) {
        return
    }

    val sourceDevices = originChain.devices.value
    if (draggedDevices.any { it !in sourceDevices }) {
        return
    }
    if (copy) {
        val copies = draggedDevices.map { device ->
            StateChain.unpackDevice(
                deviceState = StateChain.packDevice(device = device),
            )
        }
        chain.addAll(devicesToAdd = copies, atIndex = insertionIndex)
        SelectionManager.replaceSelections(
            updatedSelections = copies.map { Selectable.ChainDevice(parent = chain, device = it) },
        )
        return
    }
    val draggedIds = draggedDevices.mapTo(mutableSetOf()) { it.selectionUUID }
    val baseIndex = if (originChain === chain) {
        insertionIndex - sourceDevices.take(insertionIndex).count { it.selectionUUID in draggedIds }
    } else {
        insertionIndex
    }

    if (originChain === chain) {
        val remaining = sourceDevices.filterNot { it.selectionUUID in draggedIds }
        val targetIndex = baseIndex.coerceIn(0, remaining.size)
        val reordered = remaining.toMutableList().apply { addAll(targetIndex, draggedDevices) }
        if (reordered == sourceDevices) {
            return
        }
    }

    val movements = mutableListOf<UndoableAction.MovedChainDevice>()
    draggedDevices.forEachIndexed { offset, device ->
        val fromIndex = originChain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }
        if (fromIndex < 0) {
            return@forEachIndexed
        }

        DeviceInsertionAnimator.register(device.selectionUUID)
        val remainingBeforeInsertion = if (originChain === chain) {
            draggedDevices.drop(offset + 1).count { remaining ->
                sourceDevices.indexOfFirst { it.selectionUUID == remaining.selectionUUID } < insertionIndex
            }
        } else {
            0
        }
        val targetIndex = (baseIndex + offset + remainingBeforeInsertion)
            .coerceIn(0, if (originChain === chain) chain.devices.value.lastIndex else chain.devices.value.size)

        if (originChain === chain && onMoveDevice != null) {
            onMoveDevice.invoke(fromIndex, targetIndex)
            return@forEachIndexed
        }

        originChain.remove(device.selectionUUID, fromUser = false)

        if (originChain !== chain && onAddDevice != null) {
            onAddDevice.invoke(device, targetIndex)
            return@forEachIndexed
        }

        chain.add(device, targetIndex, fromUser = false)
        movements += UndoableAction.MovedChainDevice(
            chainBefore = originChain,
            chainAfter = chain,
            device = device,
            fromIndex = fromIndex,
            toIndex = targetIndex,
        )
    }

    when (movements.size) {
        0 -> Unit
        1 -> UndoManager.addAction(movements.single())
        else -> UndoManager.addAction(UndoableAction.MultiMovedChainDevices(movements))
    }

    if (originChain !== chain) {
        SelectionManager.replaceSelections(
            SelectionManager.selections.value.map { selection ->
                if (selection is Selectable.ChainDevice && selection.parent === originChain && selection.selectionUUID in draggedIds) {
                    Selectable.ChainDevice(parent = chain, device = selection.device)
                } else {
                    selection
                }
            }
        )
    }
}
