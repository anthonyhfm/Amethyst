package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isMetaPressed
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
import androidx.compose.foundation.gestures.detectTapGestures
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable

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
                            val alt = event.keyboardModifiers.isMetaPressed
                            val change = event.changes.firstOrNull()
                            val deltaY = change?.scrollDelta?.y ?: 0f
                            if (alt && deltaY != 0f) {
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
                                event.changes.forEach { pointer -> pointer.consume() }
                            } else if (!alt && accumulatedDeltaY != 0f) {
                                accumulatedDeltaY = 0f
                            }
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
                TimelineLane(
                    track = track,
                    zoomLevel = zoomLevel,
                    contentWidth = contentWidth,
                    scrollState = scrollState,
                    selectedTimeMs = laneSelectedTimeMs,
                    onDropInFile = { file ->
                        viewModel.addAudioFileToTrack(
                            trackIndex = index,
                            file = file,
                            at = playheadPositionMs
                        )
                    },
                    onSelectTime = { rawClickTimeMs ->
                        val snapped = GridUtils.snapToGrid(rawClickTimeMs.coerceAtLeast(0), zoomLevel)
                        SelectionManager.select(Selectable.TimelineTime(trackIndex = index, timeMs = snapped))
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
    onDropInFile: (file: PlatformFile) -> Unit = {},
    onSelectTime: (Long) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
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
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val rawTimeMs = ((scrollState.value + tapOffset.x) / zoomLevel).toLong()
                    onSelectTime(rawTimeMs)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .width(contentWidth)
                .height(140.dp)
        ) {
            GridOverlay(
                zoomLevel = zoomLevel,
                contentWidth = contentWidth
            )
            when (track) {
                is AudioTimelineTrack -> {
                    track.entries.values.forEach { audioEntry ->
                        AudioClip(
                            audioEntry = audioEntry,
                            zoomLevel = zoomLevel
                        )
                    }
                }
            }
            SelectionCursor(
                selectedTimeMs = selectedTimeMs,
                zoomLevel = zoomLevel,
                scrollState = scrollState
            )
        }
    }
}

@Composable
private fun GridOverlay(
    zoomLevel: Float,
    contentWidth: androidx.compose.ui.unit.Dp,
    laneHeight: androidx.compose.ui.unit.Dp = 140.dp
) {
    val density = LocalDensity.current
    val contentWidthPx = with(density) { contentWidth.toPx() }
    val laneHeightPx = with(density) { laneHeight.toPx() }

    val isDark = isSystemInDarkTheme()

    val intervals = GridUtils.compute(zoomLevel)
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
    zoomLevel: Float
) {
    val density = LocalDensity.current
    val startOffsetPx = (audioEntry.startTimeMs * zoomLevel).roundToInt()
    val widthDp = with(density) { (audioEntry.durationMs * zoomLevel).toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(startOffsetPx, 0) }
            .height(140.dp)
            .width(widthDp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                RoundedCornerShape(4.dp)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Text(
                text = audioEntry.fileName.substringBeforeLast('.'),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
                    .padding(4.dp)
                    .zIndex(1f),
                style = MaterialTheme.typography.labelSmall.copy(
                    lineHeight = MaterialTheme.typography.labelSmall.fontSize,
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            WaveformView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                waveColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f),
                signal = Signal.AudioSignal(
                    origin = null,
                    rawData = audioEntry.rawData,
                    bitDepth = audioEntry.bitDepth,
                    channels = audioEntry.channels,
                    sampleRate = audioEntry.sampleRate,
                )
            )
        }
    }
}

@Composable
private fun SelectionCursor(
    selectedTimeMs: Long?,
    zoomLevel: Float,
    scrollState: ScrollState,
    laneHeight: androidx.compose.ui.unit.Dp = 140.dp
) {
    if (selectedTimeMs == null) return
    val cursorXPosition by remember(selectedTimeMs, zoomLevel, scrollState) {
        derivedStateOf {
            val px = selectedTimeMs * zoomLevel
            val scroll = scrollState.value.toFloat()
            (px - scroll).roundToInt()
        }
    }
    Box(
        modifier = Modifier
            .offset { IntOffset(cursorXPosition, 0) }
            .width(3.dp)
            .height(laneHeight)
            .background(
                color = Color(0xff8f8fff),
                shape = RoundedCornerShape(1.dp)
            )
            .zIndex(2f)
    ) {}
}
