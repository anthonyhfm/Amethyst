package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
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
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun WorkspaceChainEditor(
    devices: List<GenericChainDevice<*>>,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
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
            .padding(12.dp),

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
            if (devices.isNotEmpty()) {
                DragAndDropContainer(
                    state = dragAndDropState,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Picker vor dem ersten Element: Einfügen an Index 0
                        ExpandingChainDevicePicker(
                            destinationChain = when (WorkspaceRepository.mode.value) {
                                is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspaceRepository.samplingChain
                                else -> WorkspaceRepository.lightsChain
                            },
                            dragAndDropState = dragAndDropState,
                            expanded = false,
                            onAddComponent = {
                                onEvent(WorkspaceContract.Event.AddChainDevice(it, 0))
                            },
                            onDropDevice = { device, (originalIndex, _), originChain ->
                                DeviceInsertionAnimator.register(device.selectionUUID)
                                val insertionIndex = 0
                                val finalIndex = if (originChain === chain) {
                                    // Wenn aus derselben Chain und ursprünglicher Index < Zielindex, dann verschiebt sich der Zielindex um -1
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
                                            .dragAnchor()
                                    ) {
                                        LaunchedEffect(dragAndDropState.draggedItem) {
                                            device.isDragging.value = device.selectionUUID == dragAndDropState.draggedItem?.key
                                        }

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

                                // Picker nach jedem Element: Einfügen an Index index+1
                                val insertionIndex = index + 1
                                ExpandingChainDevicePicker(
                                    destinationChain = when (WorkspaceRepository.mode.value) {
                                        is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspaceRepository.samplingChain
                                        else -> WorkspaceRepository.lightsChain
                                    },
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

        if (platform is Platform.Desktop) {
            WorkspaceChainScroller(
                scrollState = scrollState
            )
        }
    }
}