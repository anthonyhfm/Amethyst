package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

@Composable
expect fun WorkspaceTopAppBar(
    onBack: () -> Unit,
    mode: dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode,
    onEvent: (WorkspaceContract.Event) -> Unit,
)
