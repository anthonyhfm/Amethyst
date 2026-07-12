package dev.anthonyhfm.amethyst.devices.effects.composition

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.views.CompositionLayout
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode

class CompositionWorkspaceMode(
    private val device: CompositionChainDevice,
) : WorkspaceMode() {
    val editor = CompositionGraphEditor(device)
    override val displayName: String = "Composition"
    override val selectableMode: Boolean = false

    override fun onDeactivate() {
        device.pause()
        device.renderAnimation()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        if (event.key == Key.Escape && editor.selection.value != CompositionGraphSelection()) {
            editor.clearSelection()
            return true
        }

        val primary = event.isCtrlPressed || event.isMetaPressed
        when {
            primary && event.key == Key.Z -> return if (event.isShiftPressed) redo() else undo()
            primary && event.key == Key.Y -> return redo()
            event.key == Key.Delete || event.key == Key.Backspace -> return editor.deleteSelectedAutomation() || editor.delete()
            primary && event.key == Key.C -> return editor.copy()
            primary && event.key == Key.X -> return editor.cut()
            primary && event.key == Key.V -> return editor.paste()
            primary && event.key == Key.D -> return editor.duplicate()
            primary && event.key == Key.A -> return editor.selectAllAutomationPoints() || editor.selectAll()
        }

        val isExitKey = event.key == Key.Escape ||
            ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.W)

        if (isExitKey) {
            WorkspaceRepository.switchToPreviousMode()
            return true
        }

        return false
    }

    fun undo(): Boolean {
        if (!UndoManager.canUndo()) return false
        UndoManager.undo()
        editor.reconcile()
        return true
    }

    fun redo(): Boolean {
        if (!UndoManager.canRedo()) return false
        UndoManager.redo()
        editor.reconcile()
        return true
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) {
    }

    @Composable
    override fun Content(modifier: Modifier) {
        CompositionLayout(device = device, editor = editor, modifier = modifier)
    }
}
