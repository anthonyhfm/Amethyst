package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipKey
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.ui.TimelineClipDragCoordinator
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.timeline.utils.computeVisibleClipWindowPx
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimelineClipMoveEngineTest {
    @BeforeTest
    fun resetTimeline() {
        TimelineRepository.updateTracksSnapshot(emptyList())
        SelectionManager.clear()
    }

    @Test
    fun audioMoveOverwritesAndSplitsDestinationSampleAccurately() {
        val source = AudioTimelineTrack().apply { entries[0L] = audioEntry(0L, 200L, "moving") }
        val destination = AudioTimelineTrack().apply { entries[0L] = audioEntry(0L, 1_000L, "bed") }
        TimelineRepository.updateTracksSnapshot(listOf(source, destination))

        val result = TimelineCommandExecutor.execute(
            TimelineEditCommand.MoveEntries(
                entryKeys = listOf(TimelineClipKey(0, 0L)),
                anchorKey = TimelineClipKey(0, 0L),
                targetTrackIndex = 1,
                targetStartMs = 300L,
            )
        )

        assertTrue(result.didChange)
        val updatedSource = TimelineRepository.tracks.value[0] as AudioTimelineTrack
        val updatedDestination = TimelineRepository.tracks.value[1] as AudioTimelineTrack
        assertTrue(updatedSource.entries.isEmpty())
        assertEquals(listOf(0L, 300L, 500L), updatedDestination.entries.keys.sorted())
        assertEquals(300L, updatedDestination.entries.getValue(0L).durationMs)
        assertEquals(500L, updatedDestination.entries.getValue(500L).clipStartSample)
        assertEquals(500L, updatedDestination.entries.getValue(500L).durationMs)
    }

    @Test
    fun midiMoveSplitsAndRebasesNotesInDestination() {
        val moving = MidiEntry(startTimeMs = 0L, durationMs = 200L, name = "moving")
        val crossingNote = MidiNote.withColor(
            device = 0,
            pitch = 11,
            color = Color.Red,
            startTimeMs = 200L,
            durationMs = 600L,
        )
        val source = MidiTimelineTrack().apply { entries[0L] = moving }
        val destination = MidiTimelineTrack().apply {
            entries[0L] = MidiEntry(0L, 1_000L, notes = listOf(crossingNote), name = "bed")
        }
        TimelineRepository.updateTracksSnapshot(listOf(source, destination))

        TimelineCommandExecutor.execute(
            TimelineEditCommand.MoveEntries(
                entryKeys = listOf(TimelineClipKey(0, 0L)),
                anchorKey = TimelineClipKey(0, 0L),
                targetTrackIndex = 1,
                targetStartMs = 400L,
            )
        )

        val updated = TimelineRepository.tracks.value[1] as MidiTimelineTrack
        assertEquals(listOf(0L, 400L, 600L), updated.entries.keys.sorted())
        assertEquals(200L, updated.entries.getValue(0L).notes.single().durationMs)
        assertEquals(0L, updated.entries.getValue(600L).notes.single().startTimeMs)
        assertEquals(200L, updated.entries.getValue(600L).notes.single().durationMs)
    }

    @Test
    fun groupMoveKeepsAbsoluteTrackAndTimeOffsets() {
        val tracks = listOf(
            MidiTimelineTrack().apply { entries[100L] = MidiEntry(100L, 100L) },
            MidiTimelineTrack().apply { entries[300L] = MidiEntry(300L, 100L) },
            MidiTimelineTrack(),
            MidiTimelineTrack(),
        )
        val command = TimelineEditCommand.MoveEntries(
            entryKeys = listOf(TimelineClipKey(0, 100L), TimelineClipKey(1, 300L)),
            anchorKey = TimelineClipKey(0, 100L),
            targetTrackIndex = 2,
            targetStartMs = 500L,
        )

        val preview = TimelineClipMoveEngine.preview(command, tracks)

        assertTrue(preview.isValid)
        assertEquals(listOf(2, 3), preview.placements.map { it.targetTrackIndex })
        assertEquals(listOf(500L, 700L), preview.placements.map { it.targetStartMs })
    }

    @Test
    fun groupMoveIsRejectedWhenAnyTargetTrackHasWrongType() {
        val tracks = listOf(
            MidiTimelineTrack().apply { entries[0L] = MidiEntry(0L, 100L) },
            MidiTimelineTrack().apply { entries[0L] = MidiEntry(0L, 100L) },
            MidiTimelineTrack(),
            AudioTimelineTrack(),
        )
        val preview = TimelineClipMoveEngine.preview(
            TimelineEditCommand.MoveEntries(
                entryKeys = listOf(TimelineClipKey(0, 0L), TimelineClipKey(1, 0L)),
                anchorKey = TimelineClipKey(0, 0L),
                targetTrackIndex = 2,
                targetStartMs = 0L,
            ),
            tracks,
        )

        assertFalse(preview.isValid)
    }

    @Test
    fun crossTrackOverwriteIsRestoredByOneUndo() {
        val source = AudioTimelineTrack().apply { entries[0L] = audioEntry(0L, 200L, "moving") }
        val destination = AudioTimelineTrack().apply { entries[0L] = audioEntry(0L, 1_000L, "bed") }
        TimelineRepository.updateTracksSnapshot(listOf(source, destination))
        TimelineCommandExecutor.execute(
            TimelineEditCommand.MoveEntries(
                entryKeys = listOf(TimelineClipKey(0, 0L)),
                anchorKey = TimelineClipKey(0, 0L),
                targetTrackIndex = 1,
                targetStartMs = 300L,
            )
        )

        UndoManager.undo()

        val restoredSource = TimelineRepository.tracks.value[0] as AudioTimelineTrack
        val restoredDestination = TimelineRepository.tracks.value[1] as AudioTimelineTrack
        assertEquals(listOf(0L), restoredSource.entries.keys.toList())
        assertEquals(listOf(0L), restoredDestination.entries.keys.toList())
        assertEquals(1_000L, restoredDestination.entries.getValue(0L).durationMs)
    }

    @Test
    fun dragProjectionIncludesScrollAccumulatedDuringGesture() {
        TimelineRepository.updateTracksSnapshot(
            listOf(AudioTimelineTrack().apply { entries[0L] = audioEntry(0L, 100L, "clip") })
        )
        val coordinator = TimelineClipDragCoordinator().apply {
            updateLaneBounds(0, Rect(0f, 0f, 1_000f, 100f))
            begin(
                anchorKey = TimelineClipKey(0, 0L),
                pointerRoot = Offset(10f, 50f),
                viewport = EditorViewportState(zoomX = 1f, scrollX = 0f),
                tracks = TimelineRepository.tracks.value,
            )
            updatePointer(Offset(20f, 50f))
        }

        val command = coordinator.buildCommand(
            viewport = EditorViewportState(zoomX = 1f, scrollX = 100f),
            bpm = 120.0,
            gridType = GridUtils.GridType.NoGrid,
            snapEnabled = false,
        )

        assertEquals(110L, command?.targetStartMs)
    }

    @Test
    fun dragProjectionHardSnapsToActiveMusicalGrid() {
        TimelineRepository.updateTracksSnapshot(
            listOf(MidiTimelineTrack().apply { entries[0L] = MidiEntry(0L, 100L) })
        )
        val coordinator = TimelineClipDragCoordinator().apply {
            updateLaneBounds(0, Rect(0f, 0f, 1_000f, 100f))
            begin(
                anchorKey = TimelineClipKey(0, 0L),
                pointerRoot = Offset(0f, 50f),
                viewport = EditorViewportState(zoomX = 1f),
                tracks = TimelineRepository.tracks.value,
            )
            updatePointer(Offset(260f, 50f))
        }

        val command = coordinator.buildCommand(
            viewport = EditorViewportState(zoomX = 1f),
            bpm = 120.0,
            gridType = GridUtils.GridType.Fixed._1_4,
            snapEnabled = true,
        )

        assertEquals(500L, command?.targetStartMs)
        val freeCommand = coordinator.buildCommand(
            viewport = EditorViewportState(zoomX = 1f),
            bpm = 120.0,
            gridType = GridUtils.GridType.Fixed._1_4,
            snapEnabled = false,
        )
        assertEquals(260L, freeCommand?.targetStartMs)
    }

    @Test
    fun activeDragRetainsOffscreenGestureAnchor() {
        val viewport = EditorViewportState(viewportWidth = 500f, contentWidth = 5_000f, scrollX = 1_000f)

        assertEquals(
            null,
            computeVisibleClipWindowPx(0, 100, viewport, retainOffscreen = false),
        )
        val retained = computeVisibleClipWindowPx(0, 100, viewport, retainOffscreen = true)
        assertEquals(1, retained?.visibleWidthPx)
    }

    private fun audioEntry(startMs: Long, durationMs: Long, name: String) = AudioEntry(
        startTimeMs = startMs,
        durationMs = durationMs,
        fileName = "$name.wav",
        sourceId = name,
        clipStartSample = 0L,
        clipEndSample = durationMs,
        sampleRate = 1_000,
        channels = 1,
        bitDepth = 16,
        name = name,
    )
}
