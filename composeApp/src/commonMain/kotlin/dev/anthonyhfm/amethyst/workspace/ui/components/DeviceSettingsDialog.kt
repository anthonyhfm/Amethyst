package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiDeviceDetails
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiManager
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
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LayoutWorkspaceMode

@Composable
fun DeviceSettingsDialog(
    uuid: String,
) {
    val device = Heaven.devices.find { it.selectionUUID == uuid }
    val detectedDevices by AmethystMidiManager.detectedDevices.collectAsState()

    val devices = detectedDevices

    val dialogState = rememberDialogState(initiallyVisible = true)

    var selectedDevice: AmethystMidiDeviceDetails? by remember(uuid, device, devices) {
        mutableStateOf(
            devices.firstOrNull { it.id == device?.savedInputPortId }
                ?: devices.firstOrNull { it.friendlyName == device?.savedInputPortName }
        )
    }

    AlertDialog(
        state = dialogState,
        modifier = Modifier.width(350.dp),
        onDismiss = {
            WorkspaceRepository.closeDeviceConfigurator()
        },
    ) {
        AlertDialogHeader {
            AlertDialogTitle(stringResource(Res.string.workspace_device_settings_title))
            AlertDialogDescription("Choose the MIDI device for this layout element.")
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field {
                FieldLabel(stringResource(Res.string.workspace_device_settings_midi_device_label))
                Combobox(
                    items = devices,
                    selectedItem = selectedDevice,
                    onItemSelected = { selectedDevice = it },
                    itemLabel = { it.friendlyName },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (devices.isEmpty()) stringResource(Res.string.workspace_device_settings_no_devices) else stringResource(Res.string.workspace_device_settings_select_device),
                    searchPlaceholder = "Search devices...",
                    emptyMessage = "No devices found.",
                    enabled = devices.isNotEmpty(),
                )
                FieldDescription("Launchpad devices are auto-detected and grouped natively.")
            }
        }

        AlertDialogFooter {
            if (platform is Platform.Android || platform is Platform.iOS) {
                AlertDialogAction(
                    onClick = {
                        if (WorkspaceRepository.mode.value is LayoutWorkspaceMode) {
                            AmethystMidiManager().changeDeviceConfig(uuid, selectedDevice?.id)
                        }
                        WorkspaceRepository.closeDeviceConfigurator()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.workspace_device_settings_save))
                }

                AlertDialogCancel(
                    onClick = {
                        WorkspaceRepository.closeDeviceConfigurator()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.workspace_device_settings_cancel))
                }
            } else {
                AlertDialogCancel(
                    onClick = {
                        WorkspaceRepository.closeDeviceConfigurator()
                    },
                ) {
                    Text(stringResource(Res.string.workspace_device_settings_cancel))
                }

                Spacer(modifier = Modifier.weight(1f))

                AlertDialogAction(
                    onClick = {
                        if (WorkspaceRepository.mode.value is LayoutWorkspaceMode) {
                            AmethystMidiManager().changeDeviceConfig(uuid, selectedDevice?.id)
                        }
                        WorkspaceRepository.closeDeviceConfigurator()
                    },
                ) {
                    Text(stringResource(Res.string.workspace_device_settings_save))
                }
            }
        }
    }
}
