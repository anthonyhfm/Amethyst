package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.workspace_exit_dialog_cancel
import amethyst.composeapp.generated.resources.workspace_exit_dialog_description
import amethyst.composeapp.generated.resources.workspace_exit_dialog_dont_save
import amethyst.composeapp.generated.resources.workspace_exit_dialog_save
import amethyst.composeapp.generated.resources.workspace_exit_dialog_title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun ExitWorkspaceDialog(
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(stringResource(Res.string.workspace_exit_dialog_title))
        },
        text = {
            Text(stringResource(Res.string.workspace_exit_dialog_description))
        },
        confirmButton = {
            Button(onClick = onSaveAndExit) {
                Text(stringResource(Res.string.workspace_exit_dialog_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscardAndExit,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(Res.string.workspace_exit_dialog_dont_save))
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.workspace_exit_dialog_cancel))
            }
        },
    )
}
