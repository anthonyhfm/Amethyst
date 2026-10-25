package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun WorkspaceDeviceDialogs(
    deviceConfigurationUuid: String?,
    showDevicePicker: Boolean,
)
