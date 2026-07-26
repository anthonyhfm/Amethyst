package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun WorkspaceTopAppBar(
    onBack: () -> Unit,
    mode: dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode,
)
