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
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState.updateFromKeyEvent
import dev.anthonyhfm.amethyst.core.controls.shortcuts.ShortcutManager
import dev.anthonyhfm.amethyst.core.engine.echo.AudioOutput
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.FlatAmethystLaf
import dev.anthonyhfm.amethyst.desktop.OSXTitleBar
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.workspace.ui.SaveChangesDialog
import dev.anthonyhfm.amethyst.workspace.ui.WorkspaceMenuBar
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceSaveHelper
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import javax.swing.UIManager
import kotlin.system.exitProcess

@Composable
fun WorkspaceWindow(
    onClose: () -> Unit = { }
) {
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
                WorkspaceRepository.clean()
                onClose()
            }
        },
        title = "Amethyst - [${WorkspaceRepository.workspaceMeta?.title ?: "Untitled Project"}]",
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp
        ),
        onKeyEvent = {
            updateFromKeyEvent(it)
            AutomappingManager.handleKeyEvent(it)

            // Mode-specific handlers have priority over global shortcuts.
            if (WorkspaceRepository.mode.value.onKeyEvent(it)) return@Window true
            ShortcutManager.handleShortcut(it)
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

        AmethystTheme {
            Column {
                if (DesktopPlatform.get() == DesktopPlatform.MacOS) {
                    OSXTitleBar()
                }

                Workspace()
            }

            // Keep the dialog in the same theme tree as the workspace so it matches the catalog styling.
            if (showSaveDialog) {
                SaveChangesDialog(
                    onSave = {
                        coroutineScope.launch {
                            val saved = WorkspaceSaveHelper.saveWorkspace()
                            if (saved && pendingClose) {
                                WorkspaceRepository.clean()
                                onClose()
                            }
                            showSaveDialog = false
                            pendingClose = false
                        }
                    },
                    onDontSave = {
                        showSaveDialog = false
                        if (pendingClose) {
                            WorkspaceRepository.clean()
                            onClose()
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
}
