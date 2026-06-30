package dev.anthonyhfm.amethyst.workspace.modes.defaults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.workspace.WorkspaceViewModel
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.ui.components.AutoPlayButtons
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportConfig
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportPanBoundsPolicy
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport

class PerformanceWorkspaceMode(
    override val displayName: String = "Performance"
) : WorkspaceMode() {
    override val selectableMode: Boolean = true

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return false
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) {

    }

    @Composable
    override fun Content(modifier: Modifier) {
        val viewModel: WorkspaceViewModel = viewModel { WorkspaceViewModel() }

        Box(
            modifier = modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxSize()
        ) {
            WorkspaceViewport(
                viewportKey = "workspace-performance",
                config = ViewportConfig(
                    minZoom = 0.5f,
                    maxZoom = 2f,
                    enablePanning = true,
                    enableZoom = true,
                    draggableObjects = false,
                    panBoundsPolicy = ViewportPanBoundsPolicy.ClampToContent(
                        allowedOutOfBoundsFraction = 0.5f,
                    ),
                    showGrid = false,
                    showOrigin = false,
                    showActions = false,
                    showRemoteCursors = true,
                    contentPadding = 80.dp
                ),
                onEvent = { viewModel.onEvent(it) }
            )

            AnimatedVisibility(
                visible = true,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            ) {
                AutoPlayButtons()
            }
        }
    }
}
