package dev.anthonyhfm.amethyst.gem.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

fun handleGemEditorKeyEvent(
    event: KeyEvent,
    session: GemEditorSession,
    onShowNodePalette: () -> Unit,
    onFitToContent: () -> Unit = {},
    onDuplicateAsset: () -> Unit = {},
    onExportAsset: () -> Unit = {},
    onNewAsset: () -> Unit = {}
): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }

    val isPrimaryShortcut = event.isCtrlPressed || event.isMetaPressed
    val selection = session.state.value.selection
    val action = resolveGemEditorShortcutAction(
        key = event.key,
        isPrimaryShortcut = isPrimaryShortcut,
        isShiftPressed = event.isShiftPressed,
        hasNodeSelection = selection.nodeIds.isNotEmpty(),
        hasCanvasSelection = selection.nodeIds.isNotEmpty() || selection.connectionIds.isNotEmpty(),
        hasSelection = selection.nodeIds.isNotEmpty() ||
            selection.connectionIds.isNotEmpty() ||
            selection.exposedParameterId != null
    ) ?: return false
    return performGemEditorShortcutAction(
        action = action,
        session = session,
        onShowNodePalette = onShowNodePalette,
        onFitToContent = onFitToContent,
        onDuplicateAsset = onDuplicateAsset,
        onExportAsset = onExportAsset,
        onNewAsset = onNewAsset
    )
}

internal enum class GemEditorShortcutAction {
    OpenNodePalette,
    Save,
    Undo,
    Redo,
    DuplicateSelection,
    DeleteSelection,
    SelectAllNodes,
    ClearSelection,
    FitToContent,
    DuplicateAsset,
    ExportAsset,
    NewAsset,
}

internal fun resolveGemEditorShortcutAction(
    key: Key,
    isPrimaryShortcut: Boolean,
    isShiftPressed: Boolean,
    hasNodeSelection: Boolean,
    hasCanvasSelection: Boolean,
    hasSelection: Boolean
): GemEditorShortcutAction? = when {
    isPrimaryShortcut && key == Key.K -> GemEditorShortcutAction.OpenNodePalette
    isPrimaryShortcut && key == Key.S -> GemEditorShortcutAction.Save
    isPrimaryShortcut && key == Key.Z && isShiftPressed -> GemEditorShortcutAction.Redo
    isPrimaryShortcut && key == Key.Z -> GemEditorShortcutAction.Undo
    isPrimaryShortcut && key == Key.Y -> GemEditorShortcutAction.Redo
    isPrimaryShortcut && key == Key.A -> GemEditorShortcutAction.SelectAllNodes
    isPrimaryShortcut && key == Key.D && hasNodeSelection -> GemEditorShortcutAction.DuplicateSelection
    isPrimaryShortcut && key == Key.D && !hasSelection -> GemEditorShortcutAction.DuplicateAsset
    isPrimaryShortcut && key == Key.E -> GemEditorShortcutAction.ExportAsset
    isPrimaryShortcut && key == Key.N -> GemEditorShortcutAction.NewAsset
    (key == Key.Delete || key == Key.Backspace) && hasCanvasSelection -> GemEditorShortcutAction.DeleteSelection
    key == Key.Escape && hasSelection -> GemEditorShortcutAction.ClearSelection
    key == Key.F -> GemEditorShortcutAction.FitToContent
    else -> null
}

internal fun performGemEditorShortcutAction(
    action: GemEditorShortcutAction,
    session: GemEditorSession,
    onShowNodePalette: () -> Unit,
    onFitToContent: () -> Unit = {},
    onDuplicateAsset: () -> Unit = {},
    onExportAsset: () -> Unit = {},
    onNewAsset: () -> Unit = {}
): Boolean {
    return when (action) {
        GemEditorShortcutAction.OpenNodePalette -> {
            onShowNodePalette()
            true
        }

        GemEditorShortcutAction.Save -> {
            session.save()
            true
        }

        GemEditorShortcutAction.Undo -> {
            session.undo()
            true
        }

        GemEditorShortcutAction.Redo -> {
            session.redo()
            true
        }

        GemEditorShortcutAction.DuplicateSelection -> {
            session.duplicateSelection()
            true
        }

        GemEditorShortcutAction.DeleteSelection -> {
            session.deleteSelection()
            true
        }

        GemEditorShortcutAction.SelectAllNodes -> {
            val selection = session.state.value.selection
            val graph = session.state.value.editedAsset.graph(selection.graphId)
                ?: session.state.value.editedAsset.definition.rootGraph
            if (graph.nodes.isEmpty()) {
                false
            } else {
                session.setSelection(
                    GemEditorSelection(
                        graphId = graph.id,
                        nodeIds = graph.nodes.mapTo(linkedSetOf()) { it.id }
                    )
                )
                true
            }
        }

        GemEditorShortcutAction.ClearSelection -> {
            val selection = session.state.value.selection
            session.setSelection(
                selection.copy(
                    nodeIds = emptySet(),
                    connectionIds = emptySet(),
                    exposedParameterId = null
                )
            )
            true
        }

        GemEditorShortcutAction.FitToContent -> {
            onFitToContent()
            true
        }

        GemEditorShortcutAction.DuplicateAsset -> {
            onDuplicateAsset()
            true
        }

        GemEditorShortcutAction.ExportAsset -> {
            onExportAsset()
            true
        }

        GemEditorShortcutAction.NewAsset -> {
            onNewAsset()
            true
        }
    }
}
