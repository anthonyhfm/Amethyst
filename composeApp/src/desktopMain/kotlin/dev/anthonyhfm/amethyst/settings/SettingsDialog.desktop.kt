package dev.anthonyhfm.amethyst.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.FlatUtilityLaf
import dev.anthonyhfm.amethyst.desktop.OSXTitleBar
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
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
            width = 550.dp,
            height = 600.dp,
        ),
        resizable = false
    ) {
        if(DesktopPlatform.get() == DesktopPlatform.MacOS) {
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        }

        AmethystTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Theme[colors][background]),
            ) {
                if (DesktopPlatform.get() == DesktopPlatform.MacOS) {
                    OSXTitleBar()
                }

                Settings()
            }
        }
    }
}