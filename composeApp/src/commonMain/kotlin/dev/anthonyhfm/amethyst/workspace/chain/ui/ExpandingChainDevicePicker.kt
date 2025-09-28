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
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain.Companion.pack

@Composable
fun ExpandingChainDevicePicker(
    // Identify the destination chain of this drop zone to prevent self-drops
    destinationChain: Chain,
    dragAndDropState: DragAndDropState<GenericChainDevice<*>> = rememberDragAndDropState(),
    expanded: Boolean = false,
    forceOff: Boolean = false,
    expandedWidth: Dp = 56.dp,
    onAddComponent: (GenericChainDevice<*>) -> Unit,
    onDropDevice: (device: GenericChainDevice<*>, Pair<Int, String>, originChain: Chain) -> Unit
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
                    val dragged = state.data

                    // Prevent dropping a group-like device into its own chain or any of its subchains
                    if (isDroppingIntoSelf(dragged, destinationChain)) {
                        return@dropTarget
                    }

                    val device = StateChain.unpackDevice(
                        when (dragged.state.value) {
                            is GroupChainDeviceState -> (dragged as GroupChainDevice).packState()
                            is MultiGroupChainDeviceState -> (dragged as MultiGroupChainDevice).packState()
                            is ChokeChainDeviceState -> (dragged as ChokeChainDevice).state.value.copy(
                                stateChain = pack((dragged as ChokeChainDevice).state.value.chain)
                            )
                            else -> dragged.state.value
                        }
                    )

                    println("State: ${'$'}{dragged.state.value}")

                    val originChain: Chain = if (WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.SamplingChain) {
                        val oc = WorkspaceRepository.samplingChain.findDeviceChain(dragged.selectionUUID) ?: return@dropTarget
                        WorkspaceRepository.samplingChain.remove(dragged.selectionUUID, false)
                        oc
                    } else {
                        val oc = WorkspaceRepository.lightsChain.findDeviceChain(dragged.selectionUUID) ?: return@dropTarget
                        WorkspaceRepository.lightsChain.remove(dragged.selectionUUID, false)
                        oc
                    }

                    onDropDevice(
                        device,
                        Pair(
                            originChain.devices.value.indexOfFirst { it.selectionUUID == dragged.selectionUUID },
                            dragged.selectionUUID
                        ),
                        originChain
                    )
                }
            ),

        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

private fun isDroppingIntoSelf(
    dragged: GenericChainDevice<*>,
    destinationChain: Chain
): Boolean {
    return when (dragged) {
        is GroupChainDevice -> dragged.state.value.groups.any { it.chain.containsChain(destinationChain) }
        is MultiGroupChainDevice -> dragged.state.value.groups.any { it.chain.containsChain(destinationChain) }
        is ChokeChainDevice -> dragged.state.value.chain.containsChain(destinationChain)
        else -> false
    }
}

private fun Chain.containsChain(target: Chain): Boolean {
    if (this === target) return true
    for (device in this.devices.value) {
        when (device) {
            is GroupChainDevice -> if (device.state.value.groups.any { it.chain.containsChain(target) }) return true
            is MultiGroupChainDevice -> if (device.state.value.groups.any { it.chain.containsChain(target) }) return true
            is ChokeChainDevice -> if (device.state.value.chain.containsChain(target)) return true
        }
    }
    return false
}
