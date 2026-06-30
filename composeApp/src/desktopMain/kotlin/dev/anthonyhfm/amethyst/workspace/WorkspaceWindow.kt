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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState.updateFromKeyEvent
import dev.anthonyhfm.amethyst.core.controls.shortcuts.ShortcutManager
import dev.anthonyhfm.amethyst.core.engine.echo.AudioOutput
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.FlatAmethystLaf
import dev.anthonyhfm.amethyst.desktop.OSXTitleBar
import dev.anthonyhfm.amethyst.desktop.utility.CenterWindowOnFirstShow
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
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
    onClose: () -> Unit = { },
    externalCloseRequest: Int = 0,
    onExternalCloseConfirmed: () -> Unit = onClose,
    onExternalCloseCancelled: () -> Unit = { }
) {
    if (DesktopPlatform.get() == DesktopPlatform.Windows) {
        UIManager.setLookAndFeel(FlatAmethystLaf())
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingCloseAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCancelAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val projectName by WorkspaceRepository.projectName.collectAsState()
    val windowTitle = "Amethyst - [${projectName ?: "Untitled Project"}]"

    fun closeWorkspace(afterClose: () -> Unit) {
        AudioOutput.stopAll()
        WorkspaceRepository.clean()
        afterClose()
    }

    fun requestWorkspaceClose(
        afterClose: () -> Unit,
        afterCancel: () -> Unit = { }
    ) {
        if (WorkspaceRepository.hasUnsavedChanges()) {
            pendingCloseAction = afterClose
            pendingCancelAction = afterCancel
            showSaveDialog = true
        } else {
            closeWorkspace(afterClose)
        }
    }

    LaunchedEffect(externalCloseRequest) {
        if (externalCloseRequest > 0) {
            requestWorkspaceClose(
                afterClose = onExternalCloseConfirmed,
                afterCancel = onExternalCloseCancelled
            )
        }
    }

    Window(
        onCloseRequest = {
            requestWorkspaceClose(afterClose = onClose)
        },
        title = windowTitle,
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        onKeyEvent = {
            updateFromKeyEvent(it)

            // Mode-specific handlers have priority over global shortcuts.
            if (WorkspaceRepository.mode.value.onKeyEvent(it)) return@Window true
            ShortcutManager.handleShortcut(it)
        },
        onPreviewKeyEvent = {
            if (
                it.type == KeyEventType.KeyDown &&
                (it.isCtrlPressed || it.isMetaPressed) &&
                it.key == Key.S
            ) {
                coroutineScope.launch {
                    if (it.isShiftPressed) {
                        WorkspaceSaveHelper.saveWorkspaceAs()
                    } else {
                        WorkspaceSaveHelper.saveWorkspace()
                    }
                }
                return@Window true
            }

            val mode = WorkspaceRepository.mode.value
            when {
                mode is TimelineWorkspaceMode || mode is PianoRollWorkspaceMode -> {
                    mode.onKeyEvent(it)
                }

                (mode is KeyframesWorkspaceMode || mode is CoordinateFilterWorkspaceMode) &&
                    (it.key == Key.Escape || ((it.isCtrlPressed || it.isMetaPressed) && it.key == Key.W)) -> {
                    mode.onKeyEvent(it)
                }

                else -> false
            }
        },
        icon = when (DesktopPlatform.get()) {
            DesktopPlatform.Windows -> painterResource(Res.drawable.amethyst_windows)
            DesktopPlatform.Linux -> painterResource(Res.drawable.amethyst_linux)

            else -> null
        }
    ) {
        CenterWindowOnFirstShow(window)

        WorkspaceMenuBar()

        LaunchedEffect(Unit) {
            window.minimumSize = java.awt.Dimension(750, 550)
        }

        LaunchedEffect(Unit) {
            window.minimumSize = java.awt.Dimension(1000, 700)
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
                            if (saved) {
                                val closeAction = pendingCloseAction
                                showSaveDialog = false
                                pendingCloseAction = null
                                pendingCancelAction = null
                                closeAction?.let(::closeWorkspace)
                            }
                        }
                    },
                    onDontSave = {
                        val closeAction = pendingCloseAction
                        showSaveDialog = false
                        pendingCloseAction = null
                        pendingCancelAction = null
                        closeAction?.let(::closeWorkspace)
                    },
                    onCancel = {
                        val cancelAction = pendingCancelAction
                        showSaveDialog = false
                        pendingCloseAction = null
                        pendingCancelAction = null
                        cancelAction?.invoke()
                    }
                )
            }
        }
    }
}
