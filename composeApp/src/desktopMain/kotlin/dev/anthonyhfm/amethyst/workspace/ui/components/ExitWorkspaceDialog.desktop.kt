package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.workspace_exit_dialog_description
import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.workspace.ui.SaveChangesDialog
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun ExitWorkspaceDialog(
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onCancel: () -> Unit,
) {
    SaveChangesDialog(
        description = stringResource(Res.string.workspace_exit_dialog_description),
        onSave = onSaveAndExit,
        onDontSave = onDiscardAndExit,
        onCancel = onCancel,
    )
}
