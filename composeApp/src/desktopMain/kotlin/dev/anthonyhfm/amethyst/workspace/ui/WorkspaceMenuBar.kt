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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.desktop.about.AboutDialog
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.settings.SettingsDialog
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
import dev.nucleusframework.application.NucleusDecoratedWindowScope
import dev.nucleusframework.menu.macos.NativeKeyShortcut
import dev.nucleusframework.menu.macos.NativeMenuBar

@Composable
fun NucleusDecoratedWindowScope.WorkspaceMenuBar(
    onRequestClose: () -> Unit,
) {
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
    val keyframesState = keyframesMode?.let { it.state.collectAsState().value }

    var showProjectChangeDialog by remember { mutableStateOf(false) }
    var pendingProjectChangeAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val aboutLabel = stringResource(Res.string.workspace_menubar_help_about)
    val fileLabel = stringResource(Res.string.workspace_menubar_file)
    val emptyProjectsLabel = stringResource(Res.string.home_projects_empty_title)
    val saveLabel = stringResource(Res.string.workspace_menubar_file_save)
    val saveAsLabel = stringResource(Res.string.workspace_menubar_file_save_as)
    val editLabel = stringResource(Res.string.workspace_menubar_edit)
    val redoLabel = stringResource(Res.string.workspace_menubar_edit_redo)
    val cutLabel = stringResource(Res.string.workspace_menubar_edit_cut)
    val copyLabel = stringResource(Res.string.workspace_menubar_edit_copy)
    val pasteLabel = stringResource(Res.string.workspace_menubar_edit_paste)
    val deleteLabel = stringResource(Res.string.workspace_menubar_edit_delete)
    val duplicateLabel = stringResource(Res.string.workspace_menubar_edit_duplicate)
    val renameLabel = stringResource(Res.string.device_group_editor_rename)
    val selectAllLabel = stringResource(Res.string.workspace_menubar_edit_select_all)
    val viewLabel = stringResource(Res.string.workspace_menubar_view)
    val layoutLabel = stringResource(Res.string.workspace_menubar_view_layout)
    val performanceLabel = stringResource(Res.string.workspace_mode_performance)
    val timelineLabel = stringResource(Res.string.workspace_mode_timeline)
    val playLabel = stringResource(Res.string.workspace_menubar_view_play)
    val pauseLabel = stringResource(Res.string.workspace_menubar_view_pause)
    val stopLabel = stringResource(Res.string.workspace_menubar_view_stop)
    val keyframesLabel = stringResource(Res.string.workspace_chain_devicepicker_keyframes)
    val edgeWrapLabel = stringResource(Res.string.device_blur_edge_wrap)
    val isolateLabel = stringResource(Res.string.workspace_menubar_view_isolate)
    val pianoRollLabel = stringResource(Res.string.workspace_chain_devicepicker_piano_roll)
    val toolLabel = stringResource(Res.string.workspace_menubar_view_tool)
    val selectToolLabel = stringResource(Res.string.workspace_menubar_view_tool_select)
    val drawToolLabel = stringResource(Res.string.device_keyframes_draw_tooltip)
    val autoGridLabel = stringResource(Res.string.workspace_menubar_view_tool_auto)
    val zoomLabel = stringResource(Res.string.workspace_menubar_view_zoom)
    val helpLabel = stringResource(Res.string.workspace_menubar_help)
    val gridOptions = pianoRollGridOptions()

    fun requestProjectChange(action: () -> Unit) {
        if (WorkspaceRepository.hasUnsavedChanges()) {
            pendingProjectChangeAction = action
            showProjectChangeDialog = true
        } else {
            action()
        }
    }

    NativeMenuBar {
        Menu(text = "Amethyst") {
            Item(
                text = aboutLabel,
                onClick = { showAboutDialog = true },
            )
            Separator()
            Item(
                text = "Settings...",
                shortcut = NativeKeyShortcut(","),
                onClick = { showSettingsDialog = true },
            )
        }

        Menu(text = fileLabel) {
            Item(
                text = "Open Project...",
                shortcut = NativeKeyShortcut("o"),
                onClick = {
                    requestProjectChange {
                        viewModel.openProject()
                    }
                }
            )

            Menu(text = "Open Recent") {
                if (recentProjects.isEmpty()) {
                    Item(
                        text = emptyProjectsLabel,
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
                text = saveLabel,
                shortcut = NativeKeyShortcut("s"),
                onClick = {
                    viewModel.saveProject()
                }
            )

            Item(
                text = saveAsLabel,
                shortcut = NativeKeyShortcut("s", shift = true),
                onClick = {
                    viewModel.saveProjectAs()
                }
            )

            Separator()

            Item(
                text = "Close Project",
                shortcut = NativeKeyShortcut("w", shift = true),
                onClick = onRequestClose,
            )
        }

        Menu(text = editLabel) {
            Item(
                text = "Undo",
                enabled = editState.canUndo,
                shortcut = NativeKeyShortcut("z"),
                onClick = {
                    WorkspaceMenuCommandSurface.undo()
                }
            )

            Item(
                text = redoLabel,
                enabled = editState.canRedo,
                shortcut = NativeKeyShortcut("z", shift = true),
                onClick = {
                    WorkspaceMenuCommandSurface.redo()
                }
            )

            Separator()

            Item(
                text = cutLabel,
                enabled = editState.canCut,
                shortcut = NativeKeyShortcut("x"),
                onClick = {
                    WorkspaceMenuCommandSurface.cut()
                }
            )

            Item(
                text = copyLabel,
                enabled = editState.canCopy,
                shortcut = NativeKeyShortcut("c"),
                onClick = {
                    WorkspaceMenuCommandSurface.copy()
                }
            )

            Item(
                text = pasteLabel,
                enabled = editState.canPaste,
                shortcut = NativeKeyShortcut("v"),
                onClick = {
                    WorkspaceMenuCommandSurface.paste()
                }
            )

            Separator()

            Item(
                text = deleteLabel,
                enabled = editState.canDelete,
                onClick = {
                    WorkspaceMenuCommandSurface.delete()
                }
            )

            Item(
                text = duplicateLabel,
                enabled = editState.canDuplicate,
                shortcut = NativeKeyShortcut("d"),
                onClick = {
                    WorkspaceMenuCommandSurface.duplicate()
                }
            )

            Item(
                text = renameLabel,
                enabled = editState.canRename,
                shortcut = NativeKeyShortcut("r"),
                onClick = {
                    WorkspaceMenuCommandSurface.rename()
                }
            )

            Separator()

            Item(
                text = selectAllLabel,
                enabled = editState.canSelectAll,
                shortcut = NativeKeyShortcut("a"),
                onClick = {
                    WorkspaceMenuCommandSurface.selectAll()
                }
            )
        }

        Menu(text = viewLabel) {
            if (editState.canCloseCurrentTool) {
                Item(
                    text = "Close ${mode.displayName}",
                    shortcut = NativeKeyShortcut("w"),
                    onClick = {
                        WorkspaceMenuCommandSurface.closeCurrentTool()
                    }
                )

                Separator()
            }
            Menu(text = "Workspace Mode") {
                RadioButtonItem(
                    text = layoutLabel,
                    selected = primaryMode == WorkspacePrimaryMode.Layout,
                    onClick = {
                        viewModel.switchMode(LayoutWorkspaceMode())
                    }
                )

                RadioButtonItem(
                    text = performanceLabel,
                    selected = primaryMode == WorkspacePrimaryMode.Performance,
                    onClick = {
                        viewModel.switchMode(PerformanceWorkspaceMode())
                    }
                )

                RadioButtonItem(
                    text = timelineLabel,
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
                    text = if (isTimelinePlaying) pauseLabel else playLabel,
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
                    text = stopLabel,
                    enabled = timelineTracks.isNotEmpty(),
                    onClick = {
                        TimelineRepository.stop()
                    }
                )
            }
        }

        if (keyframesMode != null && keyframesState != null) {
            Menu(text = keyframesLabel) {
                CheckboxItem(
                    text = edgeWrapLabel,
                    checked = keyframesState.wrap,
                    onCheckedChange = { checked ->
                        keyframesMode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeWrap(checked))
                    }
                )

                CheckboxItem(
                    text = isolateLabel,
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
            Menu(text = pianoRollLabel) {
                Menu(text = toolLabel) {
                    RadioButtonItem(
                        text = selectToolLabel,
                        selected = pianoRollMode.activeTool == TimelineEditorTool.SELECT,
                        onClick = {
                            pianoRollMode.activeTool = TimelineEditorTool.SELECT
                        }
                    )

                    RadioButtonItem(
                        text = drawToolLabel,
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
                        text = autoGridLabel,
                        selected = !pianoRollMode.gridResolutionLocked,
                        onClick = {
                            pianoRollMode.gridResolutionLocked = false
                        }
                    )

                    gridOptions.forEach { (resolution, label) ->
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

        MenuWindow(text = "Window") {
            Item(
                text = "Minimize",
                shortcut = NativeKeyShortcut("m"),
                onClick = {
                    nucleusWindow.setMinimized(true)
                }
            )

            Item(
                text = zoomLabel,
                onClick = {
                    nucleusWindow.setMaximized(!nucleusWindow.isMaximized)
                }
            )
        }

        MenuHelp(text = helpLabel) {
            Item(
                text = "Settings...",
                shortcut = NativeKeyShortcut(","),
                onClick = { showSettingsDialog = true },
            )

            Separator()

            Item(
                text = aboutLabel,
                onClick = { showAboutDialog = true },
            )
        }
    }

    SettingsDialog(
        visible = showSettingsDialog,
        onDismiss = { showSettingsDialog = false },
    )

    AboutDialog(
        visible = showAboutDialog,
        onDismiss = { showAboutDialog = false },
    )

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
