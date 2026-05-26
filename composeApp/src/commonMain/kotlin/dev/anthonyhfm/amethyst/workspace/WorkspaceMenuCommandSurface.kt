package dev.anthonyhfm.amethyst.workspace

import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.shortcuts.ShortcutManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.preview.PreviewWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.TimelineKeyHandler
import dev.anthonyhfm.amethyst.workspace.help.GetHelpWorkspaceMode

data class WorkspaceMenuEditState(
    val canUndo: Boolean,
    val canRedo: Boolean,
    val canCut: Boolean,
    val canCopy: Boolean,
    val canPaste: Boolean,
    val canDelete: Boolean,
    val canDuplicate: Boolean,
    val canRename: Boolean,
    val canSelectAll: Boolean,
    val canCloseCurrentTool: Boolean
)

enum class WorkspacePrimaryMode {
    Layout,
    Performance,
    Timeline,
    LightsChain,
    SamplingChain
}

object WorkspaceMenuCommandSurface {
    fun currentPrimaryMode(
        mode: WorkspaceContract.WorkspaceMode = WorkspaceRepository.mode.value
    ): WorkspacePrimaryMode? {
        return when (mode) {
            is WorkspaceContract.WorkspaceMode.Layout -> WorkspacePrimaryMode.Layout
            is WorkspaceContract.WorkspaceMode.Performance -> WorkspacePrimaryMode.Performance
            is WorkspaceContract.WorkspaceMode.Timeline -> WorkspacePrimaryMode.Timeline
            is WorkspaceContract.WorkspaceMode.LightsChain -> WorkspacePrimaryMode.LightsChain
            is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspacePrimaryMode.SamplingChain
            else -> null
        }
    }

    fun editState(
        mode: WorkspaceContract.WorkspaceMode = WorkspaceRepository.mode.value,
        selections: List<Selectable> = SelectionManager.selections.value,
        clipboard: ClipboardData? = ClipboardManager.clipboardData.value
    ): WorkspaceMenuEditState {
        return WorkspaceMenuEditState(
            canUndo = when (mode) {
                else -> UndoManager.canUndo()
            },
            canRedo = when (mode) {
                else -> UndoManager.canRedo()
            },
            canCut = when (mode) {
                is WorkspaceContract.WorkspaceMode.Timeline -> TimelineKeyHandler.canCutSelection(selections)
                is KeyframesWorkspaceMode -> selections.any { it is Selectable.KeyframeItem }
                else -> ShortcutManager.canCutSelection(selections)
            },
            canCopy = when (mode) {
                is WorkspaceContract.WorkspaceMode.Timeline -> TimelineKeyHandler.canCopySelection(selections)
                is KeyframesWorkspaceMode -> selections.any { it is Selectable.KeyframeItem }
                else -> ShortcutManager.canCopySelection(selections)
            },
            canPaste = canPasteForMode(mode, clipboard, selections),
            canDelete = when (mode) {
                is WorkspaceContract.WorkspaceMode.Timeline -> TimelineKeyHandler.canDeleteSelection(selections)
                is KeyframesWorkspaceMode -> mode.state.value.frames.isNotEmpty()
                is PianoRollWorkspaceMode -> selections.any { it is Selectable.PianoRollNote }
                else -> ShortcutManager.canDeleteSelection(selections)
            },
            canDuplicate = when (mode) {
                is WorkspaceContract.WorkspaceMode.Timeline -> TimelineKeyHandler.canDuplicateSelection(selections)
                is KeyframesWorkspaceMode -> mode.state.value.frames.isNotEmpty()
                is PianoRollWorkspaceMode -> selections.any { it is Selectable.PianoRollNote }
                else -> ShortcutManager.canDuplicateSelection(selections)
            },
            canRename = when (mode) {
                is WorkspaceContract.WorkspaceMode.Timeline -> TimelineKeyHandler.canRenameSelection(selections)
                else -> ShortcutManager.canRenameSelection(selections)
            },
            canSelectAll = when (mode) {
                is KeyframesWorkspaceMode -> mode.state.value.frames.isNotEmpty()
                is PianoRollWorkspaceMode -> mode.currentEntry?.notes?.isNotEmpty() == true
                else -> ShortcutManager.canSelectAll(mode, selections)
            },
            canCloseCurrentTool = !mode.selectable
        )
    }

    fun undo(): Boolean {
        return when (val mode = WorkspaceRepository.mode.value) {
            else -> ShortcutManager.undo()
        }
    }

    fun redo(): Boolean {
        return when (val mode = WorkspaceRepository.mode.value) {
            else -> ShortcutManager.redo()
        }
    }

    fun copy(): Boolean {
        return when (val mode = WorkspaceRepository.mode.value) {
            is WorkspaceContract.WorkspaceMode.Timeline -> TimelineKeyHandler.copySelection()
            is KeyframesWorkspaceMode -> mode.copySelection()
            else -> ShortcutManager.copySelection()
        }
    }

