package dev.anthonyhfm.amethyst.gem.ui.editor

import androidx.compose.runtime.Composable
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant

@Composable
internal fun GemUnsavedChangesDialog(
    pending: GemEditorPendingTransition,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    val dialogState = rememberDialogState(initiallyVisible = true)

    AlertDialog(
        state = dialogState,
        onDismiss = onCancel
    ) {
        AlertDialogHeader {
            AlertDialogTitle("Unsaved Gem changes")
            AlertDialogDescription(
                buildString {
                    append("Save, discard, or cancel before leaving ")
                    append(pending.prompt.activeAssetName.ifBlank { "this Gem" })
                    append(". Validation: ")
                    append(if (pending.prompt.isValid) "valid" else "issues detected")
                    append(". Runnable: ")
                    append(if (pending.prompt.isRunnable) "yes" else "no")
                    append('.')
                }
            )
        }
        AlertDialogFooter {
            AlertDialogCancel(onClick = onCancel) {
                Text("Cancel")
            }
            AlertDialogAction(
                onClick = onDiscard,
                variant = ButtonVariant.Outline
            ) {
                Text("Discard")
            }
            AlertDialogAction(onClick = onSave) {
                Text("Save")
            }
        }
    }
}
