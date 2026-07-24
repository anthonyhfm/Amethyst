package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.sp
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
        modifier = Modifier.width(420.dp),
        onDismiss = onDismiss,
    ) {
        AlertDialogHeader {
            AlertDialogTitle(stringResource(Res.string.workspace_autoplay_settings))
            AlertDialogDescription("Configure what is shown during AutoPlay playback.")
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_autoplay_settings_show_presses),
                        style = Theme[typography][p],
                        color = Theme[colors][foreground],
                    )

                    Text(
                        text = stringResource(Res.string.workspace_autoplay_settings_show_presses_description),
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                        lineHeight = 16.sp,
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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_autoplay_settings_show_lights),
                        style = Theme[typography][p],
                        color = Theme[colors][foreground],
                    )

                    Text(
                        text = stringResource(Res.string.workspace_autoplay_settings_show_lights_description),
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                        lineHeight = 16.sp,
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
                Text(stringResource(Res.string.workspace_autoplay_settings_cancel))
            }

            Spacer(Modifier.weight(1f))

            AlertDialogAction(
                onClick = {
                    WorkspaceRepository.updateAutoPlaySettings(
                        showButtonPresses = showButtonPresses,
                        showLights = showLights,
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(Res.string.workspace_autoplay_settings_save))
            }
        }
    }
}
