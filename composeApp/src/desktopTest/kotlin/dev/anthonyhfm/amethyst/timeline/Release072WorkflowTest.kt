@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.shortcuts.EditorMuteCommands
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
    fun existingChainEffectClipsUseTheSameCopyMoveMuteAndReloadRules() {
        val effect = ChainEffectEntry(
            clipId = "effect-original",
            startTimeMs = 100L,
            durationMs = 2000L,
            maxDurationMs = 2000L,
            processors = StateChain(devices = listOf(FlipChainDeviceState()), deviceIds = listOf("effect-processor")),
        )
        TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(
            MidiTimelineTrack().apply { chainEffectEntries[effect.startTimeMs] = effect },
            MidiTimelineTrack(),
        ))
        val key = TimelineClipKey(trackIndex = 0, entryStartMs = 100L, clipId = effect.clipId)
        val command = TimelineEditCommand.MoveEntries(
            entryKeys = listOf(key),
            anchorKey = key,
            targetTrackIndex = 1,
            targetStartMs = 3000L,
            copy = true,
        )
        assertTrue(actual = TimelineCommandExecutor.execute(command = command).didChange)
        val copied = (TimelineRepository.tracks.value[1] as MidiTimelineTrack).chainEffectEntries.getValue(3000L)
        assertNotEquals(illegal = effect.clipId, actual = copied.clipId)
        assertEquals(expected = effect, actual = (TimelineRepository.tracks.value[0] as MidiTimelineTrack).chainEffectEntries.getValue(100L))
        UndoManager.undo()
        assertTrue(actual = (TimelineRepository.tracks.value[1] as MidiTimelineTrack).chainEffectEntries.isEmpty())
        UndoManager.redo()
        assertTrue(actual = TimelineCommandSurface.toggleEntriesMute(targets = listOf(
            TimelineClipKey(trackIndex = 1, entryStartMs = 3000L, clipId = copied.clipId),
        )))
        val muted = (TimelineRepository.tracks.value[1] as MidiTimelineTrack).chainEffectEntries.getValue(3000L)
        assertTrue(actual = muted.isMuted)
        assertTrue(actual = AmethystProtoBuf.decodeFromByteArray(
            deserializer = ChainEffectEntry.serializer(),
            bytes = AmethystProtoBuf.encodeToByteArray(serializer = ChainEffectEntry.serializer(), value = muted),
        ).isMuted)
        UndoManager.undo()
        assertTrue(actual = TimelineCommandExecutor.execute(command = command.copy(copy = false, targetStartMs = 8000L)).didChange)
        assertTrue(actual = (TimelineRepository.tracks.value[0] as MidiTimelineTrack).chainEffectEntries.isEmpty())
        assertEquals(expected = effect.clipId, actual = (TimelineRepository.tracks.value[1] as MidiTimelineTrack).chainEffectEntries.getValue(8000L).clipId)
        UndoManager.undo()
        assertEquals(expected = effect, actual = (TimelineRepository.tracks.value[0] as MidiTimelineTrack).chainEffectEntries.getValue(100L))
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
    fun zeroTogglesSelectedDevicesAsOneUndoStepAndChainCopiesSurviveProjectReload() {
        WorkspaceRepository.switchMode(mode = LightsChainWorkspaceMode(), undoable = false)
        val devices = listOf(FlipChainDevice(), FlipChainDevice())
        val chain = Chain().apply { addAll(devicesToAdd = devices, fromUser = false) }
        devices.forEach { SelectionManager.select(element = Selectable.ChainDevice(parent = chain, device = it), single = false) }
        try {
            assertTrue(actual = ShortcutManager.handleShortcut(keyEvent = KeyEvent(key = Key.Zero, type = KeyEventType.KeyDown)))
            assertTrue(actual = devices.all { it.isMuted })
            UndoManager.undo()
            assertTrue(actual = devices.none { it.isMuted })
            UndoManager.redo()
            val group = GroupChainDevice().apply {
                state.value = state.value.copy(groups = listOf(Group(name = "Original", chain = chain)))
            }
            group.duplicateGroup(index = 0, toIndex = 1)
            val root = Chain().apply { add(device = group, fromUser = false) }
            try {
                val workspace = dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData(lights = StateChain.pack(chain = root))
                val restored = AmethystProtoBuf.decodeFromByteArray(
                    deserializer = dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData.serializer(),
                    bytes = AmethystProtoBuf.encodeToByteArray(serializer = dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData.serializer(), value = workspace),
                ).lights.unpack()
                try {
                    val restoredGroups = (restored.devices.value.single() as GroupChainDevice).state.value.groups
                    assertEquals(expected = 2, actual = restoredGroups.size)
                    assertNotEquals(illegal = restoredGroups[0].chain.devices.value.first().selectionUUID, actual = restoredGroups[1].chain.devices.value.first().selectionUUID)
                    assertTrue(actual = restoredGroups.all { it.chain.devices.value.all { device -> device.isMuted } })
                } finally {
                    restored.dispose()
                }
            } finally {
                root.dispose()
            }
        } finally {
            chain.dispose()
        }
    }

    @Test
    fun commandTrackShortcutsAreTypedUndoableAndIgnoreRepeatsAndTextFocus() {
        WorkspaceRepository.switchMode(mode = TimelineWorkspaceMode(), undoable = false)
        for (meta in listOf(true, false)) {
            val down = KeyEvent(key = Key.T, type = KeyEventType.KeyDown, isMetaPressed = meta, isCtrlPressed = !meta)
            assertTrue(actual = TimelineKeyHandler.handleKeyInput(keyEvent = down))
            assertTrue(actual = TimelineKeyHandler.handleKeyInput(keyEvent = down))
            TimelineKeyHandler.handleKeyInput(keyEvent = KeyEvent(key = Key.T, type = KeyEventType.KeyUp))
        }
        assertEquals(expected = 2, actual = TimelineRepository.tracks.value.size)
        assertTrue(actual = TimelineRepository.tracks.value.all { it is AudioTimelineTrack })
        assertTrue(actual = TimelineKeyHandler.handleKeyInput(keyEvent = KeyEvent(key = Key.T, type = KeyEventType.KeyDown, isMetaPressed = true, isShiftPressed = true)))
        TimelineKeyHandler.handleKeyInput(keyEvent = KeyEvent(key = Key.T, type = KeyEventType.KeyUp))
        assertIs<MidiTimelineTrack>(value = TimelineRepository.tracks.value.last())
        UndoManager.undo()
        assertEquals(expected = 2, actual = TimelineRepository.tracks.value.size)
        WorkspaceRepository.isInputFocused = true
        assertFalse(actual = TimelineKeyHandler.handleKeyInput(keyEvent = KeyEvent(key = Key.T, type = KeyEventType.KeyDown, isMetaPressed = true)))
        assertEquals(expected = 2, actual = TimelineRepository.tracks.value.size)
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

    @Test
    fun deviceCopyDropHasIndependentSubtreesAndOneUndoStep() {
        val child = FlipChainDevice()
        val group = GroupChainDevice().apply {
            state.value = state.value.copy(groups = listOf(Group(name = "Child", chain = Chain().apply { add(device = child, fromUser = false) })))
        }
        val source = Chain().apply { add(device = group, fromUser = false) }
        val destination = Chain()
        try {
            moveChainDevices(chain = destination, parentSelectionUUID = null, onAddDevice = null, onMoveDevice = null, draggedDevices = listOf(group), originChain = source, insertionIndex = 0, copy = true)
            assertEquals(expected = listOf(group), actual = source.devices.value)
            val copied = assertIs<GroupChainDevice>(value = destination.devices.value.single())
            val copiedChild = copied.state.value.groups.single().chain.devices.value.single()
            assertNotEquals(illegal = group.selectionUUID, actual = copied.selectionUUID)
            assertNotEquals(illegal = child.selectionUUID, actual = copiedChild.selectionUUID)
            copiedChild.setMuted(muted = true)
            assertFalse(actual = child.isMuted)
            UndoManager.undo()
            assertTrue(actual = destination.devices.value.isEmpty())
            UndoManager.redo()
            assertEquals(expected = copied, actual = destination.devices.value.single())
        } finally {
            source.dispose()
            destination.dispose()
        }
    }

    @Test
    fun devicePasteIsBeforeSelectionAndPreservesOrderAndUndo() {
        WorkspaceRepository.switchMode(mode = LightsChainWorkspaceMode(), undoable = false)
        val left = FlipChainDevice()
        val selected = FlipChainDevice()
        val chain = Chain().apply { addAll(devicesToAdd = listOf(left, selected), fromUser = false) }
        SelectionManager.select(element = Selectable.ChainDevice(parent = chain, device = selected))
        ClipboardManager.setClipboardData(data = ClipboardData.ChainDevice(
            states = listOf(FlipChainDeviceState(mode = FlipChainDeviceState.FlipMode.VERTICAL), FlipChainDeviceState(mode = FlipChainDeviceState.FlipMode.DIAGONAL_PLUS)),
            type = ClipboardData.ChainDevice.ChainType.Lights,
        ))
        try {
            ClipboardManager.paste()
            assertEquals(expected = left, actual = chain.devices.value.first())
            assertEquals(expected = selected, actual = chain.devices.value.last())
            assertEquals(expected = listOf(FlipChainDeviceState.FlipMode.VERTICAL, FlipChainDeviceState.FlipMode.DIAGONAL_PLUS), actual = chain.devices.value.subList(1, 3).map { (it as FlipChainDevice).state.value.mode })
            UndoManager.undo()
            assertEquals(expected = listOf(left, selected), actual = chain.devices.value)
            UndoManager.redo()
            assertEquals(expected = 4, actual = chain.devices.value.size)
        } finally {
            chain.dispose()
        }
    }

    @Test
    fun muteDevicesChainsAndClipsSurvivesSerializationAndUndo() {
        val device = FlipChainDevice()
        val chain = Chain().apply { add(device = device, fromUser = false) }
        try {
            EditorMuteCommands.setDevices(devices = listOf(device), muted = true)
            assertTrue(actual = device.isMuted)
            device.state.value = device.state.value.copy(mode = FlipChainDeviceState.FlipMode.VERTICAL)
            assertTrue(actual = device.isMuted)
            UndoManager.undo()
            assertFalse(actual = device.isMuted)
            UndoManager.redo()
            EditorMuteCommands.toggleChains(chains = listOf(chain))
            val bytes = AmethystProtoBuf.encodeToByteArray(serializer = StateChain.serializer(), value = StateChain.pack(chain = chain))
            val restored = AmethystProtoBuf.decodeFromByteArray(deserializer = StateChain.serializer(), bytes = bytes).unpack()
            try {
                assertTrue(actual = restored.isMuted)
                assertTrue(actual = restored.devices.value.single().isMuted)
            } finally {
                restored.dispose()
            }
            val track = MidiTimelineTrack().apply { entries[0L] = MidiEntry(startTimeMs = 0L, durationMs = 100L) }
            TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(track))
            TimelineCommandSurface.toggleEntriesMute(targets = listOf(TimelineClipKey(trackIndex = 0, entryStartMs = 0L)))
            val muted = (TimelineRepository.tracks.value.single() as MidiTimelineTrack).entries.getValue(0L)
            assertTrue(actual = muted.isMuted)
            assertTrue(actual = AmethystProtoBuf.decodeFromByteArray(deserializer = MidiEntry.serializer(), bytes = AmethystProtoBuf.encodeToByteArray(serializer = MidiEntry.serializer(), value = muted)).isMuted)
            UndoManager.undo()
            assertFalse(actual = (TimelineRepository.tracks.value.single() as MidiTimelineTrack).entries.getValue(0L).isMuted)
        } finally {
            chain.dispose()
        }
    }

    @Test
    fun clipCopyDropKeepsSourceMakesIndependentNotesAndRejectsIncompatibleTargets() {
        val entry = MidiEntry(startTimeMs = 0L, durationMs = 100L, notes = listOf(MidiNote.withPaint(device = 0, pitch = 11, color = androidx.compose.ui.graphics.Color.Red, startTimeMs = 0L, durationMs = 100L)))
        val source = MidiTimelineTrack().apply { entries[0L] = entry }
        TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(source, MidiTimelineTrack(), AudioTimelineTrack()))
        val command = TimelineEditCommand.MoveEntries(entryKeys = listOf(TimelineClipKey(trackIndex = 0, entryStartMs = 0L)), anchorKey = TimelineClipKey(trackIndex = 0, entryStartMs = 0L), targetTrackIndex = 1, targetStartMs = 250L, copy = true)
        assertTrue(actual = TimelineCommandExecutor.execute(command = command).didChange)
        assertEquals(expected = entry, actual = (TimelineRepository.tracks.value[0] as MidiTimelineTrack).entries.getValue(0L))
        val copied = (TimelineRepository.tracks.value[1] as MidiTimelineTrack).entries.getValue(250L)
        assertNotEquals(illegal = entry.notes.single().noteId, actual = copied.notes.single().noteId)
        UndoManager.undo()
        assertTrue(actual = (TimelineRepository.tracks.value[1] as MidiTimelineTrack).entries.isEmpty())
        UndoManager.redo()
        assertEquals(expected = copied, actual = (TimelineRepository.tracks.value[1] as MidiTimelineTrack).entries.getValue(250L))
        assertFalse(actual = TimelineCommandExecutor.execute(command = command.copy(targetTrackIndex = 2)).didChange)
    }
}
