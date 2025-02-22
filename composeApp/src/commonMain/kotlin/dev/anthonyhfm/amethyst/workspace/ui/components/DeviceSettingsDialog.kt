package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.devices.DeviceType
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.atsushieno.ktmidi.MidiPortDetails

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
            .width(350.dp),
        title = {
            Text("Device Configuration")
        },
        text = {
            DeviceSettingsContent()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsContent() {
    var expanded: Boolean by remember { mutableStateOf(false) }

    var midiDeviceType: DeviceType? by remember { mutableStateOf(null) }
    var midiInputPort: MidiPortDetails? by remember { mutableStateOf(null) }
    var midiOutputPort: MidiPortDetails? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = it
            }
        ) {
            OutlinedTextField(
                value = midiInputPort?.name ?: "",
                onValueChange = { },
                label = { Text("Midi Input") },
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {

            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = it
            }
        ) {
            OutlinedTextField(
                value = midiOutputPort?.name ?: "",
                onValueChange = { },
                label = { Text("Midi Output") },
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {

            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = it
            }
        ) {
            OutlinedTextField(
                value = midiDeviceType?.name ?: "",
                onValueChange = { },
                label = { Text("Midi Device Type") },
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {

            }
        }
    }
}