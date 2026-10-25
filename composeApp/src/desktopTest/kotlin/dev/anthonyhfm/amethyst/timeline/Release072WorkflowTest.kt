@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.shortcuts.ShortcutManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDevice
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipKey
import dev.anthonyhfm.amethyst.timeline.data.*
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.ui.moveChainDevices
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LightsChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
import kotlin.test.*

class Release072WorkflowTest {
    private lateinit var previousMode: dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
    @BeforeTest
    fun setup() {
        previousMode = WorkspaceRepository.mode.value
        SelectionManager.clear()
        UndoManager.clear()
        WorkspaceRepository.isInputFocused = false
        TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
    }

    @AfterTest
    fun cleanup() {
        WorkspaceRepository.switchMode(mode = previousMode, undoable = false)
        TimelineRepository.pause()
        TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
        SelectionManager.clear()
        UndoManager.clear()
        WorkspaceRepository.isInputFocused = false
    }

    @Test
    fun pianoKeyboardClipboardMuteAndTextFocusStayInTheirEditors() {
        val note = MidiNote.withPaint(device = 0, pitch = 11, color = androidx.compose.ui.graphics.Color.Red, startTimeMs = 0L, durationMs = 100L)
        val entry = MidiEntry(startTimeMs = 0L, durationMs = 2000L, notes = listOf(note))
        TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(MidiTimelineTrack().apply { entries[0L] = entry }))
        val mode = PianoRollWorkspaceMode().apply {
            bindClipContext(context = dev.anthonyhfm.amethyst.timeline.contract.TimelineClipContext.midi(trackIndex = 0, entry = entry), entry = entry)
        }
        WorkspaceRepository.switchMode(mode = mode, undoable = false)
        try {
            assertTrue(actual = mode.onKeyEvent(event = KeyEvent(key = Key.A, type = KeyEventType.KeyDown, isMetaPressed = true)))
            assertTrue(actual = mode.onKeyEvent(event = KeyEvent(key = Key.C, type = KeyEventType.KeyDown, isMetaPressed = true)))
            mode.selectedTimeMs = 500L
            assertTrue(actual = mode.onKeyEvent(event = KeyEvent(key = Key.V, type = KeyEventType.KeyDown, isMetaPressed = true)))
            assertEquals(expected = listOf(0L, 500L), actual = mode.currentEntry!!.notes.map { it.startTimeMs })
            assertTrue(actual = mode.onKeyEvent(event = KeyEvent(key = Key.Z, type = KeyEventType.KeyDown, isMetaPressed = true)))
            assertEquals(expected = listOf(note), actual = mode.currentEntry!!.notes)
            WorkspaceRepository.isInputFocused = true
            for (key in listOf(Key.A, Key.C, Key.V, Key.Z, Key.Zero, Key.T)) {
                assertFalse(actual = mode.onKeyEvent(event = KeyEvent(key = key, type = KeyEventType.KeyDown, isMetaPressed = key != Key.Zero)))
            }
            WorkspaceRepository.isInputFocused = false
            ClipboardManager.setClipboardData(data = ClipboardData.ChainDevice(states = listOf(FlipChainDeviceState()), type = ClipboardData.ChainDevice.ChainType.Lights))
            assertFalse(actual = ShortcutManager.pasteClipboard())
            assertFalse(actual = mode.onKeyEvent(event = KeyEvent(key = Key.V, type = KeyEventType.KeyDown, isMetaPressed = true)))
        } finally {
            mode.onDeactivate()
        }
    }

    @Test
    fun modalAndTextFocusProtectClipboardSelectionAndMuteCommands() {
        WorkspaceRepository.switchMode(mode = LightsChainWorkspaceMode(), undoable = false)
        val release = WorkspaceRepository.blockEditorShortcuts()
        try {
            for (key in listOf(Key.A, Key.C, Key.V, Key.Z, Key.T, Key.Zero)) {
                assertFalse(actual = ShortcutManager.handleShortcut(keyEvent = KeyEvent(key = key, type = KeyEventType.KeyDown, isMetaPressed = key != Key.Zero)))
            }
        } finally {
            release()
        }
        assertFalse(actual = WorkspaceRepository.isInputFocused)
    }


}
