package dev.anthonyhfm.amethyst.devices.effects.choke

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.StepTextDial
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurface
import dev.anthonyhfm.amethyst.ui.theme.chainSurfaceRaised
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.ui.AnimatedInsertedDevice
import dev.anthonyhfm.amethyst.workspace.chain.ui.DeviceInsertionAnimator
import dev.anthonyhfm.amethyst.workspace.chain.ui.ExpandingChainDevicePicker
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.chain.ui.TitleBarModifierProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

class ChokeChainDevice : GenericChainDevice<ChokeChainDeviceState>() {
    override val state = MutableStateFlow(ChokeChainDeviceState())

    init {
        state.value.chain.signalExit = {
            signalExit?.invoke(it)
        }
        
        // Register this choke device with its channel
        chokeDevicesByChannel.getOrPut(state.value.target) { mutableListOf() }.add(this)
    }

    companion object {
        // Map of choke channel to list of choke devices on that channel
        private val chokeDevicesByChannel = mutableMapOf<Int, MutableList<ChokeChainDevice>>()

        /**
         * Choke all devices on a specific channel except the triggering device
         */
        fun chokeChannel(channel: Int, triggeringDevice: ChokeChainDevice) {
            chokeDevicesByChannel[channel]?.forEach { chokeDevice ->
                if (chokeDevice != triggeringDevice) {
                    chokeDevice.performChoke()
                }
            }
        }

        /**
         * Unregister a choke device (e.g., when it's deleted)
         */
        fun unregisterDevice(device: ChokeChainDevice, channel: Int) {
            chokeDevicesByChannel[channel]?.remove(device)
        }
    }

    private fun performChoke() {
        // Choke all devices in this choke device's chain
        state.value.chain.devices.value.forEach { device ->
            if (device is Chokeable) {
                device.onChoke()
            }
        }
    }

    @Composable
    override fun Content() {
        Content(rememberDragAndDropState())
    }

