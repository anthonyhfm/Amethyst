package dev.anthonyhfm.amethyst.start

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_linux
import amethyst.composeapp.generated.resources.amethyst_windows
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.anthonyhfm.amethyst.home.Home
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.FlatUtilityLaf
import dev.anthonyhfm.amethyst.desktop.OSXTitleBar
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.home.HomeCommandSurface
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import org.jetbrains.compose.resources.painterResource
import javax.swing.UIManager
import kotlin.system.exitProcess
import dev.anthonyhfm.amethyst.core.util.amethystVersion
import dev.anthonyhfm.amethyst.core.util.displayString
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.*

@Composable
fun StartWindow(
    onOpenEditor: () -> Unit,
) {
    if (DesktopPlatform.get() == DesktopPlatform.Windows) {
        UIManager.setLookAndFeel(FlatUtilityLaf())
    }

    Window(
        onCloseRequest = {
            exitProcess(0)
        },
        title = "Amethyst",
        state = rememberWindowState(
            width = 750.dp,
            height = 550.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        icon = when (DesktopPlatform.get()) {
            DesktopPlatform.Windows -> painterResource(Res.drawable.amethyst_windows)
            DesktopPlatform.Linux -> painterResource(Res.drawable.amethyst_linux)

            else -> null
        },
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed)) {
                when (event.key) {
                    Key.N -> { HomeCommandSurface.emit(HomeCommandSurface.HomeCommand.NewProject); true }
                    Key.O -> { HomeCommandSurface.emit(HomeCommandSurface.HomeCommand.OpenProject); true }
                    else -> false
                }
            } else false
        }
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = java.awt.Dimension(750, 550)
        }

        if (DesktopPlatform.get() == DesktopPlatform.MacOS) {
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        }

        AmethystTheme {
            Column {
                if (DesktopPlatform.get() == DesktopPlatform.MacOS) {
                    OSXTitleBar()
                }

                Home(
                    onOpenWorkspace = {
                        onOpenEditor()
                    }
                )
            }
        }
    }
}