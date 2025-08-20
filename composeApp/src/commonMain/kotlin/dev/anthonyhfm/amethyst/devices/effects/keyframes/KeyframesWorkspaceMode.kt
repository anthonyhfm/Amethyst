package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
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

    var onVirtualDevicePress: ((x: Int, y: Int) -> Unit)? = null
    var onEvent: ((KeyframesChainDeviceContract.Event) -> Unit)? = null
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
            FrameListPanel(
                state = state,
                onEvent = {
                    onEvent?.invoke(it)
                },
                parent = parentDevice
            )

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
                    onEvent?.invoke(KeyframesChainDeviceContract.Event.OnSelectFrame(state.value.currentFrameIndex - 1))
                    return true
                }

                Key.DirectionDown, Key.DirectionRight -> {
                    onEvent?.invoke(KeyframesChainDeviceContract.Event.OnSelectFrame(state.value.currentFrameIndex + 1))
                    return true
                }

                Key.D -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        val selectedKeyframes = SelectionManager.selections.value.filterIsInstance<Selectable.KeyframeItem>()
                        if (selectedKeyframes.isNotEmpty()) {
                            val frameIndices = selectedKeyframes.map { it.frameIndex }
                            val highest = frameIndices.maxOrNull() ?: 0

                            // Use duplicateFrames which handles selection automatically
                            parentDevice?.duplicateFrames(frameIndices, highest + 1)

                            // Update current frame index to the last duplicated frame
                            parentDevice?.state?.update { currentState ->
                                currentState.copy(currentFrameIndex = highest + selectedKeyframes.size)
                            }
                        } else {
                            onEvent?.invoke(KeyframesChainDeviceContract.Event.OnDuplicateFrame())
                        }
                        return true
                    }
                }

                Key.Delete, Key.Backspace -> {
                    val selectedKeyframes = SelectionManager.selections.value.filterIsInstance<Selectable.KeyframeItem>()
                    if (selectedKeyframes.isNotEmpty()) {
                        val frameIndices = selectedKeyframes.map { it.frameIndex }

                        // Use the new removeFrames method for multi-deletion
                        parentDevice?.removeFrames(frameIndices)

                        // After deletion, select the frame at the position of the lowest deleted index
                        // or the last frame if we deleted beyond the end
                        val lowestDeletedIndex = frameIndices.minOrNull() ?: 0
                        val newFrameCount = (parentDevice?.state?.value?.frames?.size ?: 1)
                        val newSelectionIndex = minOf(lowestDeletedIndex, newFrameCount - 1)

                        SelectionManager.select(
                            Selectable.KeyframeItem(parent = parentDevice!!, frameIndex = newSelectionIndex),
                            single = true
                        )
                    } else {
                        onEvent?.invoke(KeyframesChainDeviceContract.Event.OnDeleteFrame(state.value.currentFrameIndex))
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

    fun virtualDevicePress(x: Int, y: Int) {
        onVirtualDevicePress?.invoke(x, y)
    }

    fun wake() {
        modeWakeup?.invoke()
    }

    fun close() {
        modeClose?.invoke()
    }
}