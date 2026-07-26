package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Cable
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Trash2
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncCoordinator
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Dialog
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogContent
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.FullShape
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

private val actionTrayBackground = Color(0xFF282C34)
private val actionTrayBorder = Color(0xFF3E4451)
private val actionButtonBg = Color(0xFF3F3F46)
private val actionButtonBgHover = Color(0xFF52525B)
private val deleteButtonBg = Color(0xFFDC2626)
private val deleteButtonBgHover = Color(0xFFEF4444)

@Composable
actual fun LaunchpadViewportElementActions(
    element: LaunchpadViewportElement,
    modifier: Modifier,
) {
    val styleDialogState = rememberDialogState()
    val deleteDialogState = rememberDialogState()

    Row(
        modifier = modifier
            .background(actionTrayBackground, FullShape)
            .border(1.dp, actionTrayBorder, FullShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LaunchpadActionButton(
            onClick = { WorkspaceRepository.openDeviceConfigurator(element.selectionUUID) },
            icon = Lucide.Cable,
            contentDescription = stringResource(Res.string.workspace_viewport_launchpad_actions_connection),
            backgroundHoverColor = actionButtonBgHover,
        )

        if (element.hasStyleOptions) {
            LaunchpadActionButton(
                onClick = { styleDialogState.visible = true },
                icon = Lucide.Palette,
                contentDescription = stringResource(Res.string.workspace_viewport_launchpad_actions_style),
                backgroundHoverColor = actionButtonBgHover,
            )
        }

        LaunchpadActionButton(
            onClick = {
                element.rotationDegrees.floatValue += 90f
                DeviceSyncCoordinator.onDeviceRotationChanged(element)
            },
            icon = Lucide.RotateCw,
            contentDescription = stringResource(Res.string.workspace_viewport_launchpad_actions_rotate),
            backgroundHoverColor = actionButtonBgHover,
        )

        LaunchpadActionButton(
            onClick = { deleteDialogState.visible = true },
            icon = Lucide.Trash2,
            contentDescription = stringResource(Res.string.workspace_viewport_launchpad_actions_delete),
            backgroundColor = deleteButtonBg,
            backgroundHoverColor = deleteButtonBgHover,
        )
    }

    Dialog(state = styleDialogState) {
        DialogContent {
            DialogHeader {
                DialogTitle(stringResource(Res.string.workspace_viewport_launchpad_actions_style))
            }
            element.StyleConfigContent(onDismiss = { styleDialogState.visible = false })
        }
    }

    AlertDialog(
        state = deleteDialogState,
    ) {
        AlertDialogHeader {
            AlertDialogTitle(stringResource(Res.string.workspace_viewport_launchpad_actions_delete_dialog_title))
            AlertDialogDescription("This will permanently remove \"${element.name}\" from the layout.")
        }

        AlertDialogFooter {
            AlertDialogCancel(
                onClick = { deleteDialogState.visible = false },
            ) {
                Text(stringResource(Res.string.workspace_viewport_launchpad_actions_delete_dialog_cancel))
            }

            AlertDialogAction(
                onClick = {
                    deleteDialogState.visible = false
                    SelectionManager.clear()
                    WorkspaceRepository.removeVirtualDevice(element.selectionUUID)
                },
                variant = ButtonVariant.Destructive,
            ) {
                Text(stringResource(Res.string.workspace_viewport_launchpad_actions_delete))
            }
        }
    }
}

@Composable
private fun LaunchpadActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    backgroundColor: Color = Color.Transparent,
    backgroundHoverColor: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    UnstyledButton(
        onClick = onClick,
        shape = CircleShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(0.dp),
        borderColor = Color.Unspecified,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (hovered) backgroundHoverColor else backgroundColor),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = Color.White,
        )
    }
}
