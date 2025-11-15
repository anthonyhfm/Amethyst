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
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun TimelineLaneView(
    viewModel: TimelineViewModel,
    scrollState: ScrollState,
    selectionViewportRelative: Boolean = false
) {
    val tracks by viewModel.tracks.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val playheadPositionMs by viewModel.playheadPositionMs.collectAsState()

    val MAX_CANVAS_PX = 130_000f
    val MIN_TIMELINE_PX = 12_000f

    val maxDurationMs = tracks.maxOfOrNull { track ->
        when (track) {
            is AudioTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            is MidiTimelineTrack, is LightsTimelineTrack -> (track as TimelineTrack<MidiEntry>).entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            else -> 0L
        }
    } ?: 0L

    val desiredWidthPx = (maxDurationMs * zoomLevel + 1000).coerceAtLeast(MIN_TIMELINE_PX)
    val contentWidthPx = desiredWidthPx.coerceAtMost(MAX_CANVAS_PX)

    val dynamicMaxZoom = if (maxDurationMs > 0) {
        min(5f, (MAX_CANVAS_PX - 1000f) / maxDurationMs.toFloat())
    } else 5f

    val contentWidth = with(LocalDensity.current) { contentWidthPx.toDp() }

    val scope = rememberCoroutineScope()

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
                                    val baseSensitivity = 0.55f
                                    val deltaFactor = -normalizedTotal * baseSensitivity
                                    val targetScale = (1f + deltaFactor).coerceAtLeast(0.1f)
                                    val lerpWeight = 0.6f
                                    val rawNewZoom = currentZoom * targetScale
                                    val smoothedZoom = currentZoom + (rawNewZoom - currentZoom) * lerpWeight
                                    val newZoom = smoothedZoom.coerceIn(0.0025f, dynamicMaxZoom)

                                    val cursorX = change?.position?.x ?: 0f
                                    val timeAtCursorMs = if (currentZoom > 0f) (scrollState.value + cursorX) / currentZoom else 0f

                                    if (newZoom != currentZoom) {
                                        viewModel.setZoomLevel(newZoom)
                                        val targetScroll = (timeAtCursorMs * newZoom - cursorX).coerceAtLeast(0f)
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
                        val smoothedZoom = currentZoom + (rawNewZoom - currentZoom) * 0.5f
                        val newZoom = smoothedZoom.coerceIn(0.0025f, dynamicMaxZoom)
                        val cursorX = centroid.x
                        val timeAtCursorMs = if (currentZoom > 0f) (scrollState.value + cursorX) / currentZoom else 0f
                        if (newZoom != currentZoom) {
                            viewModel.setZoomLevel(newZoom)
                            val targetScroll = (timeAtCursorMs * newZoom - cursorX).coerceAtLeast(0f)
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
                        viewModel.moveAudioEntry(index, oldStart, newStart)
                    }
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
    val cursorXPosition by remember(positionMs, zoomLevel, scrollState) {
        derivedStateOf {
            val playheadPx = positionMs * zoomLevel
            val scroll = scrollState.value.toFloat()
            (playheadPx - scroll).roundToInt()
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
    onMoveEntry: (oldStart: Long, newStart: Long) -> Unit = { _, _ -> }
) {
    val bpm by WorkspaceRepository.bpm.collectAsState()
    val gridType by WorkspaceRepository.gridType.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
            .pointerInput(zoomLevel, bpm, gridType) {
                detectTapGestures { tapOffset ->
                    val currentZoom = zoomLevel
                    val currentBpm = WorkspaceRepository.bpm.value
                    val currentGridType = WorkspaceRepository.gridType.value
                    val intervals = GridUtils.computeWithGridType(currentZoom, currentBpm, currentGridType)
                    val gridIntervalMs = intervals.intervalMs

                    val rawPx = scrollState.value.toDouble() + tapOffset.x.toDouble()
                    val rawTimeMsDouble = if (currentZoom > 0f) rawPx / currentZoom.toDouble() else 0.0
                    val rawTimeMs = rawTimeMsDouble.roundToLong().coerceAtLeast(0L)
                    val gridPxSpacing = gridIntervalMs * currentZoom
                    val snapThresholdPx = (gridPxSpacing * 0.40f).coerceAtLeast(6f)
                    val shouldSnap = gridIntervalMs > 0 && gridPxSpacing >= 6f
                    val snapped = if (shouldSnap) GridUtils.snapToGridWithThreshold(rawTimeMs, currentZoom, currentBpm, currentGridType, thresholdPx = snapThresholdPx) else rawTimeMs
                    println("[TimelineLane] click(pxPerMs) tapX=${tapOffset.x} scroll=${scrollState.value} zoom=$currentZoom rawMs=$rawTimeMs snappedMs=$snapped gridIntMs=$gridIntervalMs gridPxSpacing=$gridPxSpacing diffMs=${snapped - rawTimeMs} thresholdPx=$snapThresholdPx")
                    onSelectTime(snapped)
                }
            }
            .timelineGridOverlay(
                zoomLevel = zoomLevel,
                bpm = bpm,
                gridType = gridType
            )
    ) {
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
                is MidiTimelineTrack, is LightsTimelineTrack -> {
                    (track as TimelineTrack<MidiEntry>).entries.values
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
                                    isLightsTrack = track is LightsTimelineTrack
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
    gridType: GridUtils.GridType
): Modifier {
    return this.drawWithContent {
        drawContent()
        if (zoomLevel <= 0f) return@drawWithContent
        val intervals = GridUtils.computeWithGridType(zoomLevel, bpm, gridType)
        val intervalMs = intervals.intervalMs
        val majorIntervalMs = intervals.majorIntervalMs
        if (intervalMs <= 0L) return@drawWithContent
        val pxPerGrid = intervalMs * zoomLevel
        if (pxPerGrid < 4f) return@drawWithContent

        val baseColor = Color(0xFF101010)
        val minorColor = baseColor.copy(alpha = 0.30f)
        val majorColor = baseColor.copy(alpha = 0.60f)
        val contentWidthPx = size.width
        val totalDurationMs = (contentWidthPx / zoomLevel).toLong()
        var t = 0L
        while (t <= totalDurationMs) {
            val x = t * zoomLevel
            if (x > contentWidthPx + 1f) break
            val isMajor = (t % majorIntervalMs == 0L)
            drawLine(
                color = if (isMajor) majorColor else minorColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
            t += intervalMs
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
    val bpm by WorkspaceRepository.bpm.collectAsState()
    val gridType by WorkspaceRepository.gridType.collectAsState()

    val density = LocalDensity.current
    val startOffsetPx = (audioEntry.startTimeMs * zoomLevel).roundToInt()
    val widthDp = with(density) { (audioEntry.durationMs * zoomLevel).toDp() }
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
            .background(backgroundColor, RoundedCornerShape(6.dp))
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
                .background(borderColor.copy(alpha = 0.65f))
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
                .timelineGridOverlay(
                    zoomLevel = zoomLevel,
                    bpm = bpm,
                    gridType = gridType
                )
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

    val scrollX = scrollState.value
    val cursorXPositionPx by remember(selectedTimeMs, zoomLevel, viewportRelative) {
        derivedStateOf {
            val raw = selectedTimeMs * zoomLevel
            if (viewportRelative) raw - scrollX else raw
        }
    }
    Box(
        modifier = Modifier
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
    isLightsTrack: Boolean = false
) {
    val bpm by WorkspaceRepository.bpm.collectAsState()
    val gridType by WorkspaceRepository.gridType.collectAsState()

    val density = LocalDensity.current
    val startOffsetPx = (midiEntry.startTimeMs * zoomLevel).roundToInt()
    val widthDp = with(density) { (midiEntry.durationMs * zoomLevel).toDp() }
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

    Box(
        modifier = Modifier
            .offset { IntOffset(finalOffsetPx, 0) }
            .width(widthDp)
            .height(112.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(2.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable { onSelectEntry() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(midiEntry.startTimeMs, zoomLevel, gridIntervalMs) {
                    detectDragGestures(
                        onDragStart = { snapEnabled = true },
                        onDragEnd = {
                            onMoveEntry(previewStartMs)
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
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = midiEntry.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    lineHeight = MaterialTheme.typography.labelSmall.fontSize,
                ),
                color = foregroundColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Display note count
            Text(
                text = "${midiEntry.notes.size} notes",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f
                ),
                color = foregroundColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
