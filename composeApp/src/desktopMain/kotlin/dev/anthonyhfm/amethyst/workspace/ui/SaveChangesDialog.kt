package dev.anthonyhfm.amethyst.workspace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.theme.AMETHYST_THEME

@Composable
fun SaveChangesDialog(
    onSave: () -> Unit,
    onDontSave: () -> Unit,
    onCancel: () -> Unit
) {
    MaterialTheme(
        colorScheme = AMETHYST_THEME
    ) {
        AlertDialog(
            onDismissRequest = onCancel,
            modifier = Modifier.width(400.dp),
            title = {
                Text("Save Changes?")
            },
            text = {
                Text("You have unsaved changes. Do you want to save them before closing?")
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    TextButton(onClick = onDontSave) {
                        Text("Don't Save")
                    }
                }
            },
            confirmButton = {
                Button(onClick = onSave) {
                    Text("Save")
                }
            }
        )
    }
}
