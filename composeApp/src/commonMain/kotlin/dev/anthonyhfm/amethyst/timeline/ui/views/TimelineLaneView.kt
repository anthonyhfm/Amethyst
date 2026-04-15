package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.TimelineViewModel
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.timelineTrackRows
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.timeline.viewport.wheelZoomScaleFactor
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTransformGestures
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import androidx.compose.ui.input.pointer.isMetaPressed
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.timeline.ui.components.PlayheadCursor
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme

@Composable
fun TimelineLaneView(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel,
    onOpenMidiEntryAtTime: (trackIndex: Int, timeMs: Long) -> Unit = { _, _ -> }
) {
    val tracks by viewModel.tracks.collectAsState()
    // Single atomic viewport read — zoom and scroll always come from the same snapshot.
    val viewportState by viewModel.viewport.collectAsState()
    val playheadPositionMs by viewModel.playheadPositionMs.collectAsState()
    val minTimelinePx = 12_000f
    val timelinePaddingPx = 1000f
    val minZoomLevel = 0.0025f
    val maxZoomLevel = 5f
    val maxDurationMs = tracks.maxOfOrNull { track ->
        when (track) {
            is AudioTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            is MidiTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            else -> 0L
        }
    } ?: 0L
    // desiredWidthPx is the TRUE content extent — no 260 k cap. clamp() in EditorViewportState
    // uses this value so scrollX is never pinned against a fake smaller bound.
    val desiredWidthPx = (maxDurationMs.toDouble() * viewportState.zoomX.toDouble() + timelinePaddingPx.toDouble()).coerceAtLeast(minTimelinePx.toDouble()).toFloat()
    var viewportWidthPx by remember { mutableStateOf(0) }
    // Build the single authoritative viewport for all renderers in this lane view.
    // Merge the ViewModel-owned scroll+zoom with locally-derived layout dimensions.
    // contentWidth uses the true uncapped desiredWidthPx so clamp() computes correct
    // max-scroll bounds — no more artificial 260 k ceiling.
    val renderViewport = viewportState.copy(
        viewportWidth = viewportWidthPx.toFloat(),
        contentWidth = desiredWidthPx,
        minZoomX = minZoomLevel,
        maxZoomX = maxZoomLevel,
    )
    // Keep a rememberUpdatedState reference so pointer-input closures (keyed on Unit) always
    // read the latest viewport without restarting their event loops.
    val currentViewport = rememberUpdatedState(renderViewport)
    val timelinePalette = TimelineTheme.palette
    val timelineDimensions = TimelineTheme.dimensions
    var lastPointerX by remember { mutableStateOf<Float?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val zoomEndJobHolder = remember { arrayOfNulls<Job>(1) }


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(timelinePalette.canvas)
            .onSizeChanged { viewportWidthPx = it.width }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointerX = event.changes.firstOrNull()?.position?.x
                        if (pointerX != null && event.type != PointerEventType.Exit) {
                            lastPointerX = pointerX
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val altOrMeta = event.keyboardModifiers.isMetaPressed
                            if (!altOrMeta) {
                                val change = event.changes.firstOrNull()
                                val deltaX = (change?.scrollDelta?.x ?: 0f) + (change?.scrollDelta?.y ?: 0f)
                                if (deltaX != 0f) {
                                    viewModel.updateViewport(currentViewport.value.panBy(deltaX * 40f))
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val altOrMeta = event.keyboardModifiers.isMetaPressed
                            val change = event.changes.firstOrNull()
                            val deltaY = change?.scrollDelta?.y ?: 0f
                            if (altOrMeta && deltaY != 0f) {
                                // Directly apply each scroll event as a zoom step anchored at the
                                // cursor position.  No accumulation, no lerp — the anchor is always
                                // pinned by viewport.zoomAtX() so there is no left-drift.
                                val scaleDelta = wheelZoomScaleFactor(-deltaY)
                                val cursorX = (lastPointerX ?: change?.position?.x ?: 0f).coerceAtLeast(0f)
                                val updated = currentViewport.value.zoomAtX(scaleDelta, cursorX)
                                if (updated.zoomX != currentViewport.value.zoomX) {
                                    // Atomic: zoom + scroll emitted together in one StateFlow update.
                                    viewModel.updateViewport(updated)
                                    // Debounce: emit one log when zoom input stops for ~150 ms.
                                    zoomEndJobHolder[0]?.cancel()
                                    zoomEndJobHolder[0] = coroutineScope.launch {
                                        delay(150)
                                        println("[Amethyst/Zoom] TimelineLaneView wheel — zoom end, zoomX=${updated.zoomX}")
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    if (gestureZoom != 1f) {
                        // Pass gestureZoom directly — no lerp — so the content under the
                        // centroid stays locked each frame.
                        val cursorX = centroid.x.coerceAtLeast(0f)
                        val updated = currentViewport.value.zoomAtX(gestureZoom, cursorX)
                        if (updated.zoomX != currentViewport.value.zoomX) {
                            // Atomic: zoom + scroll emitted together in one StateFlow update.
                            viewModel.updateViewport(updated)
                            // Debounce: emit one log when zoom input stops for ~150 ms.
                            zoomEndJobHolder[0]?.cancel()
                            zoomEndJobHolder[0] = coroutineScope.launch {
                                delay(150)
                                println("[Amethyst/Zoom] TimelineLaneView gesture — zoom end, zoomX=${updated.zoomX}")
                            }
                        }
                    } else if (pan.x != 0f) {
                        viewModel.updateViewport(currentViewport.value.panBy(-pan.x))
                    }
                }
            }
    ) {
        val selections by SelectionManager.selections.collectAsState()

        Column(
            verticalArrangement = Arrangement.spacedBy(timelineDimensions.laneSpacing)
        ) {
            tracks.timelineTrackRows().forEach { trackRow ->
                val index = trackRow.trackIndex
                val track = trackRow.track
                val laneSelectedTimeMs = selections.filterIsInstance<Selectable.TimelineTime>().firstOrNull { it.trackIndex == index }?.timeMs
                val laneSelectedEntries = selections.filterIsInstance<Selectable.TimelineEntryItem>().filter { it.trackIndex == index }

                TimelineLane(
                    track = track,
                    trackIndex = index,
                    nestingLevel = trackRow.nestingLevel,
                    viewport = renderViewport,
                    selectedTimeMs = laneSelectedTimeMs,
                    selectedEntryStarts = laneSelectedEntries.map { it.entryStartMs }.toSet(),
                    onDropInFile = { file ->
                        viewModel.addAudioFileToTrack(
                            trackIndex = index,
                            file = file,
                            at = playheadPositionMs
                        )
                    },
                    onSelectTime = { rawClickTimeMs ->
                        val snapped = GridUtils.snapToGrid(rawClickTimeMs.coerceAtLeast(0), renderViewport.zoomX, WorkspaceRepository.bpm.value, WorkspaceRepository.gridType.value)
                        SelectionManager.select(Selectable.TimelineTime(trackIndex = index, timeMs = snapped))
                    },
                    onSelectEntry = { entryStart ->
                        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = index, entryStartMs = entryStart))
                    },
                    onMoveEntry = { oldStart, newStart ->
                        when (track) {
                            is AudioTimelineTrack -> viewModel.moveAudioEntry(index, oldStart, newStart)
                            is MidiTimelineTrack -> viewModel.moveMidiEntry(index, oldStart, newStart)
                        }
                    },
                    onResizeEntry = { oldStart, newStart, newDuration ->
                        when (track) {
                            is MidiTimelineTrack -> viewModel.resizeMidiEntry(index, oldStart, newStart, newDuration)
                            else -> { /* ignore for audio for now */ }
                        }
                    },
                    onDoubleClickLane = { timeMs -> onOpenMidiEntryAtTime(index, timeMs) }
                )
            }
        }

        PlayheadCursor(
            positionMs = playheadPositionMs,
            viewport = renderViewport,
        )
    }
}
