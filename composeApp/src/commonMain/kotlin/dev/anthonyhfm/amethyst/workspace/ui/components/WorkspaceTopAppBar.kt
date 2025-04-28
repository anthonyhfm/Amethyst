package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

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

        Spacer(Modifier.weight(1f))

        BPMChanger()

        FilledIconButton(
            onClick = {

            }
        ) {
            Icon(Icons.Default.Settings, null)
        }
    }
}