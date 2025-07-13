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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableRow
import sh.calvin.reorderable.ReorderableScope

@Composable
fun WorkspaceChainEditor(
    sampling: Boolean,
    devices: List<ChainDevice<*>>,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    val scrollState = rememberScrollState()
    var isDraggingAny by remember { mutableStateOf(false) }

    LaunchedEffect(isDraggingAny) {
        if (!isDraggingAny && devices.isNotEmpty()) {
            delay(300)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // First HiddenDevicePickerButton at the beginning of the chain
                    HiddenDevicePickerButton(
                        expanded = false && !isDraggingAny, // Collapse during dragging
                        onAddComponent = {
                            onEvent(WorkspaceContract.Event.AddChainDevice(it, 0))
                        }
                    )

                    ReorderableRow(
                        list = devices,
                        onSettle = { fromIndex, toIndex ->
                            isDraggingAny = false

                            onEvent(
                                WorkspaceContract.Event.ReorderChainDevice(
                                    fromIndex = fromIndex,
                                    toIndex = toIndex
                                )
                            )
                        },
                        onMove = {
                            isDraggingAny = true
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) { index, device, isDragging ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // The actual device
                            ChainDeviceItem(
                                device = device,
                                isDragging = isDragging
                            )

                            // Use the new forceOff parameter instead of animation
                            HiddenDevicePickerButton(
                                expanded = index == devices.lastIndex,
                                forceOff = isDraggingAny, // Hide immediately during dragging
                                onAddComponent = {
                                    onEvent(WorkspaceContract.Event.AddChainDevice(it, index + 1))
                                }
                            )
                        }
                    }
                }
            } else {
                // If no devices exist, show only the expanded picker
                HiddenDevicePickerButton(
                    expanded = true,
                    onAddComponent = {
                        onEvent(WorkspaceContract.Event.AddChainDevice(it))
                    }
                )
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