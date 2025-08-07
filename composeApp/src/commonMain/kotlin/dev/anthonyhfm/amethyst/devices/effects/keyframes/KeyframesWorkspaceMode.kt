package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.views.FrameDrawingPanel
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.views.FrameListPanel
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import kotlinx.coroutines.flow.StateFlow

class KeyframesWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Keyframes"
    override val selectable: Boolean = false
    override val claimInputs: Boolean = true

    lateinit var state: StateFlow<KeyframesChainDeviceContract.KeyframesChainDeviceState>

    var onVirtualDevicePress: ((x: Int, y: Int) -> Unit)? = null
    var onEvent: ((KeyframesChainDeviceContract.Event) -> Unit)? = null
    var modeWakeup: (() -> Unit)? = null
    var modeClose: (() -> Unit)? = null

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
                }
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
                    onEvent?.invoke(KeyframesChainDeviceContract.Event.OnSelectFrame(state.value.selectedFrameIndex - 1))
                    return true
                }

                Key.DirectionDown, Key.DirectionRight -> {
                    onEvent?.invoke(KeyframesChainDeviceContract.Event.OnSelectFrame(state.value.selectedFrameIndex + 1))
                    return true
                }

                Key.D -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        onEvent?.invoke(KeyframesChainDeviceContract.Event.OnDuplicateFrame())
                        return true
                    }
                }

                Key.Delete, Key.Backspace -> {
                    onEvent?.invoke(KeyframesChainDeviceContract.Event.OnDeleteFrame(state.value.selectedFrameIndex))
                    return true
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