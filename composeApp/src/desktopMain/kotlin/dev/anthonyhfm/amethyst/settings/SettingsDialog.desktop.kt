package dev.anthonyhfm.amethyst.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import dev.anthonyhfm.amethyst.desktop.utility.DesktopUtilityWindowScaffold
import dev.anthonyhfm.amethyst.desktop.utility.applyDesktopUtilityLaf
import dev.anthonyhfm.amethyst.desktop.utility.applyMacUtilityWindowChrome
import dev.anthonyhfm.amethyst.desktop.utility.configureDesktopUtilityDialog
import dev.anthonyhfm.amethyst.desktop.utility.CenterWindowOnFirstShow
import java.awt.Desktop
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

private var settingsDialog: ComposeDialog? = null

fun showSettingsWindow() {
    settingsDialog
        ?.takeIf { it.isDisplayable }
        ?.let { dialog ->
            dialog.toFront()
            dialog.requestFocus()
            return
        }

    val dialog = ComposeDialog()
    configureDesktopUtilityDialog(
        dialog = dialog,
        title = "Settings",
        width = 550,
        height = 600,
        resizable = false
    )
    dialog.addWindowListener(object : WindowAdapter() {
        override fun windowClosed(event: WindowEvent?) {
            if (settingsDialog === dialog) {
                settingsDialog = null
            }
        }
    })
    dialog.setContent {
        AppLocaleProvider {
            DesktopUtilityWindowScaffold {
                AppLocaleRefreshBoundary {
                    Settings()
                }
            }
        }
    }

    settingsDialog = dialog
    dialog.show()
}

fun setupPreferencesHandler() {
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()

        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler {
                showSettingsWindow()
            }
        }
    }
}

@Composable
actual fun SettingsDialog(visible: Boolean, onDismiss: () -> Unit) {
    applyDesktopUtilityLaf()

    DialogWindow(
        visible = visible,
        onCloseRequest = {
            onDismiss()
        },
        title = "Settings",
        state = rememberDialogState(
            width = 550.dp,
            height = 600.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        resizable = false
    ) {
        CenterWindowOnFirstShow(window)

        applyMacUtilityWindowChrome(window.rootPane)

        AppLocaleProvider {
            DesktopUtilityWindowScaffold {
                AppLocaleRefreshBoundary {
                    Settings()
                }
            }
        }
    }
}
