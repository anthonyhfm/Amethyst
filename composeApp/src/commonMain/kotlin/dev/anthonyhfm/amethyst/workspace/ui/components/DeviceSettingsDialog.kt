package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.platformMidiAccess
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Combobox
import dev.anthonyhfm.amethyst.ui.components.primitives.Field
import dev.anthonyhfm.amethyst.ui.components.primitives.FieldDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.FieldLabel
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.MidiPortDetails

@Composable
fun DeviceSettingsDialog(
    uuid: String,
    onEvent: (WorkspaceContract.Event) -> Unit,
) {
    val midiAccess = platformMidiAccess ?: EmptyMidiAccess()
    val device = Heaven.devices.find { it.selectionUUID == uuid }
    val rawInputPorts = remember(midiAccess) { midiAccess.inputs.toList() }
    val rawOutputPorts = remember(midiAccess) { midiAccess.outputs.toList() }

    val inputPorts = remember(rawInputPorts, device) {
        val list = rawInputPorts.toMutableList()
        if (device != null && (device.savedInputPortId != null || device.savedInputPortName != null)) {
            val exists = list.any { it.id == device.savedInputPortId || (device.savedInputPortId == null && it.name == device.savedInputPortName) }
            if (!exists) {
                list.add(0, object : MidiPortDetails {
                    override val id: String = device.savedInputPortId ?: ""
                    override val manufacturer: String? = null
                    override val name: String? = device.savedInputPortName ?: "Disconnected Input"
                    override val version: String? = null
                    override val midiTransportProtocol: Int = 1
                })
            }
        }
        list
    }

    val outputPorts = remember(rawOutputPorts, device) {
        val list = rawOutputPorts.toMutableList()
        if (device != null && (device.savedOutputPortId != null || device.savedOutputPortName != null)) {
            val exists = list.any { it.id == device.savedOutputPortId || (device.savedOutputPortId == null && it.name == device.savedOutputPortName) }
            if (!exists) {
                list.add(0, object : MidiPortDetails {
                    override val id: String = device.savedOutputPortId ?: ""
                    override val manufacturer: String? = null
                    override val name: String? = device.savedOutputPortName ?: "Disconnected Output"
                    override val version: String? = null
                    override val midiTransportProtocol: Int = 1
                })
            }
        }
        list
    }

    val dialogState = rememberDialogState(initiallyVisible = true)

    var midiInputPort: MidiPortDetails? by remember(uuid, device) {
        mutableStateOf(
            device?.deviceConfig?.input?.details
                ?: inputPorts.firstOrNull { it.id == device?.savedInputPortId }
                ?: inputPorts.firstOrNull { it.name == device?.savedInputPortName }
        )
    }
    var midiOutputPort: MidiPortDetails? by remember(uuid, device) {
        mutableStateOf(
            device?.deviceConfig?.launchpadDevice?.midiOutput?.details
                ?: outputPorts.firstOrNull { it.id == device?.savedOutputPortId }
                ?: outputPorts.firstOrNull { it.name == device?.savedOutputPortName }
        )
    }

    val selectedInput = inputPorts.firstOrNull { it.id == midiInputPort?.id } ?: midiInputPort
    val selectedOutput = outputPorts.firstOrNull { it.id == midiOutputPort?.id } ?: midiOutputPort

    AlertDialog(
        state = dialogState,
        modifier = Modifier.width(350.dp),
        onDismiss = {
            onEvent(WorkspaceContract.Event.OnDismissDeviceConfigure)
        },
    ) {
        AlertDialogHeader {
            AlertDialogTitle("Device Configuration")
            AlertDialogDescription("Choose the MIDI input and output ports for this device.")
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field {
                FieldLabel("MIDI Input")
                Combobox(
                    items = inputPorts,
                    selectedItem = selectedInput,
                    onItemSelected = { midiInputPort = it },
                    itemLabel = { it.name ?: "Unknown Input" },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (inputPorts.isEmpty()) "No inputs available" else "Select an input",
                    searchPlaceholder = "Search inputs...",
                    emptyMessage = "No inputs found.",
                    enabled = inputPorts.isNotEmpty(),
                )
            }

            Field {
                FieldLabel("MIDI Output")
                Combobox(
                    items = outputPorts,
                    selectedItem = selectedOutput,
                    onItemSelected = { midiOutputPort = it },
                    itemLabel = { it.name ?: "Unknown Output" },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (outputPorts.isEmpty()) "No outputs available" else "Select an output",
                    searchPlaceholder = "Search outputs...",
                    emptyMessage = "No outputs found.",
                    enabled = outputPorts.isNotEmpty(),
                )
                FieldDescription("The device type will be recognized automatically.")
            }
        }

        AlertDialogFooter {
            AlertDialogCancel(
                onClick = {
                    onEvent(WorkspaceContract.Event.OnDismissDeviceConfigure)
                },
            ) {
                Text("Cancel")
            }

            Spacer(modifier = Modifier.weight(1f))

            AlertDialogAction(
                onClick = {
                    onEvent(
                        WorkspaceContract.Event.OnChangeDeviceConfig(
                            uuid = uuid,
                            inputPort = midiInputPort,
                            outputPort = midiOutputPort,
                        )
                    )
                    onEvent(WorkspaceContract.Event.OnDismissDeviceConfigure)
                },
            ) {
                Text("Save")
            }
        }
    }
}
