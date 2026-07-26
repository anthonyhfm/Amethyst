package dev.anthonyhfm.amethyst.workspace.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.util.primaryKeyShortcut
import dev.anthonyhfm.amethyst.core.util.redoKeyShortcut
import dev.anthonyhfm.amethyst.desktop.about.showAboutDialog
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.settings.showSettingsWindow
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import dev.anthonyhfm.amethyst.timeline.contract.TimelineEditorTool
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.workspace.WorkspaceMenuCommandSurface
import dev.anthonyhfm.amethyst.workspace.WorkspacePrimaryMode
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LayoutWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.PerformanceWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LightsChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceSaveHelper
import kotlinx.coroutines.launch
import java.awt.Frame
import java.awt.event.WindowEvent

@Composable
fun FrameWindowScope.WorkspaceMenuBar() {
    val viewModel = viewModel { WorkspaceMenuBarViewModel() }
    val coroutineScope = rememberCoroutineScope()

    val mode by WorkspaceRepository.mode.collectAsState()
    val selections by SelectionManager.selections.collectAsState()
    val clipboard by ClipboardManager.clipboardData.collectAsState()
    val undoState by UndoManager.state.collectAsState()
    val recentProjects by viewModel.recentProjects.collectAsState()
    val timelineTracks by TimelineRepository.tracks.collectAsState()
    val isTimelinePlaying by TimelineRepository.isPlaying.collectAsState()

    val editState = remember(mode, selections, clipboard, undoState) {
        WorkspaceMenuCommandSurface.editState(
            mode = mode,
            selections = selections,
            clipboard = clipboard
        )
    }
    val primaryMode = remember(mode) {
        WorkspaceMenuCommandSurface.currentPrimaryMode(mode)
    }
    val keyframesMode = mode as? KeyframesWorkspaceMode
    val pianoRollMode = mode as? PianoRollWorkspaceMode

    var showProjectChangeDialog by remember { mutableStateOf(false) }
    var pendingProjectChangeAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun requestProjectChange(action: () -> Unit) {
        if (WorkspaceRepository.hasUnsavedChanges()) {
            pendingProjectChangeAction = action
            showProjectChangeDialog = true
        } else {
            action()
        }
    }

    MenuBar {
        Menu(text = stringResource(Res.string.workspace_menubar_file)) {
            Item(
                text = "Open Project...",
                shortcut = primaryKeyShortcut(Key.O),
                onClick = {
                    requestProjectChange {
                        viewModel.openProject()
                    }
                }
            )

            Menu(text = "Open Recent") {
                if (recentProjects.isEmpty()) {
                    Item(
                        text = stringResource(Res.string.home_projects_empty_title),
                        enabled = false,
                        onClick = {}
                    )
                } else {
                    recentProjects.take(12).forEach { project ->
                        Item(
                            text = project.title.ifBlank { project.path.substringAfterLast('/') },
                            onClick = {
                                requestProjectChange {
                                    viewModel.openRecentProject(project)
                                }
                            }
                        )
                    }
                }
            }

            Separator()

            Item(
                text = stringResource(Res.string.workspace_menubar_file_save),
                shortcut = primaryKeyShortcut(Key.S),
                onClick = {
                    viewModel.saveProject()
                }
            )

            Item(
                text = stringResource(Res.string.workspace_menubar_file_save_as),
                shortcut = primaryKeyShortcut(Key.S, shift = true),
                onClick = {
                    viewModel.saveProjectAs()
                }
            )

            Separator()

            Item(
                text = "Close Project",
                shortcut = primaryKeyShortcut(Key.W, shift = true),
                onClick = {
                    window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
                }
            )
        }

        Menu(text = stringResource(Res.string.workspace_menubar_edit)) {
            Item(
                text = "Undo",
                enabled = editState.canUndo,
                shortcut = primaryKeyShortcut(Key.Z),
                onClick = {
                    WorkspaceMenuCommandSurface.undo()
                }
            )

            Item(
                text = stringResource(Res.string.workspace_menubar_edit_redo),
                enabled = editState.canRedo,
                shortcut = redoKeyShortcut(),
                onClick = {
                    WorkspaceMenuCommandSurface.redo()
                }
            )

            Separator()

            Item(
                text = stringResource(Res.string.workspace_menubar_edit_cut),
                enabled = editState.canCut,
                shortcut = primaryKeyShortcut(Key.X),
                onClick = {
                    WorkspaceMenuCommandSurface.cut()
                }
            )

            Item(
                text = stringResource(Res.string.workspace_menubar_edit_copy),
                enabled = editState.canCopy,
                shortcut = primaryKeyShortcut(Key.C),
                onClick = {
                    WorkspaceMenuCommandSurface.copy()
                }
            )

            Item(
                text = stringResource(Res.string.workspace_menubar_edit_paste),
                enabled = editState.canPaste,
                shortcut = primaryKeyShortcut(Key.V),
                onClick = {
                    WorkspaceMenuCommandSurface.paste()
                }
            )

            Separator()

            Item(
                text = stringResource(Res.string.workspace_menubar_edit_delete),
                enabled = editState.canDelete,
                onClick = {
                    WorkspaceMenuCommandSurface.delete()
                }
            )

            Item(
                text = stringResource(Res.string.workspace_menubar_edit_duplicate),
                enabled = editState.canDuplicate,
                shortcut = primaryKeyShortcut(Key.D),
                onClick = {
                    WorkspaceMenuCommandSurface.duplicate()
                }
            )

            Item(
                text = stringResource(Res.string.device_group_editor_rename),
                enabled = editState.canRename,
                shortcut = primaryKeyShortcut(Key.R),
                onClick = {
                    WorkspaceMenuCommandSurface.rename()
                }
            )

            Separator()

            Item(
                text = stringResource(Res.string.workspace_menubar_edit_select_all),
                enabled = editState.canSelectAll,
                shortcut = primaryKeyShortcut(Key.A),
                onClick = {
                    WorkspaceMenuCommandSurface.selectAll()
                }
            )
        }

        Menu(text = stringResource(Res.string.workspace_menubar_view)) {
            if (editState.canCloseCurrentTool) {
                Item(
                    text = "Close ${mode.displayName}",
                    shortcut = primaryKeyShortcut(Key.W),
                    onClick = {
                        WorkspaceMenuCommandSurface.closeCurrentTool()
                    }
                )

                Separator()
            }
            Menu(text = "Workspace Mode") {
                RadioButtonItem(
                    text = stringResource(Res.string.workspace_menubar_view_layout),
                    selected = primaryMode == WorkspacePrimaryMode.Layout,
                    onClick = {
                        viewModel.switchMode(LayoutWorkspaceMode())
                    }
                )

                RadioButtonItem(
                    text = stringResource(Res.string.workspace_mode_performance),
                    selected = primaryMode == WorkspacePrimaryMode.Performance,
                    onClick = {
                        viewModel.switchMode(PerformanceWorkspaceMode())
                    }
                )

                RadioButtonItem(
                    text = stringResource(Res.string.workspace_mode_timeline),
                    selected = primaryMode == WorkspacePrimaryMode.Timeline,
                    onClick = {
                        viewModel.switchMode(TimelineWorkspaceMode())
                    }
                )

                RadioButtonItem(
                    text = "Lights (Chain Editor)",
                    selected = primaryMode == WorkspacePrimaryMode.LightsChain,
                    onClick = {
                        viewModel.switchMode(LightsChainWorkspaceMode())
                    }
                )

                RadioButtonItem(
                    text = "Sampling (Chain Editor)",
                    selected = primaryMode == WorkspacePrimaryMode.SamplingChain,
                    onClick = {
                        viewModel.switchMode(SamplingChainWorkspaceMode())
                    }
                )
            }
        }

        if (mode is TimelineWorkspaceMode) {
            Menu(text = "Transport") {
                Item(
                    text = if (isTimelinePlaying) stringResource(Res.string.workspace_menubar_view_pause) else stringResource(Res.string.workspace_menubar_view_play),
                    enabled = timelineTracks.isNotEmpty(),
                    onClick = {
                        if (isTimelinePlaying) {
                            TimelineRepository.pause()
                        } else {
                            TimelineRepository.play()
                        }
                    }
                )

                Item(
                    text = stringResource(Res.string.workspace_menubar_view_stop),
                    enabled = timelineTracks.isNotEmpty(),
                    onClick = {
                        TimelineRepository.stop()
                    }
                )
            }
        }

        if (keyframesMode != null) {
            val keyframesState by keyframesMode.state.collectAsState()

            Menu(text = stringResource(Res.string.workspace_chain_devicepicker_keyframes)) {
                CheckboxItem(
                    text = stringResource(Res.string.device_blur_edge_wrap),
                    checked = keyframesState.wrap,
                    onCheckedChange = { checked ->
                        keyframesMode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeWrap(checked))
                    }
                )

                CheckboxItem(
                    text = stringResource(Res.string.workspace_menubar_view_isolate),
                    checked = keyframesState.isolate,
                    onCheckedChange = { checked ->
                        keyframesMode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeIsolate(checked))
                    }
                )

                Separator()

                Item(
                    text = "Clear Root Key",
                    enabled = keyframesState.rootKey != null,
                    onClick = {
                        keyframesMode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeRootKey(null))
                    }
                )
            }
        }

        if (pianoRollMode != null) {
            Menu(text = stringResource(Res.string.workspace_chain_devicepicker_piano_roll)) {
                Menu(text = stringResource(Res.string.workspace_menubar_view_tool)) {
                    RadioButtonItem(
                        text = stringResource(Res.string.workspace_menubar_view_tool_select),
                        selected = pianoRollMode.activeTool == TimelineEditorTool.SELECT,
                        onClick = {
                            pianoRollMode.activeTool = TimelineEditorTool.SELECT
                        }
                    )

                    RadioButtonItem(
                        text = stringResource(Res.string.device_keyframes_draw_tooltip),
                        selected = pianoRollMode.activeTool == TimelineEditorTool.DRAW,
                        onClick = {
                            pianoRollMode.activeTool = TimelineEditorTool.DRAW
                        }
                    )

                    RadioButtonItem(
                        text = "Erase",
                        selected = pianoRollMode.activeTool == TimelineEditorTool.ERASE,
                        onClick = {
                            pianoRollMode.activeTool = TimelineEditorTool.ERASE
                        }
                    )
                }

                Menu(text = "Grid") {
                    RadioButtonItem(
                        text = stringResource(Res.string.workspace_menubar_view_tool_auto),
                        selected = !pianoRollMode.gridResolutionLocked,
                        onClick = {
                            pianoRollMode.gridResolutionLocked = false
                        }
                    )

                    pianoRollGridOptions().forEach { (resolution, label) ->
                        RadioButtonItem(
                            text = label,
                            selected = pianoRollMode.gridResolutionLocked && pianoRollMode.gridResolution == resolution,
                            onClick = {
                                pianoRollMode.gridResolution = resolution
                                pianoRollMode.gridResolutionLocked = true
                            }
                        )
                    }
                }
            }
        }

        Menu(text = "Window") {
            Item(
                text = "Minimize",
                shortcut = primaryKeyShortcut(Key.M),
                onClick = {
                    window.extendedState = window.extendedState or Frame.ICONIFIED
                }
            )

            Item(
                text = stringResource(Res.string.workspace_menubar_view_zoom),
                onClick = {
                    val isMaximized = window.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
                    window.extendedState = if (isMaximized) Frame.NORMAL else Frame.MAXIMIZED_BOTH
                }
            )
        }

        Menu(text = stringResource(Res.string.workspace_menubar_help)) {
            Item(
                text = "Settings...",
                shortcut = primaryKeyShortcut(Key.Comma),
                onClick = {
                    showSettingsWindow()
                }
            )

            Separator()

            Item(
                text = stringResource(Res.string.workspace_menubar_help_about),
                onClick = {
                    showAboutDialog()
                }
            )
        }
    }

    if (showProjectChangeDialog) {
        AmethystTheme {
            SaveChangesDialog(
                description = "You have unsaved changes. Do you want to save them before opening another project?",
                onSave = {
                    val pendingAction = pendingProjectChangeAction
                    coroutineScope.launch {
                        val saved = WorkspaceSaveHelper.saveWorkspace()
                        if (saved) {
                            showProjectChangeDialog = false
                            pendingProjectChangeAction = null
                            pendingAction?.invoke()
                        }
                    }
                },
                onDontSave = {
                    val pendingAction = pendingProjectChangeAction
                    showProjectChangeDialog = false
                    pendingProjectChangeAction = null
                    pendingAction?.invoke()
                },
                onCancel = {
                    showProjectChangeDialog = false
                    pendingProjectChangeAction = null
                }
            )
        }
    }
}

@Composable
private fun pianoRollGridOptions(): List<Pair<GridResolution, String>> {
    return listOf(
        GridResolution.Quarter to stringResource(Res.string.workspace_menubar_grid_quarter),
        GridResolution.Eighth to stringResource(Res.string.workspace_menubar_grid_eighth),
        GridResolution.Sixteenth to stringResource(Res.string.workspace_menubar_grid_sixteenth),
        GridResolution.ThirtySecond to stringResource(Res.string.workspace_menubar_grid_thirty_second)
    )
}
