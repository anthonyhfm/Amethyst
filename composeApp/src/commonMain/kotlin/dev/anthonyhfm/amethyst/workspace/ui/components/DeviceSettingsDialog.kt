package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

@Composable
fun DeviceSettingsDialog(
    index: Int,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            onEvent(WorkspaceContract.Event.OnDismissDeviceConfigure)
        },
        modifier = Modifier
            .width(400.dp),
        title = {
            Text("Device Configuration")
        },
        text = {

        },
        dismissButton = {
            TextButton(
                onClick = {
                    onEvent(WorkspaceContract.Event.OnDismissDeviceConfigure)
                }
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onEvent(WorkspaceContract.Event.OnDismissDeviceConfigure)
                }
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize
                )
            }
        }
    )
}