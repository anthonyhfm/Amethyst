package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.drop.dropTarget
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun ExpandingChainDevicePicker(
    destinationChain: Chain,
    dragAndDropState: DragAndDropState<GenericChainDevice<*>> = rememberDragAndDropState(),
    expanded: Boolean = false,
    forceOff: Boolean = false,
    expandedWidth: Dp = 56.dp, // width when actual dragged item hovers (drop target focus)
    collapsedWidth: Dp = 12.dp, // default gap
    hoverWidth: Dp = 56.dp,     // width on normal pointer hover / explicit expand
    dragPresenceWidth: Dp = 18.dp, // width for all slots while a drag is happening elsewhere
    indicatorWidth: Dp = 3.dp,
    onAddComponent: (GenericChainDevice<*>) -> Unit,
    onDropDevice: (device: GenericChainDevice<*>, Pair<Int, String>, originChain: Chain) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovering: Boolean by interaction.collectIsHoveredAsState()
    var pickerVisible: Boolean by remember { mutableStateOf(false) }
    val dropKey = remember { UUID.randomUUID() }
    var isDropHover by remember { mutableStateOf(false) }

    val hasGlobalDrag = dragAndDropState.draggedItem != null

    val targetWidth by animateDpAsState(
        targetValue = when {
            forceOff -> collapsedWidth
            isDropHover -> expandedWidth                          // FULL size only on real drop hover
            hovering || pickerVisible || expanded -> hoverWidth   // pointer / explicit hover
            hasGlobalDrag -> dragPresenceWidth                    // global drag but not over this zone
            else -> collapsedWidth
        }, label = "ExpandingPickerWidth"
    )

    val showButton = !forceOff && !isDropHover && (hovering || pickerVisible || expanded)

    val indicatorAlpha by animateFloatAsState(
        targetValue = if (isDropHover) 1f else 0f,
        animationSpec = tween(120), label = "IndicatorAlpha"
    )
    val buttonAlpha by animateFloatAsState(
        targetValue = if (showButton) 1f else 0f,
        animationSpec = tween(150), label = "ButtonAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(targetWidth)
            .dropTarget(
                state = dragAndDropState,
                key = dropKey,
                onDragEnter = { state ->
                    val dragged = state.data
                    if (!isDroppingIntoSelf(dragged, destinationChain)) {
                        isDropHover = true
                    }
                },
                onDragExit = {
                    isDropHover = false
                },
                onDrop = { state ->
                    val dragged = state.data
                    if (isDroppingIntoSelf(dragged, destinationChain)) {
                        isDropHover = false
                        return@dropTarget
                    }

                    val device = dragged // reuse original instance to keep state

                    if (WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.SamplingChain) {
                        val oc = WorkspaceRepository.samplingChain.findDeviceChain(dragged.selectionUUID) ?: run {
                            isDropHover = false
                            return@dropTarget
                        }
                        val originalIndex = oc.devices.value.indexOfFirst { it.selectionUUID == dragged.selectionUUID }
                        if (originalIndex == -1) {
                            isDropHover = false
                            return@dropTarget
                        }
                        WorkspaceRepository.samplingChain.remove(dragged.selectionUUID, false)
                        onDropDevice(device, Pair(originalIndex, dragged.selectionUUID), oc)
                    } else {
                        val oc = WorkspaceRepository.lightsChain.findDeviceChain(dragged.selectionUUID) ?: run {
                            isDropHover = false
                            return@dropTarget
                        }
                        val originalIndex = oc.devices.value.indexOfFirst { it.selectionUUID == dragged.selectionUUID }
                        if (originalIndex == -1) {
                            isDropHover = false
                            return@dropTarget
                        }
                        WorkspaceRepository.lightsChain.remove(dragged.selectionUUID, false)
                        onDropDevice(device, Pair(originalIndex, dragged.selectionUUID), oc)
                    }

                    isDropHover = false
                }
            )
            .hoverable(interaction),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if(indicatorAlpha < 0.01f) {
                Box(
                    modifier = Modifier
                        .offset(y = 8.dp)
                        .align(Alignment.TopCenter)
                        .clip(CircleShape)
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.surfaceTint)
                )
            }

            if (indicatorAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .fillMaxHeight()
                        .graphicsLayer(alpha = indicatorAlpha)
                        .background(MaterialTheme.colorScheme.primary)
                        .zIndex(2f)
                )
            }

            if (buttonAlpha > 0.01f && indicatorAlpha < 0.01f) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .graphicsLayer(alpha = buttonAlpha)
                        .zIndex(3f)
                ) {
                    IconButton(onClick = { pickerVisible = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add a new device"
                        )
                    }

                    ChainDevicePicker(
                        visible = pickerVisible,
                        sampling = WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.SamplingChain,
                        onDismiss = { pickerVisible = false },
                        onPickComponent = { onAddComponent(it) }
                    )
                }
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
