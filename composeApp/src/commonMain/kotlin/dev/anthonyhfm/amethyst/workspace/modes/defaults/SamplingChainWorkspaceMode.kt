package dev.anthonyhfm.amethyst.workspace.modes.defaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composeunstyled.Text
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.isMobilePhone
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceViewModel
import dev.anthonyhfm.amethyst.workspace.chain.ui.MobileWorkspaceChainEditor
import dev.anthonyhfm.amethyst.workspace.chain.ui.WorkspaceChainEditor
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberScrollAreaState
import dev.anthonyhfm.amethyst.workspace.chain.ui.MacroControls
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportConfig
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportPanBoundsPolicy
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport

class SamplingChainWorkspaceMode(
    override val displayName: String = "Sampling"
) : WorkspaceMode() {
    override val selectableMode: Boolean = true

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return ChainModeKeyHandler.handleKeyInput(event)
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) {

    }

    @Composable
    override fun Content(modifier: Modifier) {
        val viewModel: WorkspaceViewModel = viewModel { WorkspaceViewModel() }
        val scrollState = rememberScrollAreaState()
        var macrosVisible by remember { mutableStateOf(false) }

        if (isMobilePhone()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),

                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Currently not available on mobile",
                    modifier = Modifier
                        .padding(horizontal = 16.dp),

                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    WorkspaceViewport(
                        viewportKey = "workspace-sampling",
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
                            contentPadding = 24.dp
                        ),
                        onEvent = { viewModel.onEvent(it) }
                    )

                    if (macrosVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    macrosVisible = false
                                }
                        )
                    }

                    MacroControls(
                        macrosVisible = macrosVisible,
                        onMacrosVisibleChange = { macrosVisible = it }
                    )
                }

                WorkspaceChainEditor(
                    devices = WorkspaceRepository.samplingChain.devices.value,
                    scrollState = scrollState,
                    onEvent = { viewModel.onEvent(it) }
                )
            }
        }
    }
}