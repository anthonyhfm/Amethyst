package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

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

    LaunchedEffect(key1 = showDevicePicker) {
        if (showDevicePicker) {
            IosWorkspaceBridge.onShowDevicePicker?.invoke()
        }
    }
}
