package dev.anthonyhfm.amethyst.workspace

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_linux
import amethyst.composeapp.generated.resources.amethyst_macos
import amethyst.composeapp.generated.resources.amethyst_windows
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import dev.anthonyhfm.amethyst.core.audio.AudioPlayer
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState.updateFromKeyEvent
import dev.anthonyhfm.amethyst.core.controls.shortcuts.ShortcutManager
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.FlatAmethystLaf
import dev.anthonyhfm.amethyst.desktop.OSXTitleBar
import dev.anthonyhfm.amethyst.workspace.ui.WorkspaceMenuBar
import org.jetbrains.compose.resources.painterResource
import javax.swing.UIManager
import kotlin.system.exitProcess

@Composable
fun WorkspaceWindow() {
    if (DesktopPlatform.get() == DesktopPlatform.Windows) {
        UIManager.setLookAndFeel(FlatAmethystLaf())
    }

    Window(
        onCloseRequest = {
            AudioPlayer.cleanup()

            exitProcess(0)
        },
        title = "Amethyst",
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp
        ),
        onKeyEvent = {
            updateFromKeyEvent(it)

            // Prioritize mode events over shortcuts
            val modeEvent = WorkspaceRepository.mode.value.onKeyEvent(it)

            if (!modeEvent) {
                ShortcutManager.handleShortcut(it)
            }

            modeEvent
        },
        icon = when (DesktopPlatform.get()) {
            DesktopPlatform.Windows -> painterResource(Res.drawable.amethyst_windows)
            DesktopPlatform.Linux -> painterResource(Res.drawable.amethyst_linux)

            else -> null
        }
    ) {
        WorkspaceMenuBar()

        LaunchedEffect(Unit) {
            if(DesktopPlatform.get() == DesktopPlatform.MacOS) {
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            }
        }

        MaterialTheme(
            colorScheme = darkColorScheme()
        ) {
            Column {
                if (DesktopPlatform.get() == DesktopPlatform.MacOS) {
                    OSXTitleBar()
                }

                Workspace()
            }
        }
    }
}