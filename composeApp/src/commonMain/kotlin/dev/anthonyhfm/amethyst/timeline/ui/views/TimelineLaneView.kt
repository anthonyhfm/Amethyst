package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
    scrollState: ScrollState
) {
    val tracks by viewModel.tracks.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val playheadPositionMs by viewModel.playheadPositionMs.collectAsState()

    val MAX_CANVAS_PX = 130_000f
    val MIN_TIMELINE_PX = 12_000f

    val maxDurationMs = tracks.maxOfOrNull { track ->
        when (track) {
            is AudioTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
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
                    val currentZoom = zoomLevel // immer aktueller Wert nach Neustart der Coroutine
                    val currentBpm = WorkspaceRepository.bpm.value
                    val currentGridType = WorkspaceRepository.gridType.value
                    val intervals = GridUtils.computeWithGridType(currentZoom, currentBpm, currentGridType)
                    val gridIntervalMs = intervals.intervalMs

                    val rawPx = scrollState.value.toDouble() + tapOffset.x.toDouble()
                    val rawTimeMsDouble = if (currentZoom > 0f) rawPx / currentZoom.toDouble() else 0.0
                    val rawTimeMs = rawTimeMsDouble.roundToLong().coerceAtLeast(0L)
                    val gridPxSpacing = gridIntervalMs * currentZoom
                    val shouldSnap = gridIntervalMs > 0 && gridPxSpacing >= 6f
                    val snapped = if (shouldSnap) GridUtils.snapToGrid(rawTimeMs, currentZoom, currentBpm, currentGridType) else rawTimeMs
                    println("[TimelineLane] click(pxPerMs) tapX=${tapOffset.x} scroll=${scrollState.value} zoom=$currentZoom rawMs=$rawTimeMs snappedMs=$snapped gridIntMs=$gridIntervalMs gridPxSpacing=$gridPxSpacing diffMs=${snapped - rawTimeMs}")
                    onSelectTime(snapped)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .width(contentWidth)
                .height(120.dp)
        ) {
            GridOverlay(
                zoomLevel = zoomLevel,
                contentWidth = contentWidth,
                bpm = bpm,
                gridType = gridType
            )
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
            }
            SelectionCursor(
                selectedTimeMs = selectedTimeMs,
                zoomLevel = zoomLevel,
                scrollState = scrollState,
                laneHeight = 120.dp
            )
        }
    }
}

@Composable
private fun GridOverlay(
    zoomLevel: Float,
    contentWidth: androidx.compose.ui.unit.Dp,
    bpm: Double,
    gridType: GridUtils.GridType,
    laneHeight: androidx.compose.ui.unit.Dp = 120.dp
) {
    val density = LocalDensity.current
    val contentWidthPx = with(density) { contentWidth.toPx() }
    val laneHeightPx = with(density) { laneHeight.toPx() }

    val isDark = isSystemInDarkTheme()

    val intervals = GridUtils.computeWithGridType(zoomLevel, bpm, gridType)
    val intervalMs = intervals.intervalMs
    val majorIntervalMs = intervals.majorIntervalMs

    val baseColor = if (isDark) Color(0xFFEFEFEF) else Color.Black
    val minorColor = baseColor.copy(alpha = if (isDark) 0.25f else 0.32f)
    val majorColor = baseColor.copy(alpha = if (isDark) 0.55f else 0.65f)

    Canvas(
        modifier = Modifier
            .width(contentWidth)
            .height(laneHeight)
            .zIndex(0f)
    ) {
        val strokeMinor = 1.1.dp.toPx()
        val strokeMajor = 2.dp.toPx()
        val totalDurationMs = (contentWidthPx / zoomLevel).toLong()
        var t = 0L
        while (t <= totalDurationMs) {
            val x = t * zoomLevel
            if (x > contentWidthPx + 1f) break
            val isMajor = (t % majorIntervalMs == 0L)
            drawLine(
                color = if (isMajor) majorColor else minorColor,
                start = Offset(x, 0f),
                end = Offset(x, laneHeightPx),
                strokeWidth = if (isMajor) strokeMajor else strokeMinor
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
                val q = nonNegativeCandidate / gridIntervalMs.toDouble()
                (kotlin.math.round(q) * gridIntervalMs).toLong()
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
    laneHeight: androidx.compose.ui.unit.Dp = 120.dp
) {
    if (selectedTimeMs == null) return
    val cursorXPositionPx by remember(selectedTimeMs, zoomLevel, scrollState) {
        derivedStateOf { selectedTimeMs * zoomLevel - scrollState.value }
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
