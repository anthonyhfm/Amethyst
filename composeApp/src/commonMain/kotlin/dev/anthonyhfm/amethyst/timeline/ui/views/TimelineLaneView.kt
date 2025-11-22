package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import io.github.vinceglb.filekit.PlatformFile
import dev.anthonyhfm.amethyst.ui.dnd.fileDropTarget
import kotlinx.coroutines.launch
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.timeline.TimelineViewModel
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.ui.components.WaveformView
import io.github.vinceglb.filekit.extension
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

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

    // Timeline canvas constraints
    val MAX_CANVAS_PX = 130_000f
    val MIN_TIMELINE_PX = 12_000f
    val TIMELINE_PADDING_PX = 1000.0
    val MIN_ZOOM_LEVEL = 0.0025f
    val MAX_ZOOM_LEVEL = 5f

    val maxDurationMs = tracks.maxOfOrNull { track ->
        when (track) {
            is AudioTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            is MidiTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            is LightsTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            else -> 0L
        }
    } ?: 0L

    // Use Double precision for better accuracy
    val desiredWidthPx = (maxDurationMs.toDouble() * zoomLevel.toDouble() + TIMELINE_PADDING_PX).toFloat().coerceAtLeast(MIN_TIMELINE_PX)
    val contentWidthPx = desiredWidthPx.coerceAtMost(MAX_CANVAS_PX)

    val dynamicMaxZoom = if (maxDurationMs > 0) {
        min(MAX_ZOOM_LEVEL, ((MAX_CANVAS_PX - TIMELINE_PADDING_PX.toFloat()) / maxDurationMs.toFloat()).coerceAtLeast(MIN_ZOOM_LEVEL))
    } else MAX_ZOOM_LEVEL

    val contentWidth = with(LocalDensity.current) { contentWidthPx.toDp() }

    val scope = rememberCoroutineScope()

    // Zoom interaction constants
    val ZOOM_SCROLL_SENSITIVITY = 0.55f
    val ZOOM_SCROLL_LERP_WEIGHT = 0.6f
    val ZOOM_GESTURE_LERP_WEIGHT = 0.5f

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
                                    val deltaFactor = -normalizedTotal * ZOOM_SCROLL_SENSITIVITY
                                    val targetScale = (1f + deltaFactor).coerceAtLeast(0.1f)
                                    val rawNewZoom = currentZoom * targetScale
                                    val smoothedZoom = currentZoom + (rawNewZoom - currentZoom) * ZOOM_SCROLL_LERP_WEIGHT
                                    val newZoom = smoothedZoom.coerceIn(MIN_ZOOM_LEVEL, dynamicMaxZoom)

                                    val cursorX = change?.position?.x ?: 0f
                                    // Use Double precision for better accuracy
                                    val timeAtCursorMs = if (currentZoom > 0f) (scrollState.value.toDouble() + cursorX.toDouble()) / currentZoom.toDouble() else 0.0

                                    if (newZoom != currentZoom) {
                                        viewModel.setZoomLevel(newZoom)
                                        // Use Double precision for scroll calculation
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
                        val smoothedZoom = currentZoom + (rawNewZoom - currentZoom) * ZOOM_GESTURE_LERP_WEIGHT
                        val newZoom = smoothedZoom.coerceIn(MIN_ZOOM_LEVEL, dynamicMaxZoom)
                        val cursorX = centroid.x
                        // Use Double precision for better accuracy
                        val timeAtCursorMs = if (currentZoom > 0f) (scrollState.value.toDouble() + cursorX.toDouble()) / currentZoom.toDouble() else 0.0
                        if (newZoom != currentZoom) {
                            viewModel.setZoomLevel(newZoom)
                            // Use Double precision for scroll calculation
                            val targetScroll = (timeAtCursorMs * newZoom.toDouble() - cursorX.toDouble()).coerceAtLeast(0.0)
                            scope.launch { scrollState.scrollTo(targetScroll.toInt()) }
                        }
                    }
                }
            }
    ) {
        val selections by SelectionManager.selections.collectAsState()
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

@Composable
fun PlayheadCursor(
    positionMs: Long,
    zoomLevel: Float,
    scrollState: ScrollState
) {
    // Include scrollState.value in remember keys for proper tracking
    val cursorXPosition by remember(positionMs, zoomLevel, scrollState.value) {
        derivedStateOf {
            // Use Double precision for better accuracy
            val playheadPx = positionMs.toDouble() * zoomLevel.toDouble()
            val scroll = scrollState.value.toDouble()
            (playheadPx - scroll).toFloat().roundToInt()
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(cursorXPosition, 0) }
            .width(2.dp)
            .fillMaxHeight()
            .background(
                color = Color(0xff93ff93),
                shape = RoundedCornerShape(1.dp)
            )
            .dropShadow(
                shape = RectangleShape,
                shadow = Shadow(
                    radius = 4.dp,
                    color = Color(0xff93ff93).copy(alpha = 0.6f)
                )
            )
    )
}

@Composable
fun TimelineLane(
    track: TimelineTrack<*>,
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
    onDoubleClickLane: (Long) -> Unit = {}
) {
    val bpm by WorkspaceRepository.bpm.collectAsState()
    val gridType by WorkspaceRepository.gridType.collectAsState()

    var rangeStartMs by remember(track, zoomLevel) { mutableStateOf<Long?>(null) }
    var rangeEndMs by remember(track, zoomLevel) { mutableStateOf<Long?>(null) }
    var rangeActive by remember { mutableStateOf(false) }

    val selections by SelectionManager.selections.collectAsState()
    val selectedRange = selections.filterIsInstance<Selectable.TimelineRange>().firstOrNull { it.trackIndex == trackIndexOf(track) }

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
                    if (audioFiles.isNotEmpty()) {
                        onDropInFile(audioFiles.first())
                    }
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

                            val headerHit = findHeaderEntryHit(track, pos.x, pos.y, zoomLevel, scrollState.value.toFloat(), headerHeightPx)
                            if (headerHit != null) {
                                onSelectEntry(headerHit)
                                lastClickTime = 0L
                                lastClickPos = null
                                change.consume(); continue
                            }

                            val isLights = track is LightsTimelineTrack
                            val snappedMs = computeSnappedTime(pos.x, scrollState, zoomLevel, bpm, gridType)
                            val isDouble = isLights && lastClickTime != 0L &&
                                (time - lastClickTime) in 1..doubleThresholdMs &&
                                lastClickPos?.let { prev ->
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
                        val headerHit = findHeaderEntryHit(track, offset.x, offset.y, zoomLevel, scrollState.value.toFloat(), headerHeightPx)
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
                            val currentStrict = computeStrictGridTime(change.position.x, scrollState, zoomLevel, bpm, gridType)
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
            // Use Double precision for better accuracy
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
                    track.entries.values
                        .sortedBy { it.startTimeMs }
                        .forEach { audioEntry ->
                            androidx.compose.runtime.key(audioEntry.startTimeMs) {
                                val isSelectedEntry = audioEntry.startTimeMs in selectedEntryStarts
                                AudioClip(
                                    audioEntry = audioEntry,
                                    zoomLevel = zoomLevel,
                                    isSelected = isSelectedEntry,
                                    onSelectEntry = { onSelectEntry(audioEntry.startTimeMs) },
                                    onMoveEntry = { newStart -> onMoveEntry(audioEntry.startTimeMs, newStart) },
                                    gridIntervalMs = GridUtils.computeWithGridType(zoomLevel, bpm, gridType).intervalMs
                                )
                            }
                        }
                }
                is MidiTimelineTrack -> {
                    track.entries.values
                        .sortedBy { it.startTimeMs }
                        .forEach { midiEntry ->
                            androidx.compose.runtime.key(midiEntry.startTimeMs) {
                                val isSelectedEntry = midiEntry.startTimeMs in selectedEntryStarts
                                MidiClip(
                                    midiEntry = midiEntry,
                                    zoomLevel = zoomLevel,
                                    isSelected = isSelectedEntry,
                                    onSelectEntry = { onSelectEntry(midiEntry.startTimeMs) },
                                    onMoveEntry = { newStart -> onMoveEntry(midiEntry.startTimeMs, newStart) },
                                    gridIntervalMs = GridUtils.computeWithGridType(zoomLevel, bpm, gridType).intervalMs,
                                    isLightsTrack = false,
                                    onDoubleClick = {}
                                )
                            }
                        }
                }
                is LightsTimelineTrack -> {
                    track.entries.values
                        .sortedBy { it.startTimeMs }
                        .forEach { midiEntry ->
                            androidx.compose.runtime.key(midiEntry.startTimeMs) {
                                val isSelectedEntry = midiEntry.startTimeMs in selectedEntryStarts
                                MidiClip(
                                    midiEntry = midiEntry,
                                    zoomLevel = zoomLevel,
                                    isSelected = isSelectedEntry,
                                    onSelectEntry = { onSelectEntry(midiEntry.startTimeMs) },
                                    onMoveEntry = { newStart -> onMoveEntry(midiEntry.startTimeMs, newStart) },
                                    gridIntervalMs = GridUtils.computeWithGridType(zoomLevel, bpm, gridType).intervalMs,
                                    isLightsTrack = true,
                                    onDoubleClick = { onDoubleClickLane(midiEntry.startTimeMs) }
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

@Composable
private fun Modifier.timelineGridOverlay(
    zoomLevel: Float,
    bpm: Double,
    gridType: GridUtils.GridType,
    drawBehind: Boolean = false,
    ignoreTopPx: Float = 0f,
    scrollOffsetPx: Float = 0f
): Modifier {
    val baseLineColor = Color(0xFF000000)
    val minorColor = baseLineColor.copy(alpha = 0.55f)
    val majorColor = baseLineColor.copy(alpha = 0.80f)
    return this.drawWithContent {
        if (zoomLevel <= 0f) {
            drawContent(); return@drawWithContent
        }
        val intervals = GridUtils.computeWithGridType(zoomLevel, bpm, gridType)
        val intervalMs = intervals.intervalMs
        val majorIntervalMs = intervals.majorIntervalMs
        if (intervalMs <= 0L) { drawContent(); return@drawWithContent }
        val pxPerGrid = intervalMs * zoomLevel
        if (pxPerGrid < 4f) { drawContent(); return@drawWithContent }

        fun drawGridLines() {
            val viewportWidthPx = size.width
            // Use Double precision for better accuracy
            val startTimeMsInclusive = (scrollOffsetPx / zoomLevel.toDouble()).toLong().coerceAtLeast(0L)

            // Improved first grid calculation with proper rounding
            val firstGridTimeMs = if (startTimeMsInclusive == 0L) {
                0L
            } else {
                ((startTimeMsInclusive / intervalMs) * intervalMs).coerceAtLeast(0L)
            }
            
            val endTimeMsExclusive = ((scrollOffsetPx + viewportWidthPx) / zoomLevel.toDouble()).toLong().coerceAtLeast(firstGridTimeMs)
            var t = firstGridTimeMs
            
            // Draw grid lines with sub-pixel precision
            while (t <= endTimeMsExclusive + intervalMs) {
                val x = (t.toDouble() * zoomLevel.toDouble() - scrollOffsetPx.toDouble()).toFloat()
                if (x > viewportWidthPx + 1f) break
                if (x >= -1f) {
                    val isMajor = (t % majorIntervalMs == 0L)
                    drawLine(
                        color = if (isMajor) majorColor else minorColor,
                        start = Offset(x, ignoreTopPx),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                t += intervalMs
            }
        }
        if (drawBehind) {
            drawGridLines(); drawContent()
        } else {
            drawContent(); drawGridLines()
        }
    }
}

@Composable
fun AudioClip(
    audioEntry: AudioEntry,
    zoomLevel: Float,
    isSelected: Boolean,
    onSelectEntry: () -> Unit,
    onMoveEntry: (newStartMs: Long) -> Unit,
    gridIntervalMs: Long
) {
    val density = LocalDensity.current
    // Use Double precision for better accuracy in positioning
    val startOffsetPx = (audioEntry.startTimeMs.toDouble() * zoomLevel.toDouble()).roundToInt()
    val widthDp = with(density) { (audioEntry.durationMs.toDouble() * zoomLevel.toDouble()).toFloat().toDp() }
    val borderColor = if (isSelected) Color.White else Color(0xFF3C3CBA)
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF5656EF)
    val foregroundColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White

    val dragOffsetPx = remember(audioEntry.startTimeMs) { mutableStateOf(0f) }
    var snapEnabled by remember { mutableStateOf(true) }
    val previewStartMs by remember(dragOffsetPx.value, zoomLevel, snapEnabled) {
        derivedStateOf {
            val rawDeltaMsDouble = dragOffsetPx.value / zoomLevel
            val candidateMsDouble = audioEntry.startTimeMs.toDouble() + rawDeltaMsDouble
            val nonNegativeCandidate = candidateMsDouble.coerceAtLeast(0.0)
            if (snapEnabled && gridIntervalMs > 0) {
                val gridPxSpacing = gridIntervalMs * zoomLevel
                val thresholdPx = (gridPxSpacing * 0.35f).coerceAtLeast(5f)
                GridUtils.snapToGridWithThreshold(nonNegativeCandidate.roundToLong(), zoomLevel, WorkspaceRepository.bpm.value, WorkspaceRepository.gridType.value, thresholdPx)
            } else kotlin.math.round(nonNegativeCandidate).toLong()
        }
    }

    Column(
        modifier = Modifier
            .offset { IntOffset(startOffsetPx + dragOffsetPx.value.roundToInt(), 0) }
            .clip(RoundedCornerShape(6.dp))
            .height(120.dp)
            .width(widthDp)
            .background(backgroundColor.copy(alpha = if (isSelected) 0.96f else 0.90f), RoundedCornerShape(6.dp))
            .then(
                other = if (isSelected)
                    Modifier.border(1.5.dp, borderColor, RoundedCornerShape(6.dp))
                else Modifier
            )
            .zIndex(if (isSelected) 1f else 0f)
    ) {
        Text(
            text = audioEntry.fileName.substringBeforeLast('.'),
            modifier = Modifier
                .fillMaxWidth()
                .background(borderColor)
                .clickable { onSelectEntry() }
                .pointerInput(audioEntry.startTimeMs, zoomLevel, gridIntervalMs) {
                    detectDragGestures(
                        onDragStart = { onSelectEntry() },
                        onDragEnd = {
                            if (previewStartMs != audioEntry.startTimeMs) {
                                onMoveEntry(previewStartMs)
                            }
                            dragOffsetPx.value = 0f
                            snapEnabled = true
                        },
                        onDragCancel = { dragOffsetPx.value = 0f; snapEnabled = true },
                        onDrag = { change, dragAmount ->
                            change.consume()

                            dragOffsetPx.value += dragAmount.x
                        }
                    )
                }
                .padding(4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                lineHeight = MaterialTheme.typography.labelSmall.fontSize,
            ),
            color = if (isSelected) Color.Black else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            WaveformView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                waveColor = foregroundColor,
                signal = Signal.AudioSignal(
                    origin = null,
                    rawData = audioEntry.rawData,
                    bitDepth = audioEntry.bitDepth,
                    channels = audioEntry.channels,
                    sampleRate = audioEntry.sampleRate,
                ),
            )
        }
    }
}

@Composable
private fun SelectionCursor(
    selectedTimeMs: Long?,
    zoomLevel: Float,
    scrollState: ScrollState,
    laneHeight: androidx.compose.ui.unit.Dp = 120.dp,
    viewportRelative: Boolean = false
) {
    if (selectedTimeMs == null) return

    // Include scrollState.value in the remember keys to properly track scroll changes
    val cursorXPositionPx by remember(selectedTimeMs, zoomLevel, viewportRelative, scrollState.value) {
        derivedStateOf {
            val raw = selectedTimeMs * zoomLevel
            if (viewportRelative) raw - scrollState.value else raw
        }
    }
    Box(
        modifier = Modifier
            .offset(x = -1.5.dp)
            .offset { IntOffset(cursorXPositionPx.roundToInt(), 0) }
            .width(3.dp)
            .height(laneHeight)
            .background(color = Color.White, shape = RoundedCornerShape(1.dp))
            .zIndex(2f)
    )
}

@Composable
fun MidiClip(
    midiEntry: MidiEntry,
    zoomLevel: Float,
    isSelected: Boolean,
    onSelectEntry: () -> Unit,
    onMoveEntry: (newStartMs: Long) -> Unit,
    gridIntervalMs: Long,
    isLightsTrack: Boolean = false,
    onDoubleClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    // Use Double precision for better accuracy in positioning
    val startOffsetPx = (midiEntry.startTimeMs.toDouble() * zoomLevel.toDouble()).roundToInt()
    val widthDp = with(density) { (midiEntry.durationMs.toDouble() * zoomLevel.toDouble()).toFloat().toDp() }
    val borderColor = if (isSelected) Color.White else if (isLightsTrack) Color(0xFFD4AF37) else Color(0xFFBA3C8C)
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.tertiary else if (isLightsTrack) Color(0xFFFFD700) else Color(0xFFEF5698)
    val foregroundColor = if (isSelected) MaterialTheme.colorScheme.onTertiary else if (isLightsTrack) Color.Black else Color.White

    val dragOffsetPx = remember(midiEntry.startTimeMs) { mutableStateOf(0f) }
    var snapEnabled by remember { mutableStateOf(true) }
    val previewStartMs by remember(dragOffsetPx.value, zoomLevel, snapEnabled) {
        derivedStateOf {
            val rawDeltaMsDouble = dragOffsetPx.value / zoomLevel
            val candidateMsDouble = midiEntry.startTimeMs.toDouble() + rawDeltaMsDouble
            val nonNegativeCandidate = candidateMsDouble.coerceAtLeast(0.0)
            if (snapEnabled && gridIntervalMs > 0) {
                val gridPxSpacing = gridIntervalMs * zoomLevel
                val thresholdPx = (gridPxSpacing * 0.35f).coerceAtLeast(5f)
                GridUtils.snapToGridWithThreshold(nonNegativeCandidate.roundToLong(), zoomLevel, WorkspaceRepository.bpm.value, WorkspaceRepository.gridType.value, thresholdPx)
            } else kotlin.math.round(nonNegativeCandidate).toLong()
        }
    }
    val finalOffsetPx = startOffsetPx + dragOffsetPx.value.roundToInt()

    val clipHeight = 120.dp
    val headerHeight = 20.dp

    Column(
        modifier = Modifier
            .offset { IntOffset(finalOffsetPx, 0) }
            .width(widthDp)
            .height(clipHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor.copy(alpha = if (isSelected) 0.96f else 0.90f), RoundedCornerShape(6.dp))
            .then(
                if (isSelected) Modifier.border(1.5.dp, borderColor, RoundedCornerShape(6.dp)) else Modifier.border(1.dp, borderColor.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            )
            .zIndex(if (isSelected) 1f else 0f)
    ) {
        Text(
            text = midiEntry.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .background(borderColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .clickable { onSelectEntry() }
                .pointerInput(midiEntry.startTimeMs, zoomLevel, gridIntervalMs) {
                    detectDragGestures(
                        onDragStart = { onSelectEntry() },
                        onDragEnd = {
                            if (previewStartMs != midiEntry.startTimeMs) {
                                onMoveEntry(previewStartMs)
                            }
                            dragOffsetPx.value = 0f
                            snapEnabled = true
                        },
                        onDragCancel = { dragOffsetPx.value = 0f; snapEnabled = true },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetPx.value += dragAmount.x
                        }
                    )
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleClick() }) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = MaterialTheme.typography.labelSmall.fontSize),
            color = foregroundColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(4.dp)
                .drawWithContent {
                    drawContent()
                    if (zoomLevel <= 0f) return@drawWithContent
                    val contentHeightPx = size.height
                    val noteBarMinHeightPx = 4f
                    val noteBarMaxHeightPx = 14f
                    midiEntry.notes.forEach { note ->
                        val overlapStart = maxOf(note.startTimeMs, midiEntry.startTimeMs)
                        val overlapEnd = minOf(note.endTimeMs, midiEntry.endTimeMs)
                        if (overlapEnd <= overlapStart) return@forEach
                        val relStartMs = overlapStart - midiEntry.startTimeMs
                        val relDurationMs = overlapEnd - overlapStart
                        val x = relStartMs * zoomLevel
                        val w = relDurationMs * zoomLevel
                        if (w < 0.5f) return@forEach
                        val pitchRatio = (note.pitch.coerceIn(0, 127)) / 127f
                        val y = (1f - pitchRatio) * (contentHeightPx - noteBarMaxHeightPx)
                        val barHeightPx = noteBarMinHeightPx + (noteBarMaxHeightPx - noteBarMinHeightPx) * 0.55f
                        val noteColor = Color(note.led.red, note.led.green, note.led.blue)
                        drawRect(
                            color = noteColor.copy(alpha = 0.60f),
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w, barHeightPx)
                        )
                        drawRect(
                            color = noteColor.copy(alpha = 0.92f),
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w, barHeightPx),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.0f)
                        )
                    }
                }
        ) {
            Text(
                text = "${midiEntry.notes.size} notes",
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart).background(backgroundColor.copy(alpha = 0.35f)).padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.75f),
                color = foregroundColor.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun trackIndexOf(track: TimelineTrack<*>): Int {
    return TimelineRepository.tracks.value.indexOf(track)
}

private fun isPointInsideAnyEntry(track: TimelineTrack<*>, timeMs: Long): Boolean {
    return when (track) {
        is AudioTimelineTrack -> track.entries.values.any { timeMs in it.startTimeMs..(it.startTimeMs + it.durationMs) }
        is MidiTimelineTrack -> track.entries.values.any { timeMs in it.startTimeMs..it.endTimeMs }
        is LightsTimelineTrack -> track.entries.values.any { timeMs in it.startTimeMs..it.endTimeMs }
        else -> false
    }
}

private fun computeSnappedTime(x: Float, scrollState: ScrollState, zoomLevel: Float, bpm: Double, gridType: GridUtils.GridType): Long {
    // Use Double precision for better accuracy
    val rawPx = scrollState.value.toDouble() + x.toDouble()
    val rawTimeMsDouble = if (zoomLevel > 0f) rawPx / zoomLevel.toDouble() else 0.0
    val rawTimeMs = rawTimeMsDouble.roundToLong().coerceAtLeast(0L)
    val intervals = GridUtils.computeWithGridType(zoomLevel, bpm, gridType)
    val gridIntervalMs = intervals.intervalMs
    val gridPxSpacing = gridIntervalMs * zoomLevel
    val snapThresholdPx = (gridPxSpacing * 0.40f).coerceAtLeast(6f)
    val shouldSnap = gridIntervalMs > 0 && gridPxSpacing >= 6f
    return if (shouldSnap) GridUtils.snapToGridWithThreshold(rawTimeMs, zoomLevel, bpm, gridType, thresholdPx = snapThresholdPx) else rawTimeMs
}

private fun computeStrictGridTime(x: Float, scrollState: ScrollState, zoomLevel: Float, bpm: Double, gridType: GridUtils.GridType): Long {
    // Use Double precision for better accuracy
    val rawPx = scrollState.value.toDouble() + x.toDouble()
    val rawTimeMsDouble = if (zoomLevel > 0f) rawPx / zoomLevel.toDouble() else 0.0
    val rawTimeMs = rawTimeMsDouble.roundToLong().coerceAtLeast(0L)

    return GridUtils.snapToGrid(rawTimeMs, zoomLevel, bpm, gridType)
}

private fun findHeaderEntryHit(
    track: TimelineTrack<*>,
    x: Float,
    y: Float,
    zoom: Float,
    scrollPx: Float,
    headerHeightPx: Float
): Long? {
    if (y > headerHeightPx) return null
    return when (track) {
        is AudioTimelineTrack -> {
            track.entries.values.firstOrNull { entry ->
                // Use Double precision for better accuracy
                val left = entry.startTimeMs.toDouble() * zoom.toDouble() - scrollPx.toDouble()
                val right = left + entry.durationMs.toDouble() * zoom.toDouble()
                x.toDouble() in left..right
            }?.startTimeMs
        }
        is MidiTimelineTrack -> {
            track.entries.values.firstOrNull { entry ->
                val left = entry.startTimeMs.toDouble() * zoom.toDouble() - scrollPx.toDouble()
                val right = left + entry.durationMs.toDouble() * zoom.toDouble()
                x.toDouble() in left..right
            }?.startTimeMs
        }
        is LightsTimelineTrack -> {
            track.entries.values.firstOrNull { entry ->
                val left = entry.startTimeMs.toDouble() * zoom.toDouble() - scrollPx.toDouble()
                val right = left + entry.durationMs.toDouble() * zoom.toDouble()
                x.toDouble() in left..right
            }?.startTimeMs
        }
        else -> null
    }
}
