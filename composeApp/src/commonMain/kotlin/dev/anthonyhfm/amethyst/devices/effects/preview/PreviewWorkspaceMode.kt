package dev.anthonyhfm.amethyst.devices.effects.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Custom workspace mode activated by the PreviewChainDevice when multiple Launchpad
 * devices are present.  Pad clicks on the large viewport devices are captured via
 * [onPadInteraction] and routed through the PreviewChainDevice's own [signalExit],
 * so signals enter the chain at the pre-effect insertion point.
 *
 * Heaven.devices get their [onCapturePad] set when the mode opens and cleared on close.
 * Cleanup happens regardless of how the mode exits (X button, keyboard, or explicit [close]).
 */
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.ui.components.AddDeviceButton
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportConfig
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportPanBoundsPolicy
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport

class PreviewWorkspaceMode(
    private val onPadInteraction: (down: Boolean, x: Int, y: Int) -> Unit,
) : WorkspaceMode() {
    override val displayName: String = "Preview"
    override val selectableMode: Boolean = false
    override val claimMidiInputs: Boolean = false

    override fun onMidiInput(data: MidiInputData, offset: Offset) {

    }

    @androidx.compose.runtime.Composable
    override fun Content(modifier: Modifier) {
        val viewModel: WorkspaceViewModel = viewModel { WorkspaceViewModel() }

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            WorkspaceViewport(
                viewportKey = "workspace-preview",
                config = ViewportConfig(
                    minZoom = 0.5f,
                    maxZoom = 2f,
                    enablePanning = true,
                    enableZoom = true,
                    panBoundsPolicy = ViewportPanBoundsPolicy.ClampToContent(),
                    contentPadding = 80.dp
                ),
                onEvent = { viewModel.onEvent(it) }
            )

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
            ) {
                AddDeviceButton(
                    onClick = {
                        viewModel.onEvent(WorkspaceContract.Event.OpenVirtualDevicePicker)
                    }
                )
            }
        }
    }

    var modeClose: (() -> Unit)? = null

    /** Call after [WorkspaceRepository.switchMode] to wire up pad capture on all devices. */
    fun wake() {
        Heaven.devices.forEach { device ->
            device.onCapturePad = { (down, x, y) ->
                onPadInteraction(down, x, y)
            }
        }

        // Auto-release pad capture no matter how the mode exits (X button, keyboard, or close()).
        CoroutineScope(Dispatchers.Main).launch {
            WorkspaceRepository.mode.collect { currentMode ->
                if (currentMode !== this@PreviewWorkspaceMode) {
                    releasePadCapture()
                    cancel()
                }
            }
        }
    }

    /** Releases pad capture from all devices and fires [modeClose]. */
    fun close() {
        releasePadCapture()
        modeClose?.invoke()
    }

    private fun releasePadCapture() {
        Heaven.devices.forEach { device ->
            device.onCapturePad = null
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val isExitKey = event.key == Key.Escape ||
            ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.W)
        if (isExitKey) {
            close()
            return true
        }
        return false
    }
}
