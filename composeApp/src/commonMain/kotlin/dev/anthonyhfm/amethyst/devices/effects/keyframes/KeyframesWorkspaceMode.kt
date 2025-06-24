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
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.views.FrameDrawingPanel
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.views.FrameListPanel
import dev.anthonyhfm.amethyst.ui.modifier.EditorEvent
import dev.anthonyhfm.amethyst.ui.modifier.editorEventListener
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import kotlinx.coroutines.flow.StateFlow

class KeyframesWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Keyframes"
    override val selectable: Boolean = false

    lateinit var state: StateFlow<KeyframesChainDeviceContract.KeyframesChainDeviceState>

    var onVirtualDevicePress: ((x: Int, y: Int, offset: Offset) -> Unit)? = null
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
                .editorEventListener {
                    when (it) {
                        is EditorEvent.Up -> {
                            onEvent?.invoke(
                                KeyframesChainDeviceContract.Event.OnSelectFrame(state.selectedFrameIndex - 1)
                            )
                        }

                        is EditorEvent.Down -> {
                            onEvent?.invoke(
                                KeyframesChainDeviceContract.Event.OnSelectFrame(state.selectedFrameIndex + 1)
                            )
                        }

                        is EditorEvent.Duplicate -> {

                        }

                        else -> { }
                    }
                }
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

    fun virtualDevicePress(x: Int, y: Int, offset: Offset) {
        onVirtualDevicePress?.invoke(x, y, offset)
    }

    fun wake() {
        modeWakeup?.invoke()
    }

    fun close() {
        modeClose?.invoke()
    }
}