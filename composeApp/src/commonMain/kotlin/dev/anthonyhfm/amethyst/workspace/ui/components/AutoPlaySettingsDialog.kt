package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings

@Composable
fun AutoPlaySettingsDialog(
    onDismiss: () -> Unit
) {
    val currentSettings = WorkspaceRepository.saveableWorkspaceData?.settings
    
    var showButtonPresses by remember { 
        mutableStateOf(currentSettings?.autoPlayShowButtonPresses ?: true) 
    }
    var showLights by remember { 
        mutableStateOf(currentSettings?.autoPlayShowLights ?: true) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AutoPlay Settings") },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Button Presses")
                    Switch(
                        checked = showButtonPresses,
                        onCheckedChange = { showButtonPresses = it }
                    )
                }
                Text(
                    text = "Displays white LED signals on layer 100 to indicate button presses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Lights")
                    Switch(
                        checked = showLights,
                        onCheckedChange = { showLights = it }
                    )
                }
                Text(
                    text = "Sends AutoPlay signals to the lights chain",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    WorkspaceRepository.updateAutoPlaySettings(
                        showButtonPresses = showButtonPresses,
                        showLights = showLights
                    )
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
