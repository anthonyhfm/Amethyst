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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Event
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.InfinityCheckbox
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.KeyframesPinchControl
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.PlaybackModePicker
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.RepeatsControl
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

    var onVirtualDeviceDragStart: ((device: LaunchpadViewportElement, localX: Int, localY: Int) -> Unit)? = null
    var onVirtualDeviceDrag: ((device: LaunchpadViewportElement, localX: Int, localY: Int) -> Unit)? = null
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

                verticalArrangement = Arrangement.spacedBy(6.dp)
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

                PlaybackModePicker(
                    selectedMode = state.playbackMode,
                    onModeSelected = { onEvent?.invoke(Event.OnChangePlaybackMode(it)) }
                )

                RepeatsControl(
                    repeats = state.repeats,
                    onRepeatsChange = { onEvent?.invoke(Event.OnChangeRepeats(it)) }
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

    fun selectAllFrames(): Boolean {
        val device = parentDevice ?: return false
        if (state.value.frames.isEmpty()) return false

        SelectionManager.clear()
        state.value.frames.indices.forEach { frameIndex ->
            SelectionManager.select(
                Selectable.KeyframeItem(parent = device, frameIndex = frameIndex),
                single = false
            )
        }

        return true
    }

    fun copySelection(): Boolean {
        val selectedKeyframes = SelectionManager.selections.value.filterIsInstance<Selectable.KeyframeItem>()
        if (selectedKeyframes.isEmpty()) return false

        ClipboardManager.copy(selectedKeyframes)
        return true
    }

    fun pasteSelection(
        clipboard: ClipboardData? = ClipboardManager.clipboardData.value
    ): Boolean {
        if (clipboard !is ClipboardData.Keyframe) return false

        ClipboardManager.paste()
        return true
    }

    fun deleteSelection(): Boolean {
        if (state.value.frames.isEmpty()) return false

        val selectedKeyframes = SelectionManager.selections.value.filterIsInstance<Selectable.KeyframeItem>()
        if (selectedKeyframes.isNotEmpty()) {
            val device = parentDevice ?: return false
            val frameIndices = selectedKeyframes.map { it.frameIndex }

            if (frameIndices[0] == 0 && state.value.frames.lastIndex == frameIndices.last()) {
                val newFrame = Frame(timing = state.value.frames[state.value.currentFrameIndex].timing)
                parentDevice?.addFrameInternal(frameIndices.last() + 1, newFrame)
            }

            parentDevice?.removeFrames(frameIndices)

            val lowestDeletedIndex = frameIndices.minOrNull() ?: 0
            val newFrameCount = parentDevice?.state?.value?.frames?.size ?: 1
            val newSelectionIndex = minOf(lowestDeletedIndex, newFrameCount - 1)

            SelectionManager.select(
                Selectable.KeyframeItem(parent = device, frameIndex = newSelectionIndex),
                single = true
            )

            return true
        }

        onEvent?.invoke(Event.OnDeleteFrame(state.value.currentFrameIndex))
        return true
    }

    fun cutSelection(): Boolean {
        if (!copySelection()) return false
        return deleteSelection()
    }

    fun duplicateSelection(): Boolean {
        if (state.value.frames.isEmpty()) return false

        val selectedKeyframes = SelectionManager.selections.value.filterIsInstance<Selectable.KeyframeItem>()
        if (selectedKeyframes.isNotEmpty()) {
            val frameIndices = selectedKeyframes.map { it.frameIndex }
            val highest = frameIndices.maxOrNull() ?: 0

            parentDevice?.duplicateFrames(frameIndices, highest + 1)
            parentDevice?.state?.update { currentState ->
                currentState.copy(currentFrameIndex = highest + selectedKeyframes.size)
            }

            return true
        }

        onEvent?.invoke(Event.OnDuplicateFrame())
        return true
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

                Key.MoveHome -> {
                    if (state.value.frames.isNotEmpty()) {
                        onEvent?.invoke(Event.OnSelectFrame(0))
                        return true
                    }
                }

                Key.MoveEnd -> {
                    if (state.value.frames.isNotEmpty()) {
                        onEvent?.invoke(Event.OnSelectFrame(state.value.frames.lastIndex))
                        return true
                    }
                }

                Key.A -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        return selectAllFrames()
                    }
                }

                Key.D -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        return duplicateSelection()
                    }
                }

                Key.Delete, Key.Backspace -> {
                    return deleteSelection()
                }

                Key.C -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        return copySelection()
                    }
                }

                Key.X -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        return cutSelection()
                    }
                }

                Key.V -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        return pasteSelection()
                    }
                }

                Key.Escape -> {
                    modeClose?.invoke()
                    return true
                }

                Key.W -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        modeClose?.invoke()
                        return true
                    }
                }
            }
        }

        return false
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) = {
        val x: Int = data.pitch % 10
        val y: Int = 9 - (data.pitch / 10)

        if (data.velocity != 0) {
            onEvent?.invoke(Event.OnPaintButton(x + offset.x.toInt(), y + offset.y.toInt()))
        }
    }

    fun virtualDeviceDragStart(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        onVirtualDeviceDragStart?.invoke(device, localX, localY)
    }

    fun virtualDeviceDrag(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        onVirtualDeviceDrag?.invoke(device, localX, localY)
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
