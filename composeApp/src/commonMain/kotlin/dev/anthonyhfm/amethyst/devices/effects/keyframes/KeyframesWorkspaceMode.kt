package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.FrameControl
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.FrameTools
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeyframesWorkspaceMode(
    keyframesChainDevice: KeyframesChainDevice
) : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Keyframes Editor"
    override val selectable: Boolean = false

    private val deviceState: StateFlow<KeyframesChainDeviceState> = keyframesChainDevice.state.asStateFlow()

    var onVirtualDevicePress: ((x: Int, y: Int, offset: Offset) -> Unit)? = null
    var modeWakeup: (() -> Unit)? = null
    var modeClose: (() -> Unit)? = null

    fun virtualDevicePress(x: Int, y: Int, offset: Offset) {
        onVirtualDevicePress?.invoke(x, y, offset)
    }

    fun wake() {
        modeWakeup?.invoke()
    }

    fun close() {
        modeClose?.invoke()
    }

    @Composable
    fun EditorUI(paddingValues: PaddingValues) {
        val viewModel = viewModel { KeyframesWorkspaceModeViewModel() }
        val state by viewModel.state.collectAsState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            FrameControl(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            )

            FrameTools(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopEnd)
            )
        }
    }
}