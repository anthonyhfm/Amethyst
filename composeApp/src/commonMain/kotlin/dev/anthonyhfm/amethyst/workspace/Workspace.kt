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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.network.presence.CollaborationPresence
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.Timeline
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.workspace.help.GetHelpWorkspaceMode
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract.Event
import dev.anthonyhfm.amethyst.workspace.ui.components.DeviceSettingsDialog
import dev.anthonyhfm.amethyst.workspace.chain.ui.WorkspaceChainEditor
import dev.anthonyhfm.amethyst.workspace.ui.components.AutoPlayButtons
import dev.anthonyhfm.amethyst.workspace.ui.components.ActivityToastOverlay
import dev.anthonyhfm.amethyst.workspace.ui.components.InsertLaunchpadDialog
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceTopAppBar
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberScrollAreaState

@Composable
fun Workspace(onBack: () -> Unit = {}) {
    val viewModel: WorkspaceViewModel = viewModel { WorkspaceViewModel() }

    val state by viewModel.state.collectAsState()
    val activityToasts by CollaborationPresence.activityToasts.collectAsState()

    val lightsChainScrollState = rememberScrollAreaState()
    val samplingChainScrollState = rememberScrollAreaState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),

        containerColor = Theme[colors][background],
        contentColor = Theme[colors][foreground],

        topBar = {
            WorkspaceTopAppBar(
                mode = state.mode,
                onBack = onBack,
                onEvent = {
                    viewModel.onEvent(it)
                },
            )
        }
    ) { paddingValues ->
        if (state.showDeviceConfigurator != null) {
            DeviceSettingsDialog(
                uuid = state.showDeviceConfigurator!!,
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

            ActivityToastOverlay(
                toasts = activityToasts,
                onDismiss = CollaborationPresence::dismissToast,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            )

            AnimatedVisibility(
                visible = state.mode is WorkspaceContract.WorkspaceMode.Layout,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                Button(
                    onClick = {
                        viewModel.onEvent(Event.OpenVirtualDevicePicker)
                    },
                    variant = ButtonVariant.Default,
                    size = ButtonSize.Icon,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Theme[colors][primaryForeground],
                    )
                }
            }

            AnimatedVisibility(
                visible = state.mode is WorkspaceContract.WorkspaceMode.Performance,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                AutoPlayButtons()
            }

            AnimatedVisibility(
                visible = state.mode is WorkspaceContract.WorkspaceMode.LightsChain,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                WorkspaceChainEditor(
                    devices = WorkspaceRepository.lightsChain.devices.value,
                    scrollState = lightsChainScrollState,
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
                    devices = WorkspaceRepository.samplingChain.devices.value,
                    scrollState = samplingChainScrollState,
                    onEvent = { viewModel.onEvent(it) }
                )
            }

            AnimatedVisibility(
                visible = state.mode is WorkspaceContract.WorkspaceMode.Timeline,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                Timeline()
            }

            if (state.mode is KeyframesWorkspaceMode) {
                (state.mode as KeyframesWorkspaceMode).ModeContent(paddingValues)
            }

            if (state.mode is PianoRollWorkspaceMode) {
                (state.mode as PianoRollWorkspaceMode).ModeContent(paddingValues)
            }

            if (state.mode is GetHelpWorkspaceMode) {
                (state.mode as GetHelpWorkspaceMode).ModeContent(paddingValues)
            }
        }
    }
}
