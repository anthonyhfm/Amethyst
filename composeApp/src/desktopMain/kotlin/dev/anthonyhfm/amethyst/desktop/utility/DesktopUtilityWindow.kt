package dev.anthonyhfm.amethyst.desktop.utility

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeDialog
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.FlatUtilityLaf
import dev.anthonyhfm.amethyst.desktop.OSXTitleBar
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import javax.swing.JRootPane
import javax.swing.UIManager

fun applyDesktopUtilityLaf() {
    if (DesktopPlatform.get() == DesktopPlatform.Windows) {
        UIManager.setLookAndFeel(FlatUtilityLaf())
    }
}

fun applyMacUtilityWindowChrome(rootPane: JRootPane) {
    if (DesktopPlatform.get() == DesktopPlatform.MacOS) {
        rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    }
}

fun configureDesktopUtilityDialog(
    dialog: ComposeDialog,
    title: String,
    width: Int,
    height: Int,
    resizable: Boolean = false,
) {
    applyDesktopUtilityLaf()
    dialog.title = title
    dialog.setSize(width, height)
    dialog.isResizable = resizable
    dialog.setLocationRelativeTo(null)
    applyMacUtilityWindowChrome(dialog.rootPane)
}

@Composable
fun DesktopUtilityWindowScaffold(
    content: @Composable () -> Unit
) {
    AmethystTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme[colors][background])
        ) {
            if (DesktopPlatform.get() == DesktopPlatform.MacOS) {
                OSXTitleBar()
            }

            content()
        }
    }
}
