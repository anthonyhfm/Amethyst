package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.vinceglb.filekit.PlatformFile
import dev.anthonyhfm.amethyst.ui.dnd.fileDropTarget
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import dev.anthonyhfm.amethyst.timeline.ui.components.AudioClip
import dev.anthonyhfm.amethyst.timeline.ui.components.MidiClip
import dev.anthonyhfm.amethyst.timeline.ui.components.SelectionCursor
import dev.anthonyhfm.amethyst.timeline.ui.components.timelineGridOverlay
import dev.anthonyhfm.amethyst.timeline.utils.computeSnappedTime
import dev.anthonyhfm.amethyst.timeline.utils.computeStrictGridTime
import dev.anthonyhfm.amethyst.timeline.utils.findHeaderEntryHit
import dev.anthonyhfm.amethyst.timeline.utils.isPointInsideAnyEntry
import dev.anthonyhfm.amethyst.timeline.utils.trackIndexOf
import io.github.vinceglb.filekit.extension

@Composable
fun TimelineLane(
    track: TimelineTrack<*>,
    trackIndex: Int,
    zoomLevel: Float,
    contentWidth: androidx.compose.ui.unit.Dp,
    scrollState: ScrollState,
    selectedTimeMs: Long?,
    selectedEntryStarts: Set<Long> = emptySet(),
    selectionViewportRelative: Boolean = false,
    onDropInFile: (file: PlatformFile) -> Unit = {},
    onSelectTime: (Long) -> Unit = {},
    onSelectEntry: (Long) -> Unit = {},
    onMoveEntry: (oldStart: Long, newStart: Long) -> Unit = { _, _ -> },
    onResizeEntry: (oldStart: Long, newStart: Long, newDuration: Long) -> Unit = { _, _, _ -> },
    onDoubleClickLane: (Long) -> Unit = {}
) {
    val bpm by WorkspaceRepository.bpm.collectAsState()
    val gridType by WorkspaceRepository.gridType.collectAsState()
    var rangeStartMs by remember(track, zoomLevel) { mutableStateOf<Long?>(null) }
    var rangeEndMs by remember(track, zoomLevel) { mutableStateOf<Long?>(null) }
    var rangeActive by remember { mutableStateOf(false) }
    val selections by SelectionManager.selections.collectAsState()
    val selectedRange = selections.filterIsInstance<Selectable.TimelineRange>().firstOrNull { it.trackIndex == trackIndexOf(
        track
    )
    }
    val headerHeightPx = with(LocalDensity.current) { 24.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .timelineGridOverlay(
                zoomLevel = zoomLevel,
                bpm = bpm,
                gridType = gridType,
                drawBehind = true,
                scrollOffsetPx = scrollState.value.toFloat()
            )
            .fileDropTarget(
                onHover = { _: Boolean, _: Offset?, _: List<PlatformFile> -> },
                onDrop = { files: List<PlatformFile> ->
                    val audioFiles = files.filter { it.extension.lowercase() in AudioDecoder.getSupportedFormats() }
                    if (audioFiles.isNotEmpty()) onDropInFile(audioFiles.first())
                }
            )
            .horizontalScroll(scrollState)
            .pointerInput(track, zoomLevel, bpm, gridType) {
                var lastClickTime = 0L
                var lastClickPos: Offset? = null
                val doubleThresholdMs = 250L
                val moveTolerancePx = 20f
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            val change = event.changes.firstOrNull() ?: continue
                            val pos = change.position
                            val time = change.uptimeMillis
                            val headerHit = findHeaderEntryHit(
                                track,
                                pos.x,
                                pos.y,
                                zoomLevel,
                                scrollState.value.toFloat(),
                                headerHeightPx
                            )
                            if (headerHit != null) {
                                onSelectEntry(headerHit)
                                lastClickTime = 0L
                                lastClickPos = null
                                change.consume(); continue
                            }
                            val isLights = track is LightsTimelineTrack
                            val snappedMs = computeSnappedTime(pos.x, zoomLevel, bpm, gridType)
                            val isDouble = isLights && lastClickTime != 0L && (time - lastClickTime) in 1..doubleThresholdMs && lastClickPos?.let { prev ->
                                val dx = pos.x - prev.x
                                val dy = pos.y - prev.y
                                (dx * dx + dy * dy) <= moveTolerancePx * moveTolerancePx
                            } ?: false
                            if (isDouble) {
                                onDoubleClickLane(snappedMs)
                                lastClickTime = 0L
                                lastClickPos = null
                            } else {
                                onSelectTime(snappedMs)
                                if (isLights) {
                                    lastClickTime = time
                                    lastClickPos = pos
                                } else {
                                    lastClickTime = 0L
                                    lastClickPos = null
                                }
                            }
                            change.consume()
                        }
                    }
                }
            }
            .pointerInput(track, zoomLevel, bpm, gridType) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val headerHit = findHeaderEntryHit(
                            track,
                            offset.x,
                            offset.y,
                            zoomLevel,
                            scrollState.value.toFloat(),
                            headerHeightPx
                        )
                        if (headerHit != null) {
                            onSelectEntry(headerHit)
                            rangeActive = false
                            rangeStartMs = null
                            rangeEndMs = null
                            return@detectDragGestures
                        }
                        val startMsRawStrict = computeStrictGridTime(offset.x, scrollState, zoomLevel, bpm, gridType)
                        if (!isPointInsideAnyEntry(track, startMsRawStrict)) {
                            rangeStartMs = startMsRawStrict
                            rangeEndMs = startMsRawStrict
                            rangeActive = true
                        } else {
                            rangeActive = false
                            rangeStartMs = null
                            rangeEndMs = null
                        }
                    },
                    onDrag = { change, _ ->
                        if (rangeActive && rangeStartMs != null) {
                            val currentStrict =
                                computeStrictGridTime(change.position.x, scrollState, zoomLevel, bpm, gridType)
                            if (currentStrict != rangeEndMs) rangeEndMs = currentStrict
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (rangeActive && rangeStartMs != null && rangeEndMs != null) {
                            val start = rangeStartMs!!.coerceAtLeast(0L)
                            val end = rangeEndMs!!.coerceAtLeast(0L)
                            val normalizedStart = min(start, end)
                            val normalizedEnd = maxOf(start, end)
                            if (normalizedEnd > normalizedStart) {
                                SelectionManager.select(
                                    Selectable.TimelineRange(
                                        trackIndex = trackIndexOf(track),
                                        startMs = normalizedStart,
                                        endMs = normalizedEnd
                                    )
                                )
                            } else {
                                SelectionManager.select(
                                    Selectable.TimelineTime(
                                        trackIndex = trackIndexOf(track),
                                        timeMs = normalizedStart
                                    )
                                )
                            }
                        }
                        rangeActive = false
                    },
                    onDragCancel = {
                        rangeActive = false
                        rangeStartMs = null
                        rangeEndMs = null
                    }
                )
            }
    ) {
        val overlayStart = when {
            rangeActive && rangeStartMs != null && rangeEndMs != null -> min(rangeStartMs!!, rangeEndMs!!)
            selectedRange != null -> selectedRange.startMs
            else -> null
        }
        val overlayEnd = when {
            rangeActive && rangeStartMs != null && rangeEndMs != null -> maxOf(rangeStartMs!!, rangeEndMs!!)
            selectedRange != null -> selectedRange.endMs
            else -> null
        }
        if (overlayStart != null && overlayEnd != null && overlayEnd > overlayStart) {
            val startPx = (overlayStart.toDouble() * zoomLevel.toDouble() - scrollState.value.toDouble()).toFloat()
            val widthPx = ((overlayEnd - overlayStart).toDouble() * zoomLevel.toDouble()).toFloat()
            Box(
                modifier = Modifier
                    .offset { IntOffset(startPx.roundToInt(), 0) }
                    .width(with(LocalDensity.current) { widthPx.toDp() })
                    .height(120.dp)
                    .background(Color(0x5533AAFF))
                    .border(1.dp, Color(0xFF3399FF), RoundedCornerShape(2.dp))
                    .zIndex(0.5f)
            ) {}
        }
        Box(
            modifier = Modifier
                .width(contentWidth)
                .height(120.dp)
        ) {
            when (track) {
                is AudioTimelineTrack -> {
                    track.entries.values.sortedBy { it.startTimeMs }.forEach { audioEntry ->
                        androidx.compose.runtime.key(audioEntry.startTimeMs) {
                            val isSelectedEntry = audioEntry.startTimeMs in selectedEntryStarts
                            AudioClip(
                                audioEntry = audioEntry,
                                zoomLevel = zoomLevel,
                                isSelected = isSelectedEntry,
                                onSelectEntry = { onSelectEntry(audioEntry.startTimeMs) },
                                onMoveEntry = { newStart -> onMoveEntry(audioEntry.startTimeMs, newStart) },
                                gridIntervalMs = GridUtils.computeWithGridType(zoomLevel, bpm, gridType).intervalMs,
                                trackIndex = trackIndex,
                                entryStartMs = audioEntry.startTimeMs
                            )
                        }
                    }
                }
                is MidiTimelineTrack -> {
                    track.entries.values.sortedBy { it.startTimeMs }.forEach { midiEntry ->
                        androidx.compose.runtime.key(midiEntry.startTimeMs) {
                            val isSelectedEntry = midiEntry.startTimeMs in selectedEntryStarts
                            MidiClip(
                                midiEntry = midiEntry,
                                zoomLevel = zoomLevel,
                                isSelected = isSelectedEntry,
                                onSelectEntry = { onSelectEntry(midiEntry.startTimeMs) },
                                onMoveEntry = { newStart -> onMoveEntry(midiEntry.startTimeMs, newStart) },
                                onResizeEntry = { oldStart, newStart, newDuration -> onResizeEntry(oldStart, newStart, newDuration) },
                                gridIntervalMs = GridUtils.computeWithGridType(zoomLevel, bpm, gridType).intervalMs,
                                isLightsTrack = false,
                                onDoubleClick = {},
                                trackIndex = trackIndex,
                                entryStartMs = midiEntry.startTimeMs
                            )
                        }
                    }
                }
                is LightsTimelineTrack -> {
                    track.entries.values.sortedBy { it.startTimeMs }.forEach { midiEntry ->
                        androidx.compose.runtime.key(midiEntry.startTimeMs) {
                            val isSelectedEntry = midiEntry.startTimeMs in selectedEntryStarts
                            MidiClip(
                                midiEntry = midiEntry,
                                zoomLevel = zoomLevel,
                                isSelected = isSelectedEntry,
                                onSelectEntry = { onSelectEntry(midiEntry.startTimeMs) },
                                onMoveEntry = { newStart -> onMoveEntry(midiEntry.startTimeMs, newStart) },
                                onResizeEntry = { oldStart, newStart, newDuration -> onResizeEntry(oldStart, newStart, newDuration) },
                                gridIntervalMs = GridUtils.computeWithGridType(zoomLevel, bpm, gridType).intervalMs,
                                isLightsTrack = true,
                                onDoubleClick = { onDoubleClickLane(midiEntry.startTimeMs) },
                                trackIndex = trackIndex,
                                entryStartMs = midiEntry.startTimeMs
                            )
                        }
                    }
                }
            }
        }
        SelectionCursor(
            selectedTimeMs = selectedTimeMs,
            zoomLevel = zoomLevel,
            scrollState = scrollState,
            laneHeight = 120.dp,
            viewportRelative = selectionViewportRelative
        )
    }
}
