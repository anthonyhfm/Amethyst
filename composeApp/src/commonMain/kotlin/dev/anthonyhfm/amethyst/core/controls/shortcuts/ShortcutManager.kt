package dev.anthonyhfm.amethyst.core.controls.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.shortcuts.handleRenameShortcut
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray

object ShortcutManager {
    fun undo(): Boolean {
        if (!UndoManager.canUndo()) return false
        UndoManager.undo()
        return true
    }

    fun redo(): Boolean {
        if (!UndoManager.canRedo()) return false
        UndoManager.redo()
        return true
    }

    fun canCopySelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        return selections.isNotEmpty() && selections.none { it is Selectable.GroupChainItem }
    }

    fun copySelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        if (!canCopySelection(selections)) return false
        ClipboardManager.copy(selections)
        return true
    }

    fun canCutSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean = canCopySelection(selections)

    fun cutSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        if (!copySelection(selections)) return false
        return deleteSelection()
    }

    fun canPasteClipboard(
        clipboard: ClipboardData? = ClipboardManager.clipboardData.value,
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        if (clipboard == null) return false

        val hasGroupSelection = selections.any { it is Selectable.GroupChainItem }
        return !hasGroupSelection || (clipboard !is ClipboardData.GroupChainItem && clipboard !is ClipboardData.ChainDevice)
    }

    fun pasteClipboard(
        clipboard: ClipboardData? = ClipboardManager.clipboardData.value,
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        if (!canPasteClipboard(clipboard, selections)) return false
        ClipboardManager.paste()
        return true
    }

    fun canDeleteSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        return selections.any {
            it is Selectable.GroupChainItem ||
                it is Selectable.ChainDevice ||
                it is Selectable.GradientStep
        }
    }

    fun deleteSelection(): Boolean = handleDeletionShortcut()

    fun canDuplicateSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        return selections.any { it is Selectable.GroupChainItem || it is Selectable.ChainDevice }
    }

    fun duplicateSelection(): Boolean = handleDuplicateShortcut()

    fun canRenameSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        return selections.any {
            it is Selectable.GroupChainItem ||
                it is Selectable.TimelineTrack ||
                it is Selectable.TimelineEntryItem
        }
    }

    fun renameSelection(): Boolean = handleRenameShortcut()

    fun canSelectAll(
        mode: WorkspaceContract.WorkspaceMode = WorkspaceRepository.mode.value,
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        val groupItem = selections.filterIsInstance<Selectable.GroupChainItem>().firstOrNull()
        if (groupItem != null) {
            val (_, groups) = ChainNavigator.getGroupsInfo(groupItem.parent) ?: return false
            return groups.isNotEmpty()
        }

        return when (mode) {
            is WorkspaceContract.WorkspaceMode.LightsChain -> WorkspaceRepository.lightsChain.devices.value.isNotEmpty()
            is WorkspaceContract.WorkspaceMode.SamplingChain -> WorkspaceRepository.samplingChain.devices.value.isNotEmpty()
            is WorkspaceContract.WorkspaceMode.Timeline -> TimelineRepository.tracks.value.isNotEmpty()
            else -> false
        }
    }

    fun selectAll(): Boolean = handleSelectAllShortcut()

    @OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
    fun handleShortcut(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        val isCtrl = keyEvent.isCtrlPressed || keyEvent.isMetaPressed

        // Undo / Redo
        if (isCtrl && keyEvent.key == Key.Z) {
            return if (keyEvent.isShiftPressed) redo() else undo()
        }

        if (isCtrl && keyEvent.key == Key.Y) {
            return redo()
        }

        // Delete
        if (keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace) {
            return deleteSelection()
        }

        // Duplicate
        if (isCtrl && keyEvent.key == Key.D) {
            return duplicateSelection()
        }

        // Copy
        if (isCtrl && !keyEvent.isShiftPressed && keyEvent.key == Key.C) {
            return copySelection()
        }

        // Cut (Ctrl+X)
        if (isCtrl && keyEvent.key == Key.X) {
            return cutSelection()
        }

        // Paste
        if (isCtrl && keyEvent.key == Key.V) {
            return pasteClipboard()
        }

        // Select All (Ctrl+A) — context-sensitive
        if (isCtrl && keyEvent.key == Key.A) {
            return selectAll()
        }

        // Save (Ctrl+S) / Save As (Ctrl+Shift+S)
        if (isCtrl && keyEvent.key == Key.S) {
            if (keyEvent.isShiftPressed) {
                // Ctrl+Shift+S → always open file picker (Save As)
                GlobalScope.launch {
                    var path = FileKit.openFileSaver(
                        suggestedName = WorkspaceRepository.workspaceMeta?.title ?: "Untitled",
                        extension = "ame"
                    )?.path ?: return@launch
                    persistWorkspace(path)
                }
            } else {
                // Ctrl+S → save to existing path or prompt
                GlobalScope.launch {
                    var path = WorkspaceRepository.workspaceMeta?.path
                        ?: FileKit.openFileSaver(
                            suggestedName = WorkspaceRepository.workspaceMeta?.title ?: "Untitled",
                            extension = "ame"
                        )?.path ?: return@launch
                    persistWorkspace(path)
                }
            }
            return true
        }

        // Rename — Ctrl+R or F2
        if ((isCtrl && keyEvent.key == Key.R) || keyEvent.key == Key.F2) {
            return renameSelection()
        }

        // Escape — clear selection (fallback, only if something is selected)
        if (keyEvent.key == Key.Escape) {
            if (SelectionManager.selections.value.isNotEmpty()) {
                SelectionManager.clear()
                return true
            }
        }

        // Arrow navigation
        if (keyEvent.key == Key.DirectionDown || keyEvent.key == Key.DirectionUp ||
            keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight) {
            return handleNavigationShortcut(keyEvent)
        }

        // Numeric mode switching (1–5): only when no modifier pressed, main mode is selectable
        if (!isCtrl && !keyEvent.isShiftPressed && WorkspaceRepository.mode.value.selectable) {
            val targetMode = when (keyEvent.key) {
                Key.One -> WorkspaceContract.WorkspaceMode.Performance()
                Key.Two -> WorkspaceContract.WorkspaceMode.Timeline()
                Key.Three -> WorkspaceContract.WorkspaceMode.LightsChain()
                Key.Four -> WorkspaceContract.WorkspaceMode.SamplingChain()
                Key.Five -> WorkspaceContract.WorkspaceMode.Layout()
                else -> null
            }
            if (targetMode != null) {
                WorkspaceRepository.switchMode(targetMode)
                return true
            }
        }

        return false
    }

    private fun handleSelectAllShortcut(): Boolean {
        // GroupChainItem: select all groups in the same compound device
        val groupItem = SelectionManager.selections.value.filterIsInstance<Selectable.GroupChainItem>().firstOrNull()
        if (groupItem != null) {
            val (_, groups) = ChainNavigator.getGroupsInfo(groupItem.parent) ?: return false
            if (groups.isEmpty()) return false
            SelectionManager.clear()
            groups.indices.forEach { i ->
                SelectionManager.select(Selectable.GroupChainItem(groupItem.parent, i), single = false)
            }
            return true
        }

        return when (val mode = WorkspaceRepository.mode.value) {
            is WorkspaceContract.WorkspaceMode.LightsChain -> {
                val chain = WorkspaceRepository.lightsChain
                val devices = chain.devices.value
                if (devices.isNotEmpty()) {
                    SelectionManager.clear()
                    devices.forEach { device ->
                        SelectionManager.select(Selectable.ChainDevice(chain, device), single = false)
                    }
                    true
                } else false
            }
            is WorkspaceContract.WorkspaceMode.SamplingChain -> {
                val chain = WorkspaceRepository.samplingChain
                val devices = chain.devices.value
                if (devices.isNotEmpty()) {
                    SelectionManager.clear()
                    devices.forEach { device ->
                        SelectionManager.select(Selectable.ChainDevice(chain, device), single = false)
                    }
                    true
                } else false
            }
            is WorkspaceContract.WorkspaceMode.Timeline -> {
                val tracks = TimelineRepository.tracks.value
                if (tracks.isNotEmpty()) {
                    SelectionManager.clear()
                    tracks.forEachIndexed { index, _ ->
                        SelectionManager.select(Selectable.TimelineTrack(index), single = false)
                    }
                    true
                } else false
            }
            else -> false
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun persistWorkspace(rawPath: String) {
        val path = if (rawPath.endsWith(".ame")) rawPath else "$rawPath.ame"

        WorkspaceRepository.workspaceMeta = WorkspaceRepository.workspaceMeta?.copy(path = path)
            ?: WorkspaceRepository.workspaceMeta

        PlatformFile(path).write(
            bytes = Zip.encode(
                data = AmethystProtoBuf.encodeToByteArray(
                    value = WorkspaceRepository.saveWorkspace()
                )
            )
        )

        GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces
            .filter { it.path != path }
            .toMutableList()
            .apply {
                add(
                    index = 0,
                    element = RecentWorkspace(
                        title = WorkspaceRepository.workspaceMeta?.title ?: "Untitled",
                        path = path
                    )
                )
            }
    }
}
