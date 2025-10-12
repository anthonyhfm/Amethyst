package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.timeline.TimelineViewModel
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.ui.components.WaveformView
import dev.anthonyhfm.amethyst.ui.dnd.fileDropTarget
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import kotlin.math.roundToInt

@Composable
fun TimelineLaneView(
    viewModel: TimelineViewModel,
    scrollState: ScrollState
) {
    val tracks by viewModel.tracks.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val playheadPositionMs by viewModel.playheadPositionMs.collectAsState()

    val density = LocalDensity.current

    val maxDurationMs = tracks.maxOfOrNull { track ->
        when (track) {
            is AudioTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            else -> 0L
        }
    } ?: 10000L

    val contentWidthPx = maxDurationMs * zoomLevel + 1000
    val contentWidth = with(density) { contentWidthPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tracks.forEachIndexed { index, track ->
                TimelineLane(
                    track = track,
                    zoomLevel = zoomLevel,
                    contentWidth = contentWidth,
                    scrollState = scrollState,
                    onDropInFile = { file ->
                        viewModel.addAudioFileToTrack(
                            trackIndex = index,
                            file = file,
                            at = playheadPositionMs
                        )
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
    // Verwende derivedStateOf um nur bei relevanten Änderungen zu invalidieren
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
    zoomLevel: Float, // px per ms
    contentWidth: androidx.compose.ui.unit.Dp,
    scrollState: ScrollState,
    onDropInFile: (file: PlatformFile) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .fileDropTarget(
                onHover = { _, _, _ -> },
                onDrop = { files ->
                    val audioFiles = files.filter { it.extension.lowercase() in AudioDecoder.getSupportedFormats() }
                    if (audioFiles.isNotEmpty()) {
                        onDropInFile(audioFiles.first())
                    }
                }
            )
            .horizontalScroll(scrollState)
    ) {
        Box(
            modifier = Modifier
                .width(contentWidth)
                .height(140.dp)
        ) {
            // Raster zuerst zeichnen
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

    val isDark = true

    val minSpacingPx = 48f
    val candidates = longArrayOf(1,2,5,10,20,50,100,200,500,1000,2000,5000,10000,20000,60000)
    val intervalMs = candidates.firstOrNull { it * zoomLevel >= minSpacingPx } ?: candidates.last()
    val majorEvery = when (intervalMs) {
        1L,2L,5L -> 10
        10L,20L -> 5
        50L -> 4
        100L,200L,500L -> 5
        1000L -> 5
        2000L,5000L -> 6
        else -> 2
    }
    val majorIntervalMs = intervalMs * majorEvery

    // Deutlichere Linien: in Light Theme echtes Schwarz, in Dark Theme sehr helles Grau/Weiß
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
    zoomLevel: Float // px per ms
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
                    // ebenfalls transparenter, aber leicht dunkler für Lesbarkeit
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