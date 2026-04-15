package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.Switch
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun AutoPlaySettingsDialog(
    onDismiss: () -> Unit
) {
    val currentSettings = WorkspaceRepository.workspaceMeta?.settings
    val dialogState = rememberDialogState(initiallyVisible = true)

    var showButtonPresses by remember {
        mutableStateOf(currentSettings?.autoPlayShowButtonPresses ?: true)
    }
    var showLights by remember {
        mutableStateOf(currentSettings?.autoPlayShowLights ?: true)
    }

    AlertDialog(
        state = dialogState,
        modifier = Modifier.width(340.dp),
        onDismiss = onDismiss,
    ) {
        AlertDialogHeader {
            AlertDialogTitle("AutoPlay Settings")
            AlertDialogDescription("Configure what is shown during AutoPlay playback.")
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Show Button Presses",
                        style = Theme[typography][p],
                        color = Theme[colors][foreground],
                    )
                    Text(
                        text = "Display white LEDs to indicate button presses",
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                    )
                }
                Switch(
                    checked = showButtonPresses,
                    onCheckedChange = { showButtonPresses = it },
                )
            }

            Separator(
                modifier = Modifier
                    .fillMaxWidth(),
                orientation = SeparatorOrientation.Horizontal,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Show Lights",
                        style = Theme[typography][p],
                        color = Theme[colors][foreground],
                    )
                    Text(
                        text = "Process project lights and show them during AutoPlay",
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                    )
                }
                Switch(
                    checked = showLights,
                    onCheckedChange = { showLights = it },
                )
            }
        }

        AlertDialogFooter {
            AlertDialogCancel(onClick = onDismiss) {
                Text("Cancel")
            }

            AlertDialogAction(
                onClick = {
                    WorkspaceRepository.updateAutoPlaySettings(
                        showButtonPresses = showButtonPresses,
                        showLights = showLights,
                    )
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        }
    }
}
