package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.drop.dropTarget
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

@Composable
fun ExpandingChainDevicePicker(
    dragAndDropState: DragAndDropState<ChainDevice<*>> = rememberDragAndDropState(),
    expanded: Boolean = false,
    forceOff: Boolean = false,
    expandedWidth: Dp = 56.dp,
    onAddComponent: (ChainDevice<*>) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovering: Boolean by interaction.collectIsHoveredAsState()
    var pickerVisible: Boolean by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(
                if (forceOff) {
                    12.dp
                } else {
                    animateDpAsState(
                        targetValue = if (hovering || expanded || pickerVisible) {
                            expandedWidth
                        } else {
                            12.dp
                        }
                    ).value
                }
            )
            .hoverable(interaction)
            .dropTarget(
                state = dragAndDropState,
                key = remember { UUID.randomUUID() },
                onDrop = { state ->
                    onAddComponent(StateChain.unpackDevice(state.data.state.value))

                    if (WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.SamplingChain) {
                        println("Removing from sampling heaven chain: ${state.data.selectionUUID}")
                        WorkspaceRepository.samplingChain.heavenChain.remove(state.data.selectionUUID)
                    } else {
                        println("Removing from lights heaven chain: ${state.data.selectionUUID}")
                        WorkspaceRepository.lightsChain.heavenChain.remove(state.data.selectionUUID)
                    }
                }
            ),

        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Show the plus button only if not forceOff and if hovering, expanded, or pickerVisible is true
        AnimatedVisibility(
            visible = !forceOff && (hovering || expanded || pickerVisible),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box {
                IconButton(
                    onClick = {
                        pickerVisible = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add a new device"
                    )
                }

                ChainDevicePicker(
                    visible = pickerVisible,
                    sampling = WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.SamplingChain,
                    onDismiss = {
                        pickerVisible = false
                    },
                    onPickComponent = {
                        onAddComponent(it)
                    }
                )
            }
        }
    }
}