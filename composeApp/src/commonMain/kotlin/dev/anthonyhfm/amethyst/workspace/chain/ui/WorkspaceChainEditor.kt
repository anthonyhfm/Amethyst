package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import io.androidpoet.dropdown.Dropdown
import io.androidpoet.dropdown.dropDownMenu

@Composable
fun WorkspaceChainEditor(
    devices: List<GenericChainDevice<*>>,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    val density = LocalDensity.current.density
    val dragAndDropState = rememberDragAndDropState<GenericChainDevice<*>>()
    val scrollState = rememberScrollState()
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

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .height(280.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surfaceColorAtElevation(24.dp)))
                .border(1.dp, MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(12.dp))
                .padding(vertical = 12.dp)
                .horizontalScroll(scrollState)
        ) {
            key(devices) {
                if (devices.isNotEmpty()) {
                    DragAndDropContainer(
                        state = dragAndDropState,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                            Row {
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
                                                    SelectionManager.select(
                                                        Selectable.ChainDevice(
                                                            parent = when (WorkspaceRepository.mode.value) {
                                                                is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspaceRepository.samplingChain
                                                                else -> WorkspaceRepository.lightsChain
                                                            },
                                                            device = device
                                                        )
                                                    )
                                                }
                                                .rightClickable {
                                                    rightClickMenuOffset = DpOffset((it.x / density).dp, (it.y / density).dp)

                                                    println(it)
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

        if (platform is Platform.Desktop) {
            WorkspaceChainScroller(
                scrollState = scrollState
            )
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

    Dropdown(
        isOpen = visible,
        menu = dropDownMenu {
            item("copy", "Copy") {
                icon(Icons.Default.ContentCopy)
            }

            item("duplicate", "Duplicate") {
                icon(Icons.Default.ControlPointDuplicate)
            }

            if (currentClipboard is ClipboardData.ChainDevice) {
                item("paste", "Paste") {
                    icon(Icons.Default.ContentPaste)
                }

                item("replace", "Paste Replace") {
                    icon(Icons.Default.FindReplace)
                }
            }

            horizontalDivider()

            item("delete", "Delete") {
                icon(Icons.Default.DeleteOutline)
            }
        },
        offset = offset,
        onItemSelected = {
            when (it) {
                "copy" -> {
                    ClipboardManager.setClipboardData(
                        ClipboardData.ChainDevice(
                            states = listOf(device.state.value),
                            type = when (WorkspaceRepository.mode.value) {
                                is WorkspaceContract.WorkspaceMode.SamplingChain -> ClipboardData.ChainDevice.ChainType.Sampling
                                else -> ClipboardData.ChainDevice.ChainType.Lights
                            }
                        )
                    )
                }

                "duplicate" -> {
                    val index = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }

                    chain.add(
                        device = StateChain.unpackDevice(StateChain.packDevice(device)),
                        atIndex = index + 1
                    )
                }

                "paste" -> {
                    val index = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }

                    (currentClipboard as ClipboardData.ChainDevice).states.map {
                        StateChain.unpackDevice(it)
                    }.fastForEachReversed {
                        chain.add(
                            device = it,
                            atIndex = index + 1
                        )
                    }
                }

                "replace" -> {
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
                }

                "delete" -> {
                    chain.remove(device.selectionUUID)
                }
            }

            onDismiss()
        },
        onDismiss = {
            onDismiss()
        }
    )
}