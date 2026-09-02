package dev.anthonyhfm.amethyst.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.network.presence.CollaborationPresence
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.workspace.ui.components.ActivityToastOverlay
import dev.anthonyhfm.amethyst.workspace.ui.components.DeviceSettingsDialog
import dev.anthonyhfm.amethyst.workspace.ui.components.ExitWorkspaceBottomSheet
import dev.anthonyhfm.amethyst.workspace.ui.components.InsertLaunchpadDialog
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceTopAppBar

@Composable
fun Workspace(onBack: () -> Unit = {}) {
    val mode by WorkspaceRepository.mode.collectAsState()
    val activityToasts by CollaborationPresence.activityToasts.collectAsState()
    var showExitSheet by remember { mutableStateOf(false) }

    val showDeviceConfigurator by WorkspaceRepository.showDeviceConfigurator.collectAsState()
    val showDevicePicker by WorkspaceRepository.showDevicePicker.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme[colors][background]),
    ) {
        WorkspaceTopAppBar(
            mode = mode,
            onBack = {
                if (WorkspaceRepository.hasUnsavedChanges()) {
                    showExitSheet = true
                } else {
                    onBack()
                }
            },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (showExitSheet) {
                ExitWorkspaceBottomSheet(
                    onSaveAndExit = {
                        showExitSheet = false
                        WorkspaceRepository.saveWorkspace()
                        onBack()
                    },
                    onDiscardAndExit = {
                        showExitSheet = false
                        onBack()
                    },
                    onCancel = {
                        showExitSheet = false
                    }
                )
            }

            if (showDeviceConfigurator != null) {
                DeviceSettingsDialog(
                    uuid = showDeviceConfigurator!!
                )
            }

            if (showDevicePicker) {
                InsertLaunchpadDialog()
            }

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
                    .clipToBounds()
            ) {
                mode.Content(Modifier.fillMaxSize())
            }
        }
    }
}
