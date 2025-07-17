package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

@Composable
fun WorkspaceChainEditor(
    devices: List<ChainDevice<*>>,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    val dragAndDropState = rememberDragAndDropState<ChainDevice<*>>()
    val scrollState = rememberScrollState()

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
                        HiddenDevicePickerButton(
                            dragAndDropState = dragAndDropState,
                            expanded = false,
                            onAddComponent = {
                                onEvent(WorkspaceContract.Event.AddChainDevice(it, 0))
                            }
                        )

                        Row {
                            devices.forEachIndexed { index, device ->
                                DraggableItem(
                                    state = dragAndDropState,
                                    key = device.selectionUUID,
                                    data = device,
                                ) {
                                    device.Content()
                                }

                                HiddenDevicePickerButton(
                                    dragAndDropState = dragAndDropState,
                                    expanded = index == devices.lastIndex,
                                    onAddComponent = {
                                        onEvent(WorkspaceContract.Event.AddChainDevice(it, index + 1))
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                HiddenDevicePickerButton(
                    dragAndDropState = dragAndDropState,
                    expanded = true,
                    onAddComponent = {
                        onEvent(WorkspaceContract.Event.AddChainDevice(it))
                    }
                )
            }
        }
    }
}
