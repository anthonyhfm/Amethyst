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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState.updateFromKeyEvent
import dev.anthonyhfm.amethyst.core.controls.shortcuts.ShortcutManager
import dev.anthonyhfm.amethyst.core.engine.echo.AudioOutput
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.FlatAmethystLaf
import dev.anthonyhfm.amethyst.desktop.OSXTitleBar
import dev.anthonyhfm.amethyst.workspace.ui.SaveChangesDialog
import dev.anthonyhfm.amethyst.workspace.ui.WorkspaceMenuBar
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceSaveHelper
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import javax.swing.UIManager
import kotlin.system.exitProcess

@Composable
fun WorkspaceWindow() {
    if (DesktopPlatform.get() == DesktopPlatform.Windows) {
        UIManager.setLookAndFeel(FlatAmethystLaf())
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingClose by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Window(
        onCloseRequest = {
            // Check if there are unsaved changes
            if (WorkspaceRepository.hasUnsavedChanges()) {
                showSaveDialog = true
                pendingClose = true
            } else {
                // AudioOutput.cleanup()
                exitProcess(0)
            }
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

        // Show save changes dialog if needed
        if (showSaveDialog) {
            SaveChangesDialog(
                onSave = {
                    coroutineScope.launch {
                        val saved = WorkspaceSaveHelper.saveWorkspace()
                        if (saved && pendingClose) {
                            // AudioOutput.cleanup()
                            exitProcess(0)
                        }
                        showSaveDialog = false
                        pendingClose = false
                    }
                },
                onDontSave = {
                    showSaveDialog = false
                    if (pendingClose) {
                        // AudioOutput.cleanup()
                        exitProcess(0)
                    }
                    pendingClose = false
                },
                onCancel = {
                    showSaveDialog = false
                    pendingClose = false
                }
            )
        }
    }
}