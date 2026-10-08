package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.lifecycle.ViewModelStore
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.shortcuts.ShortcutManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LightsChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, InternalComposeUiApi::class)
class TimelinePianoRollToggleIntegrationTest {
    @Test
    fun selectedClipReopensAcrossModeChangesButFocusedOrDeletedClipDoesNot() {
        val previousMode = WorkspaceRepository.mode.value
        val previousFocus = WorkspaceRepository.isInputFocused
        val store = ViewModelStore()
        Dispatchers.setMain(dispatcher = UnconfinedTestDispatcher())
        try {
            WorkspaceRepository.isInputFocused = false
            WorkspaceRepository.switchMode(mode = TimelineWorkspaceMode(), undoable = false)
            val entry = MidiEntry(startTimeMs = 1000L, durationMs = 2000L)
            val track = MidiTimelineTrack().apply { entries[entry.startTimeMs] = entry }
            TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(track))
            val viewModel = TimelineViewModel()
            store.put(key = "timeline-toggle", viewModel = viewModel)
            SelectionManager.select(element = Selectable.TimelineEntryItem(trackIndex = 0, entryStartMs = entry.startTimeMs))
            val shiftTab = KeyEvent(key = Key.Tab, type = KeyEventType.KeyDown, isShiftPressed = true)
            assertTrue(actual = TimelineKeyHandler.handleKeyInput(keyEvent = shiftTab))
            val piano = assertIs<PianoRollWorkspaceMode>(value = WorkspaceRepository.mode.value)
            assertEquals(expected = 1000L, actual = piano.currentEntry?.startTimeMs)
            assertTrue(actual = piano.onKeyEvent(event = shiftTab))
            assertIs<TimelineWorkspaceMode>(value = WorkspaceRepository.mode.value)

            SelectionManager.clear()
            WorkspaceRepository.switchMode(mode = LightsChainWorkspaceMode(), undoable = false)
            assertTrue(actual = ShortcutManager.handleShortcut(keyEvent = shiftTab))
            val reopened = assertIs<PianoRollWorkspaceMode>(value = WorkspaceRepository.mode.value)
            assertEquals(expected = entry.startTimeMs, actual = reopened.currentEntry?.startTimeMs)
            assertEquals(expected = 1, actual = (viewModel.tracks.value.single() as MidiTimelineTrack).entries.size)
            assertTrue(actual = reopened.onKeyEvent(event = shiftTab))

            WorkspaceRepository.isInputFocused = true
            assertFalse(actual = ShortcutManager.handleShortcut(keyEvent = shiftTab))
            assertFalse(actual = TimelineKeyHandler.handleKeyInput(keyEvent = shiftTab))
            WorkspaceRepository.isInputFocused = false
            viewModel.deleteMidiEntry(trackIndex = 0, entryStartMs = entry.startTimeMs)
            SelectionManager.clear()
            val modeBefore = WorkspaceRepository.mode.value
            assertFalse(actual = ShortcutManager.handleShortcut(keyEvent = shiftTab))
            assertEquals(expected = modeBefore, actual = WorkspaceRepository.mode.value)
            assertTrue(actual = (viewModel.tracks.value.single() as MidiTimelineTrack).entries.isEmpty())
        } finally {
            store.clear()
            WorkspaceRepository.switchMode(mode = previousMode, undoable = false)
            WorkspaceRepository.isInputFocused = previousFocus
            TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
            SelectionManager.clear()
            UndoManager.clear()
            Dispatchers.resetMain()
        }
    }
}