    fun cut(): Boolean {
        return when (val mode = WorkspaceRepository.mode.value) {
            is WorkspaceContract.WorkspaceMode.Timeline -> TimelineKeyHandler.cutSelection()
            is KeyframesWorkspaceMode -> mode.cutSelection()
            else -> ShortcutManager.cutSelection()
        }
    }

    fun paste(): Boolean {
        return when (val mode = WorkspaceRepository.mode.value) {
            is KeyframesWorkspaceMode -> mode.pasteSelection()
            is PianoRollWorkspaceMode -> false
            else -> ShortcutManager.pasteClipboard()
        }
    }

    fun delete(): Boolean {
        return when (val mode = WorkspaceRepository.mode.value) {
            is WorkspaceContract.WorkspaceMode.Timeline -> TimelineKeyHandler.deleteSelection()
            is KeyframesWorkspaceMode -> mode.deleteSelection()
            is PianoRollWorkspaceMode -> mode.deleteSelectedNotes()
            else -> ShortcutManager.deleteSelection()
        }
    }

    fun duplicate(): Boolean {
        return when (val mode = WorkspaceRepository.mode.value) {
            is WorkspaceContract.WorkspaceMode.Timeline -> TimelineKeyHandler.duplicateSelection()
            is KeyframesWorkspaceMode -> mode.duplicateSelection()
            is PianoRollWorkspaceMode -> mode.duplicateSelectedNotes()
            else -> ShortcutManager.duplicateSelection()
        }
    }

    fun rename(): Boolean {
        return when (WorkspaceRepository.mode.value) {
            is WorkspaceContract.WorkspaceMode.Timeline -> TimelineKeyHandler.renameSelection()
            else -> ShortcutManager.renameSelection()
        }
    }

    fun selectAll(): Boolean {
        return when (val mode = WorkspaceRepository.mode.value) {
            is KeyframesWorkspaceMode -> mode.selectAllFrames()
            is PianoRollWorkspaceMode -> mode.selectAllNotes()
            else -> ShortcutManager.selectAll()
        }
    }

    fun closeCurrentTool(): Boolean {
        return when (val mode = WorkspaceRepository.mode.value) {
            is CoordinateFilterWorkspaceMode -> {
                mode.close()
                true
            }

            is KeyframesWorkspaceMode -> {
                mode.close()
                true
            }

            is PreviewWorkspaceMode -> {
                mode.close()
                true
            }

            is PianoRollWorkspaceMode -> {
                mode.modeClose?.invoke()
                true
            }

            is GetHelpWorkspaceMode -> {
                WorkspaceRepository.switchToPreviousMode()
                true
            }

            else -> {
                if (mode.selectable) {
                    false
                } else {
                    WorkspaceRepository.switchToPreviousMode()
                    true
                }
            }
        }
    }

    private fun canPasteForMode(
        mode: WorkspaceContract.WorkspaceMode,
        clipboard: ClipboardData?,
        selections: List<Selectable>
    ): Boolean {
        clipboard ?: return false

        return when (mode) {
            is WorkspaceContract.WorkspaceMode.Timeline -> {
                clipboard is ClipboardData.TimelineAudioEntries ||
                    clipboard is ClipboardData.TimelineAudioRange ||
                    clipboard is ClipboardData.TimelineMidiEntries
            }

            is WorkspaceContract.WorkspaceMode.LightsChain -> canPasteIntoChainMode(
                clipboard = clipboard,
                selections = selections,
                allowsSamplingDrops = false,
                chainType = ClipboardData.ChainDevice.ChainType.Lights
            )

            is WorkspaceContract.WorkspaceMode.SamplingChain -> canPasteIntoChainMode(
                clipboard = clipboard,
                selections = selections,
                allowsSamplingDrops = true,
                chainType = ClipboardData.ChainDevice.ChainType.Sampling
            )

            is KeyframesWorkspaceMode -> clipboard is ClipboardData.Keyframe
            else -> ShortcutManager.canPasteClipboard(clipboard, selections)
        }
    }

    private fun canPasteIntoChainMode(
        clipboard: ClipboardData,
        selections: List<Selectable>,
        allowsSamplingDrops: Boolean,
        chainType: ClipboardData.ChainDevice.ChainType
    ): Boolean {
        val hasGroupSelection = selections.any { it is Selectable.GroupChainItem }

        return when (clipboard) {
            is ClipboardData.ChainDevice -> !hasGroupSelection && clipboard.type == chainType
            is ClipboardData.GroupChainItem -> hasGroupSelection
            is ClipboardData.TimelineAudioEntries,
            is ClipboardData.TimelineAudioRange -> allowsSamplingDrops
            else -> false
        }
    }
}
