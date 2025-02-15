package dev.anthonyhfm.amethyst.editor.projectsettings.launchpadconfig.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.data.project.ProjectDeviceConfig
import dev.anthonyhfm.amethyst.core.midi.devices.DeviceType
import dev.anthonyhfm.amethyst.core.midi.devices.novation.LaunchpadMiniMk3Device
import dev.anthonyhfm.amethyst.core.midi.devices.novation.LaunchpadMk2Device
import dev.anthonyhfm.amethyst.core.midi.devices.novation.LaunchpadProMk3Device
import dev.anthonyhfm.amethyst.core.midi.devices.novation.LaunchpadXDevice
import dev.anthonyhfm.amethyst.core.midi.devices.others.MatrixDevice
import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiPortDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject

@Composable
fun LaunchpadConfigItem(
    item: ProjectDeviceConfig,
    onChangeItemProperties: (ProjectDeviceConfig) -> Unit
) {
    val midiAccess = koinInject<MidiAccess>()
    var showPlaceholder: Boolean by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = "Name:",
                    style = MaterialTheme.typography.labelLarge
                )

                BasicTextField(
                    value = if (!showPlaceholder) {
                        item.name
                    } else {
                        "Enter device name"
                    },
                    onValueChange = {
                        onChangeItemProperties(
                            item.copy(
                                name = it
                            )
                        )
                    },
                    singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    textStyle = MaterialTheme.typography.labelLarge.copy(
                        color = if (showPlaceholder) {
                            MaterialTheme.colorScheme.secondary.copy(0.6f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (!it.isFocused && (item.name.isEmpty() || item.name.isBlank())) {
                                showPlaceholder = true
                            } else {
                                showPlaceholder = false
                            }
                        }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var inputDevicesDialog: Boolean by remember { mutableStateOf(false) }

                Text(
                    text = "Input:",
                    style = MaterialTheme.typography.labelLarge
                )

                Row(
                    modifier = Modifier
                        .clickable {
                            inputDevicesDialog = true
                        }
                ) {
                    Text(
                        text = item.input?.details?.name ?: "Select",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .rotate(90f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                PortSelectionDialog(
                    expanded = inputDevicesDialog,
                    portList = midiAccess.inputs.toList(),
                    onSelect = {
                        onChangeItemProperties(
                            item.copy(
                                input = runBlocking(Dispatchers.IO) {
                                    midiAccess.openInput(it.id)
                                }
                            )
                        )

                        inputDevicesDialog = false
                    },
                    onDismiss = {
                        inputDevicesDialog = false
                    }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var outputDevicesDialog: Boolean by remember { mutableStateOf(false) }

                Text(
                    text = "Output:",
                    style = MaterialTheme.typography.labelLarge
                )

                Row(
                    modifier = Modifier
                        .clickable {
                            outputDevicesDialog = true
                        }
                ) {
                    Text(
                        text = item.output?.details?.name ?: "Select",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .rotate(90f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                PortSelectionDialog(
                    expanded = outputDevicesDialog,
                    portList = midiAccess.outputs.toList(),
                    onSelect = {
                        onChangeItemProperties(
                            item.copy(
                                output = runBlocking(Dispatchers.IO) {
                                    midiAccess.openOutput(it.id)
                                }
                            )
                        )

                        outputDevicesDialog = false
                    },
                    onDismiss = {
                        outputDevicesDialog = false
                    }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var deviceTypeDialog: Boolean by remember { mutableStateOf(false) }

                Text(
                    text = "Type:",
                    style = MaterialTheme.typography.labelLarge
                )

                Row(
                    modifier = Modifier
                        .clickable {
                            deviceTypeDialog = true
                        }
                ) {
                    Text(
                        text = item.type?.name ?: "Select",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .rotate(90f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                TypeSelectionDialog(
                    expanded = deviceTypeDialog,
                    onSelect = {
                        onChangeItemProperties(
                            item.copy(
                                type = it
                            )
                        )

                        deviceTypeDialog = false
                    },
                    onDismiss = {
                        deviceTypeDialog = false
                    }
                )
            }
        }
    }

    /**/
}

@Composable
fun PortSelectionDialog(
    expanded: Boolean,
    portList: List<MidiPortDetails>,
    onSelect: (MidiPortDetails) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            onDismiss()
        },
    ) {
        portList.forEach { port ->
            if (port.name != null) {
                DropdownMenuItem(
                    text = {
                        Text(port.name!!)
                    },
                    onClick = {
                        onSelect(port)
                    }
                )
            }
        }
    }
}

@Composable
fun TypeSelectionDialog(
    expanded: Boolean,
    onSelect: (DeviceType) -> Unit,
    onDismiss: () -> Unit
) {
    val deviceTypes by remember { mutableStateOf(
        listOf(
            LaunchpadProMk3Device(),
            LaunchpadXDevice(),
            LaunchpadMiniMk3Device(),
            MatrixDevice(),
            LaunchpadMk2Device()
        )
    ) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            onDismiss()
        },
    ) {
        deviceTypes.forEach {
            DropdownMenuItem(
                text = {
                    Text(it.name)
                },
                onClick = {
                    onSelect(it)
                }
            )
        }
    }
}