package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncCoordinator
import dev.anthonyhfm.amethyst.ui.components.primitives.Dialog
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogContent
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogTitle
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import platform.UIKit.*

private const val ActionButtonSize = 44.0
private const val ActionButtonSpacing = 0.0

@OptIn(ExperimentalComposeUiApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
actual fun LaunchpadViewportElementActions(
    element: LaunchpadViewportElement,
    modifier: Modifier,
) {
    val styleDialogState = rememberDialogState()
    val buttonCount = remember(element.hasStyleOptions) {
        3 + if (element.hasStyleOptions) 1 else 0
    }
    val width = buttonCount * ActionButtonSize + (buttonCount - 1) * ActionButtonSpacing

    val connectionLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_connection_ios)
    val styleTitleLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_style_dialog_title_ios)
    val rotateLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_rotate_ios)
    val deleteLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_delete_dialog_delete_ios)
    val deleteTitleLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_delete_dialog_title_ios)
    val cancelLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_delete_dialog_cancel_ios)

    UIKitView(
        factory = {
            UIToolbar().apply {
                backgroundColor = UIColor.clearColor
                translucent = true
                setBackgroundImage(UIImage(), forToolbarPosition = UIBarPositionAny, barMetrics = UIBarMetricsDefault)
                setShadowImage(UIImage(), forToolbarPosition = UIBarPositionAny)
            }
        },
        modifier = modifier.size(width.dp, ActionButtonSize.dp),
        update = { toolbar ->
            toolbar.rebuildLaunchpadActions(
                element = element,
                connectionLabel = connectionLabel,
                styleTitleLabel = styleTitleLabel,
                rotateLabel = rotateLabel,
                deleteLabel = deleteLabel,
                deleteTitleLabel = deleteTitleLabel,
                cancelLabel = cancelLabel,
                onShowStyle = { styleDialogState.visible = true },
                onShowDelete = {
                    toolbar.presentDeleteAlert(element, deleteTitleLabel, cancelLabel, deleteLabel)
                },
            )
        },
        properties = UIKitInteropProperties(
            placedAsOverlay = true,
        ),
    )

    Dialog(state = styleDialogState) {
        DialogContent {
            DialogHeader {
                DialogTitle(styleTitleLabel)
            }
            element.StyleConfigContent(onDismiss = { styleDialogState.visible = false })
        }
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun UIToolbar.rebuildLaunchpadActions(
    element: LaunchpadViewportElement,
    connectionLabel: String,
    styleTitleLabel: String,
    rotateLabel: String,
    deleteLabel: String,
    deleteTitleLabel: String,
    cancelLabel: String,
    onShowStyle: () -> Unit,
    onShowDelete: () -> Unit,
) {
    val items = buildList {
        add(
            actionItem(
                systemImageName = "cable.connector",
                accessibilityLabel = connectionLabel,
                tintColor = UIColor.labelColor,
                onClick = {
                    element.onEvent?.invoke(WorkspaceContract.Event.OnClickDeviceConfigure(element.selectionUUID))
                },
            ),
        )

        if (element.hasStyleOptions) {
            add(
                actionItem(
                    systemImageName = "paintpalette",
                    accessibilityLabel = styleTitleLabel,
                    tintColor = UIColor.labelColor,
                    onClick = onShowStyle,
                ),
            )
        }

        add(
            actionItem(
                systemImageName = "rotate.right",
                accessibilityLabel = rotateLabel,
                tintColor = UIColor.labelColor,
                onClick = {
                    element.rotationDegrees.floatValue += 90f
                    DeviceSyncCoordinator.onDeviceRotationChanged(element)
                },
            ),
        )

        add(
            actionItem(
                systemImageName = "trash",
                accessibilityLabel = deleteLabel,
                tintColor = UIColor.systemRedColor,
                onClick = onShowDelete,
            ),
        )
    }

    setItems(items, animated = false)
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun UIView.presentDeleteAlert(
    element: LaunchpadViewportElement,
    deleteTitleLabel: String,
    cancelLabel: String,
    deleteLabel: String,
) {
    val presenter = nearestViewController() ?: return
    if (presenter.presentedViewController is UIAlertController) return

    val alertController = UIAlertController.alertControllerWithTitle(
        title = deleteTitleLabel,
        message = "This will permanently remove \"${element.name}\" from the layout.",
        preferredStyle = UIAlertControllerStyleAlert,
    )
    alertController.addAction(
        UIAlertAction.actionWithTitle(
            title = cancelLabel,
            style = UIAlertActionStyleCancel,
            handler = null,
        ),
    )
    alertController.addAction(
        UIAlertAction.actionWithTitle(
            title = deleteLabel,
            style = UIAlertActionStyleDestructive,
        ) {
            element.onEvent?.invoke(WorkspaceContract.Event.OnDeleteDevice(element.selectionUUID))
        },
    )
    presenter.presentViewController(alertController, animated = true, completion = null)
}

private fun UIView.nearestViewController(): UIViewController? {
    var responder: UIResponder? = this
    while (responder != null) {
        if (responder is UIViewController) return responder
        responder = responder.nextResponder
    }
    return window?.rootViewController?.topPresentedViewController()
}

private fun UIViewController.topPresentedViewController(): UIViewController {
    var controller = this
    while (controller.presentedViewController != null) {
        controller = controller.presentedViewController!!
    }
    return controller
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun actionItem(
    systemImageName: String,
    accessibilityLabel: String,
    tintColor: UIColor,
    onClick: () -> Unit,
): UIBarButtonItem {
    val item = UIBarButtonItem(
        image = UIImage.systemImageNamed(systemImageName),
        style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
        target = null,
        action = null,
    )
    item.primaryAction = UIAction.actionWithHandler {
        onClick()
    }
    item.accessibilityLabel = accessibilityLabel
    item.tintColor = tintColor
    item.width = ActionButtonSize
    return item
}
