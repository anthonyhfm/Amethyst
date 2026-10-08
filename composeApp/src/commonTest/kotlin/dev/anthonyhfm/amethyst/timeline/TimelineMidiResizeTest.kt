package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelStore
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineMidiResizeTest {
    private val store = ViewModelStore()
    private lateinit var viewModel: TimelineViewModel
    private lateinit var previousGrid: GridUtils.GridType

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher = UnconfinedTestDispatcher())
        previousGrid = WorkspaceRepository.gridType.value
        WorkspaceRepository.setGridType(type = GridUtils.GridType.NoGrid)
        TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
        SelectionManager.clear()
        UndoManager.clear()
        viewModel = TimelineViewModel()
        store.put(key = "midi-resize", viewModel = viewModel)
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
        SelectionManager.clear()
        UndoManager.clear()
        WorkspaceRepository.setGridType(type = previousGrid)
        Dispatchers.resetMain()
    }

    private fun note(startMs: Long, durationMs: Long): MidiNote {
        return MidiNote.withColor(
            device = 0,
            pitch = 11,
            color = Color.Red,
            startTimeMs = startMs,
            durationMs = durationMs,
        )
    }

    @Test
    fun leftTrimPreservesRetainedAbsoluteOnsetsClipsEdgesAndUndoRestoresAll() {
        val before = note(startMs = 50L, durationMs = 50L)
        val crossingLeft = note(startMs = 200L, durationMs = 200L)
        val retained = note(startMs = 500L, durationMs = 200L)
        val crossingRight = note(startMs = 1600L, durationMs = 300L)
        val after = note(startMs = 1800L, durationMs = 100L)
        val entry = MidiEntry(
            startTimeMs = 1000L,
            durationMs = 2000L,
            notes = listOf(before, crossingLeft, retained, crossingRight, after),
        )
        TimelineRepository.updateTracksSnapshot(
            updatedTracks = listOf(MidiTimelineTrack().apply { entries[entry.startTimeMs] = entry }),
        )
        viewModel.resizeMidiEntry(trackIndex = 0, oldStartMs = 1000L, newStartMs = 1300L, newDurationMs = 1400L)
        val resized = (viewModel.tracks.value.single() as MidiTimelineTrack).entries.getValue(1300L)
        val kept = resized.notes.single { it.noteId == retained.noteId }
        assertEquals(expected = entry.startTimeMs + retained.startTimeMs, actual = resized.startTimeMs + kept.startTimeMs)
        assertEquals(expected = 1400L, actual = resized.durationMs)
        assertEquals(expected = setOf(crossingLeft.noteId, retained.noteId, crossingRight.noteId), actual = resized.notes.map { it.noteId }.toSet())
        assertEquals(expected = 0L, actual = resized.notes.single { it.noteId == crossingLeft.noteId }.startTimeMs)
        assertEquals(expected = 100L, actual = resized.notes.single { it.noteId == crossingLeft.noteId }.durationMs)
        assertEquals(expected = 100L, actual = resized.notes.single { it.noteId == crossingRight.noteId }.durationMs)
        assertTrue(actual = resized.notes.all { it.startTimeMs >= 0L && it.durationMs > 0L && it.endTimeMs <= resized.durationMs })

        UndoManager.undo()
        val restored = (TimelineRepository.tracks.value.single() as MidiTimelineTrack).entries.getValue(1000L)
        assertEquals(expected = entry, actual = restored)
        UndoManager.redo()
        val redone = (TimelineRepository.tracks.value.single() as MidiTimelineTrack).entries.getValue(1300L)
        assertEquals(expected = resized, actual = redone)
    }

    @Test
    fun extendingLeftAddsEmptyTimeWithoutMovingExistingAbsoluteNotes() {
        val originalNote = note(startMs = 300L, durationMs = 200L)
        val entry = MidiEntry(startTimeMs = 1000L, durationMs = 1000L, notes = listOf(originalNote))
        TimelineRepository.updateTracksSnapshot(
            updatedTracks = listOf(MidiTimelineTrack().apply { entries[entry.startTimeMs] = entry }),
        )
        viewModel.resizeMidiEntry(trackIndex = 0, oldStartMs = 1000L, newStartMs = 500L, newDurationMs = 1500L)
        val resized = (viewModel.tracks.value.single() as MidiTimelineTrack).entries.getValue(500L)
        assertEquals(expected = 1300L, actual = resized.startTimeMs + resized.notes.single().startTimeMs)
        assertEquals(expected = entry.endTimeMs, actual = resized.endTimeMs)
        assertEquals(expected = originalNote.durationMs, actual = resized.notes.single().durationMs)
    }
}
