package dev.anthonyhfm.amethyst.workspace.modes.defaults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceViewModel
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.ui.components.AddDeviceButton
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportConfig
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportPanBoundsPolicy
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport

class LayoutWorkspaceMode(
    override val displayName: String = "Layout"
) : WorkspaceMode() {
    override val selectableMode: Boolean = true

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.Backspace, Key.Delete -> {
                    val devices = SelectionManager.selections.value.filterIsInstance<Selectable.VirtualViewportDevice>()

                    devices.forEach { device ->
                        WorkspaceRepository.removeVirtualDevice(device.selectionUUID)
                    }
                }

                Key.Escape -> {
                    SelectionManager.clear()

                    return true
                }
            }
        }

        return false
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) {

    }

    @Composable
    override fun Content(modifier: Modifier) {
        val viewModel: WorkspaceViewModel = viewModel { WorkspaceViewModel() }

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            WorkspaceViewport(
                viewportKey = "workspace-layout",
                config = ViewportConfig(
                    minZoom = 0.5f,
                    maxZoom = 2f,
                    enablePanning = true,
                    enableZoom = true,
                    draggableObjects = true,
                    panBoundsPolicy = ViewportPanBoundsPolicy.Unbounded,
                    showGrid = true,
                    showOrigin = true,
                    showActions = true,
                    showRemoteCursors = true,
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
}
