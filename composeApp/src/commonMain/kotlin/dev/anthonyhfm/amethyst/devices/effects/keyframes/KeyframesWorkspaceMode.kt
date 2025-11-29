package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Event
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.InfinityCheckbox
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.KeyframesPinchControl
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.views.FrameDrawingPanel
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.views.FrameListPanel
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class KeyframesWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Keyframes"
    override val selectable: Boolean = false
    override val claimInputs: Boolean = true

    lateinit var state: StateFlow<KeyframesChainDeviceContract.KeyframesChainDeviceState>

    var onVirtualDeviceDragStart: ((x: Int, y: Int) -> Unit)? = null
    var onVirtualDeviceDrag: ((x: Int, y: Int) -> Unit)? = null
    var onVirtualDeviceDragEnd: (() -> Unit)? = null
    var onEvent: ((Event) -> Unit)? = null
    var modeWakeup: (() -> Unit)? = null
    var modeClose: (() -> Unit)? = null

    var parentDevice: KeyframesChainDevice? = null

    @Composable
    fun ModeContent(paddingValues: PaddingValues) {
        val state by state.collectAsState()

        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),

                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FrameListPanel(
                    state = state,
                    onEvent = {
                        onEvent?.invoke(it)
                    },
                    parent = parentDevice
                )

                KeyframesPinchControl(
                    pinch = state.pinch,
                    onPinchChange = { onEvent?.invoke(Event.OnChangePinch(it)) },
                    bilateral = state.bilateralPinch,
                    onToggleBilateral = { onEvent?.invoke(Event.OnTogglePinchBilateral) }
                )

                InfinityCheckbox(
                    checked = state.infinity,
                    onCheckedChange = {
                        onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeInfinity(it))
                    }
                )
            }

            FrameDrawingPanel(
                state = state,
                onEvent = {
                    onEvent?.invoke(it)
                }
            )
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.DirectionUp, Key.DirectionLeft -> {
                    onEvent?.invoke(Event.OnSelectFrame(state.value.currentFrameIndex - 1))
                    return true
                }

                Key.DirectionDown, Key.DirectionRight -> {
                    onEvent?.invoke(Event.OnSelectFrame(state.value.currentFrameIndex + 1))
                    return true
                }

                Key.A -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        SelectionManager.clear()

                        state.value.frames.indices.forEach { frameIndex ->
                            SelectionManager.select(
                                Selectable.KeyframeItem(parent = parentDevice!!, frameIndex = frameIndex),
                                single = false
                            )
                        }

                        return true
                    }
                }

                Key.D -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        val selectedKeyframes = SelectionManager.selections.value.filterIsInstance<Selectable.KeyframeItem>()
                        if (selectedKeyframes.isNotEmpty()) {
                            val frameIndices = selectedKeyframes.map { it.frameIndex }
                            val highest = frameIndices.maxOrNull() ?: 0

                            parentDevice?.duplicateFrames(frameIndices, highest + 1)

                            parentDevice?.state?.update { currentState ->
                                currentState.copy(currentFrameIndex = highest + selectedKeyframes.size)
                            }
                        } else {
                            onEvent?.invoke(Event.OnDuplicateFrame())
                        }
                        return true
                    }
                }

                Key.Delete, Key.Backspace -> {
                    val selectedKeyframes = SelectionManager.selections.value.filterIsInstance<Selectable.KeyframeItem>()
                    if (selectedKeyframes.isNotEmpty()) {
                        val frameIndices = selectedKeyframes.map { it.frameIndex }

                        if (frameIndices[0] == 0 && (parentDevice?.state?.value?.frames?.size ?: 0) - 1 == frameIndices.last()) {
                            val newFrame = Frame(timing = state.value.frames[state.value.currentFrameIndex].timing)
                            parentDevice?.addFrameInternal(frameIndices.last() + 1, newFrame)
                        }

                        parentDevice?.removeFrames(frameIndices)

                        val lowestDeletedIndex = frameIndices.minOrNull() ?: 0
                        val newFrameCount = (parentDevice?.state?.value?.frames?.size ?: 1)
                        val newSelectionIndex = minOf(lowestDeletedIndex, newFrameCount - 1)

                        SelectionManager.select(
                            Selectable.KeyframeItem(parent = parentDevice!!, frameIndex = newSelectionIndex),
                            single = true
                        )
                    } else {
                        onEvent?.invoke(Event.OnDeleteFrame(state.value.currentFrameIndex))
                    }
                    return true
                }

                Key.C -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        val selectedKeyframes = SelectionManager.selections.value.filterIsInstance<Selectable.KeyframeItem>()
                        if (selectedKeyframes.isNotEmpty()) {
                            ClipboardManager.copy(selectedKeyframes)
                        }
                        return true
                    }
                }

                Key.V -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        ClipboardManager.paste()
                        return true
                    }
                }
            }
        }

        return false
    }

    fun virtualDeviceDragStart(x: Int, y: Int) {
        onVirtualDeviceDragStart?.invoke(x, y)
    }

    fun virtualDeviceDrag(x: Int, y: Int) {
        onVirtualDeviceDrag?.invoke(x, y)
    }

    fun virtualDeviceDragEnd() {
        onVirtualDeviceDragEnd?.invoke()
    }

    fun wake() {
        modeWakeup?.invoke()
    }

    fun close() {
        modeClose?.invoke()
    }
}