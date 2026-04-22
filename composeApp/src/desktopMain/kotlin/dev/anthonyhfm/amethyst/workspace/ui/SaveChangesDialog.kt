package dev.anthonyhfm.amethyst.workspace.ui

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
    description: String = "You have unsaved changes. Do you want to save them before closing?",
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
                AlertDialogTitle("Save Changes?")
                AlertDialogDescription(description)
            }

            AlertDialogFooter {
                AlertDialogCancel(onClick = onCancel) {
                    Text("Cancel")
                }

                Spacer(modifier = Modifier.weight(1f))

                AlertDialogAction(
                    onClick = onDontSave,
                    variant = ButtonVariant.Secondary,
                ) {
                    Text("Don't Save")
                }

                AlertDialogAction(onClick = onSave) {
                    Text("Save")
                }
            }
        }
    }
}
