package dev.anthonyhfm.amethyst.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import dev.anthonyhfm.amethyst.workspace.ui.components.AutoPlayButtons
import dev.anthonyhfm.amethyst.workspace.ui.components.ActivityToastOverlay
import dev.anthonyhfm.amethyst.workspace.ui.components.InsertLaunchpadDialog
import dev.anthonyhfm.amethyst.workspace.ui.components.AddDeviceButton
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceTopAppBar
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LightsChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Zap
import dev.anthonyhfm.amethyst.settings.SettingsDialog
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainCanvas

@Composable
fun Workspace(onBack: () -> Unit = {}) {
    val viewModel: WorkspaceViewModel = viewModel { WorkspaceViewModel() }

    val state by viewModel.state.collectAsState()
    val activityToasts by CollaborationPresence.activityToasts.collectAsState()

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
            ActivityToastOverlay(
                toasts = activityToasts,
                onDismiss = CollaborationPresence::dismissToast,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .clipToBounds()
            ) {
                state.mode.Content(Modifier.fillMaxSize())
            }
        }
    }
}
