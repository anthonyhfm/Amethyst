package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceType
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiPortDetails
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsDialog(
    index: Int,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    var midiAccess = koinInject<MidiAccess>()

    var expandedInput: Boolean by remember { mutableStateOf(false) }
    var expandedOutput: Boolean by remember { mutableStateOf(false) }

    var midiInputPort: MidiPortDetails? by remember { mutableStateOf(null) }
    var midiOutputPort: MidiPortDetails? by remember { mutableStateOf(null) }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth(),

                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expandedInput,
                    onExpandedChange = {
                        expandedInput = it
                    }
                ) {
                    OutlinedTextField(
                        value = midiInputPort?.name ?: "",
                        onValueChange = { },
                        label = { Text("Midi Input") },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedInput,
                        onDismissRequest = { expandedInput = false },
                    ) {
                        midiAccess.inputs.forEach {
                            DropdownMenuItem(
                                text = { Text(it.name ?: "Unknown Input") },
                                onClick = {
                                    midiInputPort = it
                                    expandedInput = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expandedOutput,
                    onExpandedChange = {
                        expandedOutput = it
                    }
                ) {
                    OutlinedTextField(
                        value = midiOutputPort?.name ?: "",
                        onValueChange = { },
                        label = { Text("Midi Output") },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedOutput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedOutput,
                        onDismissRequest = { expandedOutput = false },
                    ) {
                        midiAccess.outputs.forEach {
                            DropdownMenuItem(
                                text = { Text(it.name ?: "Unknown Output") },
                                onClick = {
                                    midiOutputPort = it
                                    expandedOutput = false
                                }
                            )
                        }
                    }
                }

                Text("The device type will be recognized automatically")
            }
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
                    onEvent(
                        WorkspaceContract.Event.OnChangeDeviceConfig(
                            index = index,
                            inputPort = midiInputPort,
                            outputPort = midiOutputPort,
                        )
                    )
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