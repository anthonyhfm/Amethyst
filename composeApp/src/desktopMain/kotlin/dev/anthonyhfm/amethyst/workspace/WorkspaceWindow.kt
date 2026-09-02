package dev.anthonyhfm.amethyst.workspace

import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_linux
import amethyst.composeapp.generated.resources.amethyst_windows
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
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState.updateFromKeyEvent
import dev.anthonyhfm.amethyst.core.controls.shortcuts.ShortcutManager
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.settings.AppLocaleProvider
import dev.anthonyhfm.amethyst.settings.AppLocaleRefreshBoundary
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.workspace.ui.SaveChangesDialog
import dev.anthonyhfm.amethyst.workspace.ui.WorkspaceMenuBar
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceSaveHelper
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode

@Composable
fun WorkspaceWindow(
    onClose: () -> Unit = { },
    externalCloseRequest: Int = 0,
    onExternalCloseConfirmed: () -> Unit = onClose,
    onExternalCloseCancelled: () -> Unit = { }
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingCloseAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCancelAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val projectName by WorkspaceRepository.projectName.collectAsState()
    val windowTitle = "Amethyst - [${projectName ?: stringResource(Res.string.workspace_window_untitled_project)}]"

    fun closeWorkspace(afterClose: () -> Unit) {
        Echo.stopAll()
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

    DecoratedWindow(
        onCloseRequest = {
            requestWorkspaceClose(afterClose = onClose)
        },
        title = windowTitle,
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        minimumSize = DpSize(750.dp, 550.dp),
        onKeyEvent = {
            // Mode-specific handlers have priority over global shortcuts.
            if (WorkspaceRepository.mode.value.onKeyEvent(it)) return@DecoratedWindow true
            ShortcutManager.handleShortcut(it)
        },
        onPreviewKeyEvent = {
            // Keep modifier state current even when a focused editor consumes the event.
            updateFromKeyEvent(it)

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
                return@DecoratedWindow true
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
        WindowAppearance(WindowAppearanceMode.Dark)

        TitleBar {
            Text(
                text = windowTitle,
                color = Theme[colors][foreground].copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        WorkspaceMenuBar(
            onRequestClose = { requestWorkspaceClose(afterClose = onClose) },
        )

        AppLocaleProvider {
            AppLocaleRefreshBoundary {
                Workspace()
            }

            // Keep the dialog in the same theme tree as the workspace so it matches the catalog styling.
            if (showSaveDialog) {
                AppLocaleRefreshBoundary {
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
}
