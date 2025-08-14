package dev.anthonyhfm.amethyst.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.FlatUtilityLaf
import dev.anthonyhfm.amethyst.desktop.OSXTitleBar
import javax.swing.UIManager

@Composable
actual fun SettingsDialog(visible: Boolean, onDismiss: () -> Unit) {
    if (DesktopPlatform.get() == DesktopPlatform.Windows) {
        UIManager.setLookAndFeel(FlatUtilityLaf())
    }

    DialogWindow(
        visible = visible,
        onCloseRequest = {
            onDismiss()
        },
        title = "Settings",
        state = rememberDialogState(
            width = 400.dp,
            height = 450.dp
        )
    ) {
        if(DesktopPlatform.get() == DesktopPlatform.MacOS) {
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        }

        Column {
            if (DesktopPlatform.get() == DesktopPlatform.MacOS) {
                OSXTitleBar()
            }

            Settings()
        }
    }
}