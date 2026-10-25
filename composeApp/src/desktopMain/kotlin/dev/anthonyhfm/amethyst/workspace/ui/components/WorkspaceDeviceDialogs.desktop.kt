package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun WorkspaceDeviceDialogs(
    deviceConfigurationUuid: String?,
    showDevicePicker: Boolean,
) {
    if (deviceConfigurationUuid != null) {
        DeviceSettingsDialog(
            uuid = deviceConfigurationUuid,
        )
    }

    if (showDevicePicker) {
        InsertLaunchpadDialog()
    }
}
