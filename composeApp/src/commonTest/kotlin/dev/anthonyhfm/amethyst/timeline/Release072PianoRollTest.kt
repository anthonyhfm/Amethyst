package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipContext
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import dev.anthonyhfm.amethyst.timeline.transforms.PianoRollTransforms
import dev.anthonyhfm.amethyst.timeline.ui.pianoroll.parsePinchValue
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.random.Random
import kotlin.test.*

class Release072PianoRollTest {
    private lateinit var previousMode: dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
    private lateinit var mode: PianoRollWorkspaceMode
    private lateinit var original: List<MidiNote>

    @BeforeTest
    fun setup() {
        previousMode = WorkspaceRepository.mode.value
        SelectionManager.clear()
        UndoManager.clear()
        original = listOf(
            MidiNote.withPaint(device = 0, pitch = 11, color = Color.Red, startTimeMs = 50L, durationMs = 100L),
            MidiNote.withPaint(device = 1, pitch = 42, color = Color.Blue, startTimeMs = 350L, durationMs = 250L),
        )
        val entry = MidiEntry(startTimeMs = 1000L, durationMs = 4000L, notes = original)
        val track = MidiTimelineTrack().apply { entries[entry.startTimeMs] = entry }
        TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(track))
        mode = PianoRollWorkspaceMode().apply {
            bindClipContext(context = TimelineClipContext.midi(trackIndex = 0, entry = entry), entry = entry)
        }
        WorkspaceRepository.switchMode(mode = mode, undoable = false)
        mode.selectAllNotes()
    }

    @AfterTest
    fun cleanup() {
        WorkspaceRepository.switchMode(mode = previousMode, undoable = false)
        TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
        SelectionManager.clear()
        UndoManager.clear()
    }

    private fun notes(): List<MidiNote> = (TimelineRepository.tracks.value[0] as MidiTimelineTrack).entries.getValue(1000L).notes

    @Test
    fun gradientSpreadUsesCurrentNotesAndSupportsUndoAndCopy() {
        val third = original.last().copy(startTimeMs = 700L, noteId = "third")
        val originalEntry = mode.currentEntry!!
        TimelineRepository.setMidiTrackEntries(trackIndex = 0, midiEntries = listOf(originalEntry.copy(notes = original + third)))
        mode.syncCurrentEntry(entry = (TimelineRepository.tracks.value[0] as MidiTimelineTrack).entries.getValue(1000L))
        mode.selectAllNotes()
        assertTrue(actual = mode.spreadSelectedGradient())
        assertEquals(expected = 0.5f, actual = notes()[1].led.red)
        UndoManager.undo()
        assertEquals(expected = original + third, actual = notes())
        assertTrue(actual = mode.spreadSelectedGradient(copy = true))
        assertEquals(expected = 6, actual = notes().size)
        assertEquals(expected = original + third, actual = notes().take(3))
        UndoManager.undo()
        assertEquals(expected = original + third, actual = notes())
    }

    @Test
    fun scrambleUndoLengthUsesRestoredNotesAndSelection() {
        mode.applySelectedTransform(transform = { PianoRollTransforms.scrambleAll(notes = it, random = Random(seed = 17)) })
        UndoManager.undo()
        assertEquals(expected = original, actual = notes())
        assertEquals(expected = original, actual = SelectionManager.selections.value.filterIsInstance<Selectable.PianoRollNote>().map { it.note })
        mode.applySelectedTransform(transform = PianoRollTransforms::doubleLength)
        val expected = PianoRollTransforms.doubleLength(notes = original)
        assertEquals(expected = expected, actual = notes())
        assertFalse(actual = UndoManager.canRedo())
        UndoManager.undo()
        assertEquals(expected = original, actual = notes())
        UndoManager.redo()
        assertEquals(expected = expected, actual = notes())
    }

    @Test
    fun copyPasteKeepsAttributesAndIsOneUndoStep() {
        assertTrue(actual = mode.copySelectedNotes())
        val copied = assertIs<ClipboardData.PianoRollNotes>(value = ClipboardManager.clipboardData.value)
        mode.selectedTimeMs = 1700L
        mode.pasteNotes(pastedNotes = copied.notes)
        val pasted = notes().drop(2)
        assertEquals(expected = listOf(1700L, 2000L), actual = pasted.map { it.startTimeMs })
        original.zip(pasted).forEach { (before, after) ->
            assertEquals(expected = before, actual = after.copy(startTimeMs = before.startTimeMs, noteId = before.noteId))
            assertNotEquals(illegal = before.noteId, actual = after.noteId)
        }
        UndoManager.undo()
        assertEquals(expected = original, actual = notes())
        UndoManager.redo()
        assertEquals(expected = original + pasted, actual = notes())
    }

    @Test
    fun allTransformsSupportIndependentCopiesAndUndoRedo() {
        val transforms: List<(List<MidiNote>) -> List<MidiNote>> = listOf(
            PianoRollTransforms::doubleLength, PianoRollTransforms::halveLength,
            PianoRollTransforms::doubleSpeed, PianoRollTransforms::halveSpeed,
            PianoRollTransforms::shiftUp, PianoRollTransforms::shiftDown,
            PianoRollTransforms::shiftLeft, PianoRollTransforms::shiftRight,
            PianoRollTransforms::rotateCW, PianoRollTransforms::rotateCCW, PianoRollTransforms::rotate180,
            PianoRollTransforms::mirrorHorizontal, PianoRollTransforms::mirrorVertical,
            { PianoRollTransforms.scrambleAll(notes = it, random = Random(seed = 7)) },
            { PianoRollTransforms.scrambleByPad(notes = it, random = Random(seed = 7)) },
            { PianoRollTransforms.pinch(notes = it, factor = 1f) },
        )
        transforms.forEach { transform ->
            mode.selectAllNotes()
            assertTrue(actual = mode.applySelectedTransform(transform = transform, copy = true))
            val copied = notes().drop(original.size)
            assertEquals(expected = original, actual = notes().take(original.size))
            assertEquals(expected = transform(original), actual = copied.zip(transform(original)) { actual, expected -> actual.copy(noteId = expected.noteId) })
            assertEquals(expected = notes().size, actual = notes().map { it.noteId }.distinct().size)
            UndoManager.undo()
            assertEquals(expected = original, actual = notes())
            UndoManager.redo()
            assertEquals(expected = original + copied, actual = notes())
            UndoManager.undo()
        }
    }

    @Test
    fun emptySelectionDoesNotTransformAndPinchSettingsSurviveEdits() {
        mode.pinchValue = 1.25f
        mode.pinchBilateral = true
        SelectionManager.clear()
        assertFalse(actual = mode.applySelectedTransform(transform = PianoRollTransforms::doubleLength))
        mode.selectAllNotes()
        mode.applySelectedTransform(transform = { PianoRollTransforms.pinch(notes = it, factor = mode.pinchValue, bilateral = mode.pinchBilateral) })
        UndoManager.undo()
        assertEquals(expected = 1.25f, actual = mode.pinchValue)
        assertTrue(actual = mode.pinchBilateral)
    }

    @Test
    fun pinchAcceptsCommaAndClampsWithoutAcceptingNonFiniteText() {
        assertEquals(expected = 1.25f, actual = parsePinchValue(text = "1,25"))
        assertEquals(expected = 2f, actual = parsePinchValue(text = "99"))
        assertEquals(expected = -2f, actual = parsePinchValue(text = "-99"))
        listOf("", "x", "NaN", "Infinity").forEach { assertNull(actual = parsePinchValue(text = it)) }
    }

    @Test
    fun gradientSpreadHasVisibleMidpointAndKeepsTiming() {
        val input = original + original.last().copy(startTimeMs = 700L)
        val spread = PianoRollTransforms.gradientSpread(
            notes = input,
            gradientStops = listOf(
                NoteGradientStop(position = 0f, r = 1f, g = 0f, b = 0f),
                NoteGradientStop(position = 1f, r = 0f, g = 0f, b = 1f),
            ),
        )
        assertEquals(expected = 0.5f, actual = spread[1].led.red)
        assertEquals(expected = 0.5f, actual = spread[1].led.blue)
        assertEquals(expected = input.map { it.startTimeMs }, actual = spread.map { it.startTimeMs })
    }
}
