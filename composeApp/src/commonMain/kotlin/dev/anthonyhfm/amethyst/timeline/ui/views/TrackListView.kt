package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack

@Composable
fun TrackListView(
    tracks: List<TimelineTrack<*>>,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tracks.forEachIndexed { index, track ->
            TrackInfo(track = track, trackIndex = index)
        }
    }
}

@Composable
fun TrackInfo(
    track: TimelineTrack<*>,
    trackIndex: Int
) {
    val trackName = when (track) {
        is AudioTimelineTrack -> "Audio Track ${trackIndex + 1}"
        else -> "Track ${trackIndex + 1}"
    }

    val entryCount = when (track) {
        is AudioTimelineTrack -> track.entries.size
        else -> 0
    }

    Column(
        modifier = Modifier
            .width(200.dp)
            .height(140.dp)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(8.dp),
    ) {
        Text(
            text = trackName,
            style = MaterialTheme.typography.labelLarge.copy(
                lineHeight = MaterialTheme.typography.labelLarge.fontSize,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier
                .padding(4.dp)
        )

        if (entryCount > 0) {
            Text(
                text = "$entryCount clip${if (entryCount != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
            )
        }

        Spacer(
            modifier = Modifier
                .weight(1f)
        )

        Icon(
            imageVector = when (track) {
                is AudioTimelineTrack -> Icons.Default.Audiotrack
                else -> Icons.Default.Lightbulb
            },
            contentDescription = "Track Type Icon",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.End)
                .padding(8.dp)
                .size(24.dp)
        )
    }
}