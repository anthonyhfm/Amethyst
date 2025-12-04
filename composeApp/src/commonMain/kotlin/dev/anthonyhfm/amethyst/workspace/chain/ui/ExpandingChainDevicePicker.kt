package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.zIndex
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.drop.dropTarget
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import io.androidpoet.dropdown.Dropdown
import io.androidpoet.dropdown.dropDownMenu
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ExpandingChainDevicePicker(
    destinationChain: Chain,
    slotIndex: Int,
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
    val density = LocalDensity.current.density
    val interaction = remember { MutableInteractionSource() }
    val hovering: Boolean by interaction.collectIsHoveredAsState()
    var pickerVisible: Boolean by remember { mutableStateOf(false) }
    val dropKey = remember { UUID.randomUUID() }
    var isDropHover by remember { mutableStateOf(false) }
    val clipboard by ClipboardManager.clipboardData.collectAsState()

    var showRightClickMenu by remember { mutableStateOf(false) }
    var rightClickMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    val hasGlobalDrag = dragAndDropState.draggedItem != null

    val targetWidth by animateDpAsState(
        targetValue = when {
            forceOff -> collapsedWidth
            isDropHover -> expandedWidth                          // FULL size only on real drop hover
            hovering || pickerVisible || showRightClickMenu || expanded -> hoverWidth   // pointer / explicit hover
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

    val pulseAlpha = remember { Animatable(0f) }
    val pulseEvents = remember(destinationChain, slotIndex) {
        SignalIndicatorManager.events(destinationChain, slotIndex)
    }

    LaunchedEffect(pulseEvents) {
        pulseEvents.collectLatest {
            pulseAlpha.stop()
            pulseAlpha.snapTo(1f)
            pulseAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 600, easing = LinearEasing)
            )
        }
    }

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
            .hoverable(interaction)
            .rightClickable {
                val isDevice = clipboard is ClipboardData.ChainDevice
                val isTimelineAudio = clipboard is ClipboardData.TimelineAudioEntries && WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.SamplingChain
                val isMidiEntries = clipboard is ClipboardData.TimelineMidiEntries && WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.LightsChain

                if (isDevice || isTimelineAudio || isMidiEntries) {
                    showRightClickMenu = true
                }

                rightClickMenuOffset = DpOffset((it.x / density).dp, (it.y / density).dp)
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (clipboard is ClipboardData.ChainDevice || clipboard is ClipboardData.TimelineAudioEntries || clipboard is ClipboardData.TimelineMidiEntries) {
            Dropdown(
                isOpen = showRightClickMenu,
                offset = rightClickMenuOffset,
                menu = dropDownMenu {
                    if (clipboard is ClipboardData.ChainDevice) {
                        val count = (clipboard as ClipboardData.ChainDevice).states.size
                        item("paste", "Paste all ($count)") {
                            icon(Icons.Default.ContentPaste)
                        }
                    } else if (clipboard is ClipboardData.TimelineAudioEntries && WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.SamplingChain) {
                        item("paste_audio", "Paste Audio from Timeline") {
                            icon(Icons.Default.ContentPaste)
                        }
                    } else if (clipboard is ClipboardData.TimelineMidiEntries && WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.LightsChain) {
                        item("paste_midi", "Paste Midi from Timeline") {
                            icon(Icons.Default.ContentPaste)
                        }
                    }
                },
                onItemSelected = {
                    when (it) {
                        "paste" -> {
                            (clipboard as ClipboardData.ChainDevice).states.map {
                                StateChain.unpackDevice(it)
                            }.fastForEachReversed {
                                destinationChain.add(
                                    device = it,
                                    atIndex = slotIndex
                                )
                            }
                        }

                        "paste_audio" -> {
                            (clipboard as ClipboardData.TimelineAudioEntries).entries.fastForEachReversed {
                                destinationChain.add(
                                    device = StateChain.unpackDevice(
                                        device = ClipChainDeviceState(
                                            fileName = it.fileName,
                                            rawData = it.rawData,
                                            sampleRate = it.sampleRate,
                                            channels = it.channels,
                                            bitDepth = it.bitDepth,
                                            isLoaded = true,
                                        )
                                    )
                                )
                            }
                        }

                        "paste_midi" -> {
                            (clipboard as ClipboardData.TimelineMidiEntries).entries.fastForEachReversed { midiEntry ->
                                destinationChain.add(
                                    device = StateChain.unpackDevice(
                                        device = dev.anthonyhfm.amethyst.devices.effects.pianoroll.PianoRollChainDeviceState(
                                            midiEntry = midiEntry.copy()
                                        )
                                    ),
                                    atIndex = slotIndex
                                )
                            }
                        }
                    }

                    showRightClickMenu = false
                },
                onDismiss = {
                    showRightClickMenu = false
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (indicatorAlpha < 0.01f) {
                Box(
                    modifier = Modifier
                        .offset(y = 12.dp)
                        .clip(CircleShape)
                        .size(5.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer(alpha = pulseAlpha.value)
                        .background(MaterialTheme.colorScheme.secondary)
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
