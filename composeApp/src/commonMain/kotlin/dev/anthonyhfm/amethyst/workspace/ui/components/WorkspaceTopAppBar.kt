package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.settings.SettingsDialog
import dev.anthonyhfm.amethyst.timeline.ui.components.TimelinePlaybackControls
import dev.anthonyhfm.amethyst.timeline.ui.components.TimelineGridPicker
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceTopAppBar(
    mode: WorkspaceContract.WorkspaceMode,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .statusBarsPadding()
            .padding(16.dp)
            .fillMaxWidth(),

        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WorkspaceMode(mode, onEvent)

        if (mode is KeyframesWorkspaceMode) {
            FilledIconButton(
                onClick = {
                    mode.onEvent?.invoke(
                        KeyframesChainDeviceContract.Event.OnImportMidiFile
                    )
                }
            ) {
                Icon(Icons.Default.FileOpen, null)
            }
        }

        Spacer(Modifier.weight(1f))

        if (mode is WorkspaceContract.WorkspaceMode.Timeline) {
            TimelinePlaybackControls()
            TimelineGridPicker()
        }

        BPMChanger()

        CleanupButtons()

        FilledIconButton(
            onClick = {
                showSettingsDialog = true
            }
        ) {
            Icon(Icons.Default.Settings, null)
        }
    }

    // Settings dialog - platform implementations handle visibility appropriately
    SettingsDialog(
        visible = showSettingsDialog,
        onDismiss = { showSettingsDialog = false }
    )
}