package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.anthonyhfm.amethyst.timeline.TimelineViewModel
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import kotlin.math.abs
import kotlin.math.min
import androidx.compose.foundation.gestures.detectTransformGestures
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import androidx.compose.ui.input.pointer.isMetaPressed
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import androidx.compose.ui.platform.LocalDensity
import dev.anthonyhfm.amethyst.timeline.ui.components.PlayheadCursor

@Composable
fun TimelineLaneView(
    viewModel: TimelineViewModel,
    scrollState: ScrollState,
    selectionViewportRelative: Boolean = false,
    onDoubleClickLightsLane: (trackIndex: Int, timeMs: Long) -> Unit = { _, _ -> }
) {
    val tracks by viewModel.tracks.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val playheadPositionMs by viewModel.playheadPositionMs.collectAsState()
    val maxCanvasPx = 130_000f
    val minTimelinePx = 12_000f
    val timelinePaddingPx = 1000f
    val minZoomLevel = 0.0025f
    val maxZoomLevel = 5f
    val maxDurationMs = tracks.maxOfOrNull { track ->
        when (track) {
            is AudioTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            is MidiTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            is LightsTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            else -> 0L
        }
    } ?: 0L
    val desiredWidthPx = (maxDurationMs.toDouble() * zoomLevel.toDouble() + timelinePaddingPx.toDouble()).coerceAtLeast(minTimelinePx.toDouble()).toFloat()
    val contentWidthPx = desiredWidthPx.coerceAtMost(maxCanvasPx)
    val dynamicMaxZoom = if (maxDurationMs > 0) {
        min(maxZoomLevel, ((maxCanvasPx - timelinePaddingPx) / maxDurationMs.toFloat()).coerceAtLeast(minZoomLevel))
    } else maxZoomLevel
    val contentWidth = with(LocalDensity.current) { contentWidthPx.toDp() }
    val scope = rememberCoroutineScope()
    val zoomScrollSensitivity = 0.55f
    val zoomScrollLerpWeight = 0.6f
    val zoomGestureLerpWeight = 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var accumulatedDeltaY = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val altOrMeta = event.keyboardModifiers.isMetaPressed
                            val change = event.changes.firstOrNull()
                            val deltaY = change?.scrollDelta?.y ?: 0f
                            if (altOrMeta && deltaY != 0f) {
                                accumulatedDeltaY += deltaY
                                val normalizedTotal = (accumulatedDeltaY / 220f).coerceIn(-1f, 1f)
                                if (abs(normalizedTotal) >= 0.015f) {
                                    val currentZoom = viewModel.zoomLevel.value
                                    val deltaFactor = -normalizedTotal * zoomScrollSensitivity
                                    val targetScale = (1f + deltaFactor).coerceAtLeast(0.1f)
                                    val rawNewZoom = currentZoom * targetScale
                                    val smoothedZoom = currentZoom + (rawNewZoom - currentZoom) * zoomScrollLerpWeight
                                    val newZoom = smoothedZoom.coerceIn(minZoomLevel, dynamicMaxZoom)
                                    val cursorX = change?.position?.x ?: 0f
                                    val timeAtCursorMs = if (currentZoom > 0f) (scrollState.value.toDouble() + cursorX.toDouble()) / currentZoom.toDouble() else 0.0
                                    if (newZoom != currentZoom) {
                                        viewModel.setZoomLevel(newZoom)
                                        val targetScroll = (timeAtCursorMs * newZoom.toDouble() - cursorX.toDouble()).coerceAtLeast(0.0)
                                        scope.launch { scrollState.scrollTo(targetScroll.toInt()) }
                                    }
                                    accumulatedDeltaY *= 0.25f
                                }
                                event.changes.forEach { it.consume() }
                            } else if (!altOrMeta && accumulatedDeltaY != 0f) {
                                accumulatedDeltaY = 0f
                            }
                        }
                    }
                }
            }
            .pointerInput(zoomLevel) {
                detectTransformGestures { centroid, _, gestureZoom, _ ->
                    if (gestureZoom != 1f) {
                        val currentZoom = viewModel.zoomLevel.value
                        val rawNewZoom = currentZoom * gestureZoom
                        val smoothedZoom = currentZoom + (rawNewZoom - currentZoom) * zoomGestureLerpWeight
                        val newZoom = smoothedZoom.coerceIn(minZoomLevel, dynamicMaxZoom)
                        val cursorX = centroid.x
                        val timeAtCursorMs = if (currentZoom > 0f) (scrollState.value.toDouble() + cursorX.toDouble()) / currentZoom.toDouble() else 0.0
                        if (newZoom != currentZoom) {
                            viewModel.setZoomLevel(newZoom)
                            val targetScroll = (timeAtCursorMs * newZoom.toDouble() - cursorX.toDouble()).coerceAtLeast(0.0)
                            scope.launch { scrollState.scrollTo(targetScroll.toInt()) }
                        }
                    }
                }
            }
    ) {
        val selections by SelectionManager.selections.collectAsState()

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tracks.forEachIndexed { index, track ->
                val laneSelectedTimeMs = selections.filterIsInstance<Selectable.TimelineTime>().firstOrNull { it.trackIndex == index }?.timeMs
                val laneSelectedEntries = selections.filterIsInstance<Selectable.TimelineEntryItem>().filter { it.trackIndex == index }

                TimelineLane(
                    track = track,
                    zoomLevel = zoomLevel,
                    contentWidth = contentWidth,
                    scrollState = scrollState,
                    selectedTimeMs = laneSelectedTimeMs,
                    selectedEntryStarts = laneSelectedEntries.map { it.entryStartMs }.toSet(),
                    selectionViewportRelative = selectionViewportRelative,
                    onDropInFile = { file ->
                        viewModel.addAudioFileToTrack(
                            trackIndex = index,
                            file = file,
                            at = playheadPositionMs
                        )
                    },
                    onSelectTime = { rawClickTimeMs ->
                        val snapped = GridUtils.snapToGrid(rawClickTimeMs.coerceAtLeast(0), zoomLevel, WorkspaceRepository.bpm.value, WorkspaceRepository.gridType.value)
                        SelectionManager.select(Selectable.TimelineTime(trackIndex = index, timeMs = snapped))
                    },
                    onSelectEntry = { entryStart ->
                        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = index, entryStartMs = entryStart))
                    },
                    onMoveEntry = { oldStart, newStart ->
                        when (track) {
                            is AudioTimelineTrack -> viewModel.moveAudioEntry(index, oldStart, newStart)
                            is MidiTimelineTrack, is LightsTimelineTrack -> viewModel.moveMidiEntry(index, oldStart, newStart)
                        }
                    },
                    onDoubleClickLane = { timeMs -> onDoubleClickLightsLane(index, timeMs) }
                )
            }
        }

        PlayheadCursor(
            positionMs = playheadPositionMs,
            zoomLevel = zoomLevel,
            scrollState = scrollState
        )
    }
}
