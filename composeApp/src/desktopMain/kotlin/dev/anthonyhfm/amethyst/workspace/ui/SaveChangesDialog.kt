package dev.anthonyhfm.amethyst.workspace.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Dialog
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogContent

@Composable
fun SaveChangesDialog(
    description: String = stringResource(Res.string.workspace_savechanges_description),
    onSave: () -> Unit,
    onDontSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val dialogState = rememberDialogState()

    LaunchedEffect(Unit) {
        dialogState.visible = true
    }

    Dialog(
        state = dialogState,
        onDismiss = onCancel,
    ) {
        DialogContent(
            modifier = Modifier.width(400.dp),
            showCloseButton = false,
        ) {
            AlertDialogHeader {
                AlertDialogTitle(stringResource(Res.string.workspace_savechanges_title))
                AlertDialogDescription(description)
            }

            AlertDialogFooter {
                AlertDialogCancel(onClick = onCancel) {
                    Text(stringResource(Res.string.workspace_savechanges_keep_editing))
                }

                Spacer(modifier = Modifier.weight(1f))

                AlertDialogAction(
                    onClick = onDontSave,
                    variant = ButtonVariant.Secondary,
                ) {
                    Text(stringResource(Res.string.workspace_savechanges_dont_save))
                }

                AlertDialogAction(onClick = onSave) {
                    Text(stringResource(Res.string.workspace_savechanges_save))
                }
            }
        }
    }
}
