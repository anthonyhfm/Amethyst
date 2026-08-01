package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalDensity
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
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.modifier.clickableWithDoubleTap
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
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
    val devices by chain.devices
    val effectivePrivateTimelineChain = privateTimelineChain || !chain.collaborationSyncEnabled
    val remoteFocuses by CollaborationPresence.remoteFocuses.collectAsState()
    val remoteCursors by CollaborationPresence.remoteCursors.collectAsState()
    fun addDevice(device: GenericChainDevice<*>, index: Int) {
        onAddDevice?.invoke(device, index) ?: chain.add(device, index)
    }

    fun handleDrop(
        device: GenericChainDevice<*>,
        originalIndex: Int,
        originalUUID: String,
        originChain: Chain,
        insertionIndex: Int,
    ) {
        if (parentSelectionUUID != null && originalUUID == parentSelectionUUID) {
            return
        }

        DeviceInsertionAnimator.register(device.selectionUUID)
        val finalIndex = if (originChain === chain) {
            if (originalIndex < insertionIndex) insertionIndex - 1 else insertionIndex
        } else insertionIndex
        val safeIndex = finalIndex.coerceIn(0, chain.devices.value.size)

        if (originChain === chain && onMoveDevice != null) {
            onMoveDevice.invoke(originalIndex, safeIndex)
            return
        }

        if (originChain !== chain && onAddDevice != null) {
            onAddDevice.invoke(device, safeIndex)
            return
        }

        chain.add(device, safeIndex, fromUser = false)

        UndoManager.addAction(
            UndoableAction.MovedChainDevice(
                chainBefore = originChain,
                chainAfter = chain,
                device = device,
                fromIndex = originalIndex,
                toIndex = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
            )
        )
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
                    onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                        handleDrop(device, originalIndex, originalUUID, originChain, 0)
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
                        onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                            handleDrop(device, originalIndex, originalUUID, originChain, 0)
                        }
                    )

                    devices.forEachIndexed { index, device ->
                        DraggableItem(
                            state = dragAndDropState,
                            key = device.selectionUUID,
                            data = device,
                            useDragAnchor = true,
                            dragAfterLongPress = dragAfterLongPress,
                        ) {
                            var showRightClickMenu by remember { mutableStateOf(false) }
                            var rightClickMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

                            TitleBarModifierProvider(
                                Modifier
                                    .clickableWithDoubleTap(
                                        onSingleClick = {
                                            val chainDeviceSelectable = Selectable.ChainDevice(
                                                parent = chain,
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
                                LaunchedEffect(dragAndDropState.draggedItem) {
                                    showRightClickMenu = false
                                    device.isDragging.value = device.selectionUUID == dragAndDropState.draggedItem?.key
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
                                            .chainDeviceMuteEffect(deviceState.isMuted)
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
                            onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                                handleDrop(device, originalIndex, originalUUID, originChain, insertionIndex)
                            }
                        )
                    }
                }
            }
        }
    }
}
