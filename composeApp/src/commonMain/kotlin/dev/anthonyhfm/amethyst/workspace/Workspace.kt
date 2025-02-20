package dev.anthonyhfm.amethyst.workspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract.Event
import dev.anthonyhfm.amethyst.workspace.ui.components.DeviceSettingsDialog
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

        topBar = {
            WorkspaceTopAppBar(
                mode = state.mode,
                onEvent = {
                    viewModel.onEvent(it)
                }
            )
        },

        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.onEvent(Event.AddDeviceToViewport)
                }
            ) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) {
        if (state.showDeviceConfigurator != null) {
            DeviceSettingsDialog(
                index = state.showDeviceConfigurator!!,
                onEvent = { viewModel.onEvent(it) }
            )
        }

        WorkspaceViewport(
            elements = state.viewportElements,
            onEvent = {
                viewModel.onEvent(it)
            }
        )
    }
}