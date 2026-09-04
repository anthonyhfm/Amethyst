package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.workspace_exit_dialog_cancel
import amethyst.composeapp.generated.resources.workspace_exit_dialog_description
import amethyst.composeapp.generated.resources.workspace_exit_dialog_dont_save
import amethyst.composeapp.generated.resources.workspace_exit_dialog_save
import amethyst.composeapp.generated.resources.workspace_exit_dialog_title
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIViewController

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
actual fun ExitWorkspaceDialog(
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onCancel: () -> Unit,
) {
    val presenter = LocalUIViewController.current
    val currentOnSaveAndExit by rememberUpdatedState(onSaveAndExit)
    val currentOnDiscardAndExit by rememberUpdatedState(onDiscardAndExit)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val title = stringResource(Res.string.workspace_exit_dialog_title)
    val description = stringResource(Res.string.workspace_exit_dialog_description)
    val saveLabel = stringResource(Res.string.workspace_exit_dialog_save)
    val dontSaveLabel = stringResource(Res.string.workspace_exit_dialog_dont_save)
    val cancelLabel = stringResource(Res.string.workspace_exit_dialog_cancel)

    DisposableEffect(presenter, title, description, saveLabel, dontSaveLabel, cancelLabel) {
        var actionHandled = false
        val alertController = UIAlertController.alertControllerWithTitle(
            title = title,
            message = description,
            preferredStyle = UIAlertControllerStyleAlert,
        )

        val saveAction = UIAlertAction.actionWithTitle(
            title = saveLabel,
            style = UIAlertActionStyleDefault,
        ) {
            actionHandled = true
            currentOnSaveAndExit()
        }
        alertController.addAction(saveAction)
        alertController.preferredAction = saveAction
        alertController.addAction(
            UIAlertAction.actionWithTitle(
                title = dontSaveLabel,
                style = UIAlertActionStyleDestructive,
            ) {
                actionHandled = true
                currentOnDiscardAndExit()
            },
        )
        alertController.addAction(
            UIAlertAction.actionWithTitle(
                title = cancelLabel,
                style = UIAlertActionStyleCancel,
            ) {
                actionHandled = true
                currentOnCancel()
            },
        )

        presenter.topPresentedViewController().presentViewController(
            viewControllerToPresent = alertController,
            animated = true,
            completion = null,
        )

        onDispose {
            if (!actionHandled && alertController.presentingViewController != null) {
                alertController.dismissViewControllerAnimated(flag = true, completion = null)
            }
        }
    }
}

private fun UIViewController.topPresentedViewController(): UIViewController {
    var controller = this
    while (controller.presentedViewController != null) {
        controller = controller.presentedViewController!!
    }
    return controller
}
