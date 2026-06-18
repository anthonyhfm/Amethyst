package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun AddDeviceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
