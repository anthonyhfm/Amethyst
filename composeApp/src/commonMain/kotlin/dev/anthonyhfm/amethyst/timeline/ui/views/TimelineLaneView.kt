package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anthonyhfm.amethyst.timeline.TimelineViewModel
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun TimelineLaneView(scrollState: ScrollState) {
    val viewModel: TimelineViewModel = koinViewModel()
    val tracks by viewModel.tracks.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val playheadPositionMs by viewModel.playheadPositionMs.collectAsState()

    // Calculate content width based on the longest track
    val maxDurationMs = tracks.maxOfOrNull { track ->
        when (track) {
            is AudioTimelineTrack -> track.entries.values.maxOfOrNull { it.endTimeMs } ?: 0L
            else -> 0L
        }
    } ?: 10000L // Default minimum width

    val contentWidth = (maxDurationMs * zoomLevel + 1000).dp // Add some padding

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tracks.forEach { track ->
                TimelineLane(
                    track = track,
                    zoomLevel = zoomLevel,
                    contentWidth = contentWidth,
                    scrollState = scrollState
                )
            }
        }

        // Playhead cursor - positioned over the timeline lanes
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
    val playheadPixelPosition = (positionMs * zoomLevel).roundToInt()
    val scrollOffset = scrollState.value

    // Calculate cursor position relative to the timeline content
    val cursorXPosition = playheadPixelPosition - scrollOffset + 12 // 12dp for padding

    Box(
        modifier = Modifier
            .offset { IntOffset(cursorXPosition, 0) }
            .width(2.dp)
            .fillMaxHeight()
            .background(
                color = Color.Red,
                shape = RoundedCornerShape(1.dp)
            )
    )
}

@Composable
fun TimelineLane(
    track: TimelineTrack<*>,
    zoomLevel: Float,
    contentWidth: androidx.compose.ui.unit.Dp,
    scrollState: ScrollState
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .fillMaxWidth()
            .height(96.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp)
            .horizontalScroll(scrollState)
    ) {
        // Content container with proper width
        Box(
            modifier = Modifier
                .width(contentWidth)
                .height(84.dp)
        ) {
            // Render all entries in this track
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
fun AudioClip(
    audioEntry: AudioEntry,
    zoomLevel: Float
) {
    val startOffsetPx = (audioEntry.startTimeMs * zoomLevel).roundToInt()
    val widthPx = (audioEntry.durationMs * zoomLevel).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(startOffsetPx, 0) }
            .clip(RoundedCornerShape(4.dp))
            .height(84.dp)
            .width(widthPx.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = audioEntry.fileName.substringBeforeLast('.'),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${audioEntry.durationMs}ms",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}