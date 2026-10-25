package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.copyWithShiftedStartMs
import dev.anthonyhfm.amethyst.timeline.ui.views.TimelineLane
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class TimelineAudioContextMenuTest {
    @BeforeTest
    @AfterTest
    fun reset() {
        TimelineAudioConsolidation.reset()
        TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
        AudioLibraryRepository.clear()
        SelectionManager.clear()
    }

    @Test
    fun rightClickOnEitherSelectedClipHeaderOrWaveformOpensMenuAndPreservesBothClips() {
        for (x in listOf(50f, 250f)) {
            for (y in listOf(8f, 55f)) {
                val selections = selectedClips()
                clickLane(
                    selections = selections,
                    position = Offset(x = x, y = y),
                    expectMenu = true,
                )
                assertEquals(expected = selections, actual = SelectionManager.selections.value)
            }
        }
    }

    @Test
    fun rightClickInGapBetweenSelectedClipsOpensMenuAndPreservesSelection() {
        val selections = selectedClips()
        clickLane(selections = selections, position = Offset(x = 150f, y = 55f), expectMenu = true)
        assertEquals(expected = selections, actual = SelectionManager.selections.value)
        assertTrue(actual = TimelineCommandSurface.canConsolidateSelection())
    }

    @Test
    fun rightClickInRangeContainingAudioOpensMenuEvenOverTheGap() {
        val selections = listOf(Selectable.TimelineRange(trackIndex = 0, startMs = 50L, endMs = 250L))
        clickLane(selections = selections, position = Offset(x = 150f, y = 55f), expectMenu = true)
        assertEquals(expected = selections, actual = SelectionManager.selections.value)
    }

    @Test
    fun rightClickOnUnselectedClipSelectsItAndOpensMenu() {
        clickLane(selections = emptyList(), position = Offset(x = 250f, y = 8f), expectMenu = true)
        val selected = SelectionManager.selections.value.single() as Selectable.TimelineEntryItem
        assertEquals(expected = 200L, actual = selected.entryStartMs)
    }

    @Test
    fun rightClickInEmptySpaceWithoutSelectionDoesNotOpenMenu() {
        clickLane(selections = emptyList(), position = Offset(x = 150f, y = 55f), expectMenu = false)
        assertTrue(actual = SelectionManager.selections.value.isEmpty())
    }

    @Test
    fun rightClickInRangeWithoutAudioDoesNotOpenMenu() {
        val selections = listOf(Selectable.TimelineRange(trackIndex = 0, startMs = 120L, endMs = 180L))
        clickLane(selections = selections, position = Offset(x = 150f, y = 55f), expectMenu = false)
        assertEquals(expected = selections, actual = SelectionManager.selections.value)
    }

    @Test
    fun rightClickOutsideSelectionDoesNotOpenMenuOrDeselectClips() {
        val selections = selectedClips()
        clickLane(selections = selections, position = Offset(x = 350f, y = 55f), expectMenu = false)
        assertEquals(expected = selections, actual = SelectionManager.selections.value)
    }

    @Test
    fun leftClickInGapStillReplacesClipSelectionWithTimeSelection() {
        clickLane(
            selections = selectedClips(),
            position = Offset(x = 150f, y = 55f),
            expectMenu = false,
            secondary = false,
        )
        assertTrue(actual = SelectionManager.selections.value.single() is Selectable.TimelineTime)
    }

    private fun selectedClips(): List<Selectable> {
        return listOf(
            Selectable.TimelineEntryItem(trackIndex = 0, entryStartMs = 0L),
            Selectable.TimelineEntryItem(trackIndex = 0, entryStartMs = 200L),
        )
    }

    private fun clickLane(
        selections: List<Selectable>,
        position: Offset,
        expectMenu: Boolean,
        secondary: Boolean = true,
    ) {
        val source = AudioLibraryRepository.add(
            source = AudioSource(
                id = "context-menu-audio",
                fileName = "audio.wav",
                rawData = ByteArray(size = 200),
                sampleRate = 1_000,
                channels = 1,
                bitDepth = 16,
            ),
        )
        val first = AudioEntry(
            startTimeMs = 0L,
            durationMs = 100L,
            sourceId = source.id,
            fileName = source.fileName,
            clipEndSample = 100L,
            sampleRate = 1_000,
            channels = 1,
            bitDepth = 16,
        )
        val track = AudioTimelineTrack().apply {
            entries[0L] = first
            entries[200L] = first.copyWithShiftedStartMs(startTimeMs = 200L)
        }
        TimelineRepository.updateTracksSnapshot(updatedTracks = listOf(track))
        SelectionManager.replaceSelections(updatedSelections = selections)
        val shortcutBlocks = WorkspaceRepository.editorShortcutBlockCount
        val recomposer = FrameRecomposer(coroutineContext = Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(
            frameRecomposer = recomposer,
            size = IntSize(width = 400, height = 160),
        )
        try {
            scene.setContent {
                AmethystTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        TimelineLane(
                            track = track,
                            trackIndex = 0,
                            viewport = EditorViewportState(zoomX = 1f, viewportWidth = 400f, contentWidth = 400f),
                            selectedTimeMs = null,
                            selectedEntryStarts = selections.filterIsInstance<Selectable.TimelineEntryItem>()
                                .mapTo(destination = mutableSetOf()) { it.entryStartMs },
                            onSelectEntry = { start ->
                                SelectionManager.select(element = Selectable.TimelineEntryItem(trackIndex = 0, entryStartMs = start))
                            },
                            onSelectTime = { time ->
                                SelectionManager.select(element = Selectable.TimelineTime(trackIndex = 0, timeMs = time))
                            },
                        )
                    }
                }
            }
            recomposer.performFrame(frameTimeNanos = 0L)
            scene.measureAndLayout()
            scene.sendPointerEvent(
                eventType = PointerEventType.Press,
                position = position,
                buttons = PointerButtons(isPrimaryPressed = !secondary, isSecondaryPressed = secondary),
            )
            scene.sendPointerEvent(
                eventType = PointerEventType.Release,
                position = position,
                buttons = PointerButtons(),
            )
            recomposer.performFrame(frameTimeNanos = 16_000_000L)
            scene.measureAndLayout()
            assertEquals(
                expected = shortcutBlocks + if (expectMenu) 1 else 0,
                actual = WorkspaceRepository.editorShortcutBlockCount,
                message = "Context menu at $position with selection $selections",
            )
        } finally {
            scene.close()
            recomposer.close()
        }
        assertEquals(expected = shortcutBlocks, actual = WorkspaceRepository.editorShortcutBlockCount)
    }
}
