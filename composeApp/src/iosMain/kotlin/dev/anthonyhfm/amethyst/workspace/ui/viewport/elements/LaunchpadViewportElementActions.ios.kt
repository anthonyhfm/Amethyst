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
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.ui.components.IosWorkspaceBridge
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.UIKit.*

private const val ActionButtonSize = 44.0

@OptIn(ExperimentalComposeUiApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
actual fun LaunchpadViewportElementActions(
    element: LaunchpadViewportElement,
    modifier: Modifier,
) {
    val styleDialogState = rememberDialogState()
    val buttonCount = remember(element.hasStyleOptions) {
        4 + if (element.hasStyleOptions) 1 else 0
    }
    val width = buttonCount * ActionButtonSize

    val swapLabel = stringResource(resource = Res.string.workspace_viewport_launchpad_actions_swap)
    val connectionLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_connection_ios)
    val styleTitleLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_style_dialog_title_ios)
    val rotateLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_rotate_ios)
    val deleteLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_delete_dialog_delete_ios)
    val deleteTitleLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_delete_dialog_title_ios)
    val cancelLabel = stringResource(Res.string.workspace_viewport_launchpad_actions_delete_dialog_cancel_ios)

    UIKitView(
        factory = {
            UIVisualEffectView(
                effect = IosWorkspaceBridge.createLiquidGlassEffect?.invoke()
            ).apply {
                clipsToBounds = true
                layer.cornerRadius = 22.0

                val stack = UIStackView()
                stack.axis = 0
                stack.distribution = UIStackViewDistributionFillEqually
                stack.translatesAutoresizingMaskIntoConstraints = false
                contentView.addSubview(stack)
                NSLayoutConstraint.activateConstraints(
                    listOf(
                        stack.leftAnchor.constraintEqualToAnchor(contentView.leftAnchor),
                        stack.rightAnchor.constraintEqualToAnchor(contentView.rightAnchor),
                        stack.topAnchor.constraintEqualToAnchor(contentView.topAnchor),
                        stack.bottomAnchor.constraintEqualToAnchor(contentView.bottomAnchor),
                    ),
                )
            }
        },
        modifier = modifier.size(width.dp, ActionButtonSize.dp),
        update = { actionView ->
            actionView.rebuildLaunchpadActions(
                element = element,
                connectionLabel = connectionLabel,
                swapLabel = swapLabel,
                styleTitleLabel = styleTitleLabel,
                rotateLabel = rotateLabel,
                deleteLabel = deleteLabel,
                deleteTitleLabel = deleteTitleLabel,
                cancelLabel = cancelLabel,
                onShowStyle = {
                    IosWorkspaceBridge.onShowDeviceStyle?.invoke(element.selectionUUID)
                        ?: run { styleDialogState.visible = true }
                },
                onShowDelete = {
                    actionView.presentDeleteAlert(element, deleteTitleLabel, cancelLabel, deleteLabel)
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
private fun UIVisualEffectView.rebuildLaunchpadActions(
    element: LaunchpadViewportElement,
    connectionLabel: String,
    swapLabel: String,
    styleTitleLabel: String,
    rotateLabel: String,
    deleteLabel: String,
    deleteTitleLabel: String,
    cancelLabel: String,
    onShowStyle: () -> Unit,
    onShowDelete: () -> Unit,
) {
    val stack = contentView.subviews.firstOrNull() as? UIStackView ?: return
    stack.arrangedSubviews.filterIsInstance<UIView>().forEach { button ->
        stack.removeArrangedSubview(button)
        button.removeFromSuperview()
    }

    val buttons = buildList {
        add(
            actionButton(
                systemImageName = "arrow.left.arrow.right",
                accessibilityLabel = swapLabel,
                tintColor = UIColor.labelColor,
                onClick = {
                    WorkspaceRepository.openDevicePicker(replacingDeviceId = element.launchpadId)
                    IosWorkspaceBridge.onShowDevicePicker?.invoke()
                },
            ),
        )

        add(
            actionButton(
                systemImageName = "cable.connector",
                accessibilityLabel = connectionLabel,
                tintColor = UIColor.labelColor,
                onClick = {
                    IosWorkspaceBridge.onShowDeviceConfigurator?.invoke(element.selectionUUID)
                        ?: WorkspaceRepository.openDeviceConfigurator(element.selectionUUID)
                },
            ),
        )

        if (element.hasStyleOptions) {
            add(
                actionButton(
                    systemImageName = "paintpalette",
                    accessibilityLabel = styleTitleLabel,
                    tintColor = UIColor.labelColor,
                    onClick = onShowStyle,
                ),
            )
        }

        add(
            actionButton(
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
            actionButton(
                systemImageName = "trash",
                accessibilityLabel = deleteLabel,
                tintColor = UIColor.systemRedColor,
                onClick = onShowDelete,
            ),
        )
    }

    buttons.forEach { button -> stack.addArrangedSubview(button) }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun UIView.presentDeleteAlert(
    element: LaunchpadViewportElement,
    deleteTitleLabel: String,
    cancelLabel: String,
    deleteLabel: String,
) {
    val presenter = window?.rootViewController?.topPresentedViewController()
        ?: nearestViewController()
        ?: return
    if (presenter is UIAlertController) {
        return
    }

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
            SelectionManager.clear()
            CoroutineScope(context = Dispatchers.Main).launch {
                WorkspaceRepository.removeVirtualDeviceById(uuid = element.selectionUUID)
            }
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
private fun actionButton(
    systemImageName: String,
    accessibilityLabel: String,
    tintColor: UIColor,
    onClick: () -> Unit,
): UIButton {
    return UIButton.buttonWithType(UIButtonTypeSystem).apply {
        configuration = UIButtonConfiguration.plainButtonConfiguration().apply {
            image = UIImage.systemImageNamed(systemImageName)
            baseForegroundColor = tintColor
        }
        setAccessibilityLabel(accessibilityLabel)
        addAction(
            UIAction.actionWithHandler { onClick() },
            forControlEvents = UIControlEventTouchUpInside,
        )
    }
}