    @Composable
    fun Content(
        dragAndDropState: DragAndDropState<GenericChainDevice<*>> = rememberDragAndDropState()
    ) {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        Row(
            modifier = Modifier
                .clip(DefaultShape)
                .fillMaxHeight()
                .background(Theme[chainColorTokens][chainSurface])
        ) {
            ChainDeviceShell(
                title = "Choke",
                isSelected = isSelected,
                modifier = Modifier
                    .width(100.dp),
                titleBarModifier = LocalTitleBarModifier.current
            ) {
                StepTextDial(
                    headline = "Target",
                    value = deviceState.target,
                    steps = IntArray(16) { it + 1 }.toList(),
                    text = "${deviceState.target}",
                    onResolveTextValue = {
                        val chokeChannel = it.trim().toIntOrNull()

                        chokeChannel?.let { channel ->
                            if (chokeChannel in 0..16) {
                                val oldChannel = state.value.target
                                state.update {
                                    it.copy(target = channel)
                                }
                                // Update channel registration
                                if (oldChannel != channel) {
                                    unregisterDevice(this@ChokeChainDevice, oldChannel)
                                    chokeDevicesByChannel.getOrPut(channel) { mutableListOf() }.add(this@ChokeChainDevice)
                                }
                            }
                        }
                    },
                    onValueChange = { value ->
                        val oldChannel = state.value.target
                        state.update {
                            it.copy(target = value)
                        }
                        // Update channel registration
                        if (oldChannel != value) {
                            unregisterDevice(this@ChokeChainDevice, oldChannel)
                            chokeDevicesByChannel.getOrPut(value) { mutableListOf() }.add(this@ChokeChainDevice)
                        }
                    }
                )
            }

            key( // Trigger recomposition on selected group change
                state.collectAsState().value
            ) {
                GroupContent(dragAndDropState)
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(28.dp)
                    .clip(DefaultShape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(Theme[colors][selectionSurface], DefaultShape)
                                .border(1.dp, Theme[colors][selectionSurface], DefaultShape)
                        } else {
                            Modifier
                                .background(Theme[chainColorTokens][chainSurfaceRaised], DefaultShape)
                                .border(1.dp, Theme[chainColorTokens][chainBorder], DefaultShape)
                        }
                    )
            )
        }
    }

    @Composable
    private fun GroupContent(dragAndDropState: DragAndDropState<GenericChainDevice<*>>) {
        val state by state.collectAsState()
        val devices by state.chain.devices

        key(devices) {
            if (devices.isEmpty()) {
                ExpandingChainDevicePicker(
                    destinationChain = state.chain,
                    slotIndex = 0,
                    dragAndDropState = dragAndDropState,
                    expanded = true,
                    expandedWidth = 100.dp,
                    onAddComponent = {
                        state.chain.add(it)
                    },
                    onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                        if (originalUUID == selectionUUID) return@ExpandingChainDevicePicker

                        val targetChain = state.chain
                        val insertionIndex = 0
                        val finalIndex = if (originChain === targetChain) {
                            if (originalIndex < insertionIndex) insertionIndex - 1 else insertionIndex
                        } else insertionIndex
                        val safeIndex = finalIndex.coerceIn(0, targetChain.devices.value.size)

                        targetChain.add(
                            device,
                            safeIndex,
                            fromUser = false
                        )

                        UndoManager.addAction(
                            UndoableAction.MovedChainDevice(
                                chainBefore = originChain,
                                chainAfter = targetChain,
                                device = device,
                                fromIndex = originalIndex,
                                toIndex = targetChain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
                            )
                        )
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpandingChainDevicePicker(
                        destinationChain = state.chain,
                        slotIndex = 0,
                        dragAndDropState = dragAndDropState,
                        onAddComponent = {
                            state.chain.add(it, 0)
                        },
                        onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                            if (originalUUID == selectionUUID) return@ExpandingChainDevicePicker
                            DeviceInsertionAnimator.register(device.selectionUUID)
                            val targetChain = state.chain
                            val insertionIndex = 0
                            val finalIndex = if (originChain === targetChain) {
                                if (originalIndex < insertionIndex) insertionIndex - 1 else insertionIndex
                            } else insertionIndex
                            val safeIndex = finalIndex.coerceIn(0, targetChain.devices.value.size)

                            targetChain.add(
                                device,
                                safeIndex,
                                fromUser = false
                            )

                            UndoManager.addAction(
                                UndoableAction.MovedChainDevice(
                                    chainBefore = originChain,
                                    chainAfter = targetChain,
                                    device = device,
                                    fromIndex = originalIndex,
                                    toIndex = targetChain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
                                )
                            )
                        }
                    )

                    devices.forEachIndexed { index, device ->
                        DraggableItem(
                            state = dragAndDropState,
                            key = device.selectionUUID,
                            data = device,
                            useDragAnchor = true, // Enable drag anchor mode
                        ) {
                            TitleBarModifierProvider(
                                Modifier
                                    .clickable {
                                        val chainDeviceSelectable = Selectable.ChainDevice(
                                            parent = state.chain,
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
                                                SelectionManager.select(
                                                    chainDeviceSelectable,
                                                    single = false
                                                )
                                            }
                                            else -> {
                                                SelectionManager.select(chainDeviceSelectable)
                                            }
                                        }
                                    }
                                    .dragAnchor() // Add drag anchor to title bar
                            ) {
                                LaunchedEffect(dragAndDropState.draggedItem) {
                                    device.isDragging.value = device.selectionUUID == dragAndDropState.draggedItem?.key
                                }

                                AnimatedInsertedDevice(id = device.selectionUUID) {
                                    when (device) {
                                        is GroupChainDevice -> device.Content(
                                            dragAndDropState = dragAndDropState
                                        )

                                        is MultiGroupChainDevice -> device.Content(
                                            dragAndDropState = dragAndDropState
                                        )

                                        is ChokeChainDevice -> device.Content(
                                            dragAndDropState = dragAndDropState
                                        )

                                        else -> device.Content()
                                    }
                                }
                            }
                        }

                        ExpandingChainDevicePicker(
                            destinationChain = state.chain,
                            slotIndex = index + 1,
                            dragAndDropState = dragAndDropState,
                            expanded = index == devices.lastIndex,
                            onAddComponent = {
                                state.chain.add(it, index + 1)
                            },
                            onDropDevice = { device, (originalIndex, originalUUID), originChain ->
                                if (originalUUID == selectionUUID) return@ExpandingChainDevicePicker
                                DeviceInsertionAnimator.register(device.selectionUUID)
                                val targetChain = state.chain
                                val insertionIndex = index + 1
                                val finalIndex = if (originChain === targetChain) {
                                    if (originalIndex < insertionIndex) insertionIndex - 1 else insertionIndex
                                } else insertionIndex
                                val safeIndex = finalIndex.coerceIn(0, targetChain.devices.value.size)

                                targetChain.add(
                                    device,
                                    safeIndex,
                                    fromUser = false
                                )

                                UndoManager.addAction(
                                    UndoableAction.MovedChainDevice(
                                        chainBefore = originChain,
                                        chainAfter = targetChain,
                                        device = device,
                                        fromIndex = originalIndex,
                                        toIndex = targetChain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID },
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        // Trigger choking on all other choke devices with the same channel
        chokeChannel(state.value.target, this)
        
        // Pass signals through to the chain
        state.value.chain.signalEnter(n)
    }
}

@Serializable
data class ChokeChainDeviceState(
    val target: Int = 0,
    @Transient
    val chain: Chain = Chain(),
    var stateChain: StateChain = StateChain()
) : DeviceState()
