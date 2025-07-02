package dev.anthonyhfm.amethyst.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract.Event
import dev.anthonyhfm.amethyst.workspace.ui.components.DeviceSettingsDialog
import dev.anthonyhfm.amethyst.workspace.chain.ui.WorkspaceChainEditor
import dev.anthonyhfm.amethyst.workspace.ui.components.InsertLaunchpadDialog
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceTopAppBar
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Workspace() {
    val viewModel: WorkspaceViewModel = koinViewModel()

    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),

        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(0.5.dp),
        contentColor = MaterialTheme.colorScheme.onBackground,

        topBar = {
            WorkspaceTopAppBar(
                mode = state.mode,
                onEvent = {
                    viewModel.onEvent(it)
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = state.mode is WorkspaceContract.WorkspaceMode.Layout,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        viewModel.onEvent(Event.OpenVirtualDevicePicker)
                    }
                ) {
                    Icon(Icons.Default.Add, null)
                }
            }
        }
    ) { paddingValues ->
        if (state.showDeviceConfigurator != null) {
            DeviceSettingsDialog(
                index = state.showDeviceConfigurator!!,
                onEvent = { viewModel.onEvent(it) }
            )
        }

        if (state.showDevicePicker) {
            InsertLaunchpadDialog(
                onEvent = {
                    viewModel.onEvent(it)
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            WorkspaceViewport(
                viewportState = state.viewportState,
                elements = state.viewportElements,
                onEvent = {
                    viewModel.onEvent(it)
                }
            )

            AnimatedVisibility(
                visible = state.mode is WorkspaceContract.WorkspaceMode.LightsChain,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                WorkspaceChainEditor(
                    sampling = false,
                    devices = WorkspaceRepository.lightsChain.heavenChain.devices.value,
                    onEvent = { viewModel.onEvent(it) }
                )
            }

            AnimatedVisibility(
                visible = state.mode is WorkspaceContract.WorkspaceMode.SamplingChain,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                WorkspaceChainEditor(
                    sampling = true,
                    devices = WorkspaceRepository.samplingChain.heavenChain.devices.value,
                    onEvent = { viewModel.onEvent(it) }
                )
            }

            if (state.mode is KeyframesWorkspaceMode) {
                (state.mode as KeyframesWorkspaceMode).ModeContent(paddingValues)
            }
        }
    }
}