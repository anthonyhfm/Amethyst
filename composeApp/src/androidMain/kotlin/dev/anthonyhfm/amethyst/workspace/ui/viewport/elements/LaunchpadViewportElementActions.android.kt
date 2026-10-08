package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncCoordinator
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.components.AndroidDeviceStyleSheet

@Composable
actual fun LaunchpadViewportElementActions(
    element: LaunchpadViewportElement,
    modifier: Modifier,
) {
    var showStyleSheet by remember(element) { mutableStateOf(false) }
    val deleteDialogState = rememberDialogState()

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .padding(2.dp),
    ) {
        IconButton(
            onClick = {
                WorkspaceRepository.openDeviceConfigurator(element.selectionUUID)
            },
        ) {
            Icon(
                imageVector = Icons.Outlined.Usb,
                contentDescription = stringResource(Res.string.workspace_viewport_launchpad_actions_connection),
            )
        }

        IconButton(
            onClick = {
                element.rotationDegrees.floatValue += 90f
                DeviceSyncCoordinator.onDeviceRotationChanged(element)
            },
        ) {
            Icon(
                imageVector = Icons.Outlined.Rotate90DegreesCw,
                contentDescription = stringResource(Res.string.workspace_viewport_launchpad_actions_rotate),
            )
        }

        if (element.hasStyleOptions) {
            IconButton(
                onClick = {
                    showStyleSheet = true
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Style,
                    contentDescription = stringResource(Res.string.workspace_viewport_launchpad_actions_style),
                )
            }
        }

        IconButton(
            onClick = { WorkspaceRepository.openDevicePicker(replacingDeviceId = element.launchpadId) },
        ) {
            Icon(
                imageVector = Icons.Outlined.SwapHoriz,
                contentDescription = stringResource(resource = Res.string.workspace_viewport_launchpad_actions_swap),
            )
        }

        FilledIconButton(
            onClick = {
                deleteDialogState.visible = true
            },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(Res.string.workspace_viewport_launchpad_actions_delete),
            )
        }
    }

    if (showStyleSheet) {
        AndroidDeviceStyleSheet(
            element = element,
            onDismiss = { showStyleSheet = false },
        )
    }

    AlertDialog(
        state = deleteDialogState,
    ) {
        AlertDialogHeader {
            AlertDialogTitle(
                text = stringResource(Res.string.workspace_viewport_launchpad_actions_delete_dialog_title),
            )

            AlertDialogDescription(
                text = "This will permanently remove \"${element.name}\" from the layout.",
            )
        }

        AlertDialogFooter {
            AlertDialogCancel(
                onClick = {
                    deleteDialogState.visible = false
                },
            ) {
                Text(
                    text = stringResource(Res.string.workspace_viewport_launchpad_actions_delete_dialog_cancel),
                )
            }

            AlertDialogAction(
                onClick = {
                    deleteDialogState.visible = false
                    SelectionManager.clear()
                    WorkspaceRepository.removeVirtualDevice(element.selectionUUID)
                },
                variant = ButtonVariant.Destructive,
            ) {
                Text(
                    text = stringResource(Res.string.workspace_viewport_launchpad_actions_delete),
                )
            }
        }
    }
}
