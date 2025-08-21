package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TooltipBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceTopAppBar(
    mode: WorkspaceContract.WorkspaceMode,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
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

        BPMChanger()

        FilledIconButton(
            onClick = {
                WorkspaceRepository.resetMulti()
            }
        ) {
            Icon(Icons.Default.RestartAlt, null)
        }

        FilledIconButton(
            onClick = {

            }
        ) {
            Icon(Icons.Default.Settings, null)
        }
    }
}