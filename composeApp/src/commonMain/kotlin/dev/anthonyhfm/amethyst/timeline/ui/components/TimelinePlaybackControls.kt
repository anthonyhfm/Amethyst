package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.TimelineRepository

@Composable
fun TimelinePlaybackControls() {
    val isPlaying: Boolean by TimelineRepository.isPlaying.collectAsState()

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(0.2f), CircleShape),

        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .height(44.dp)
                .clickable {
                    if (isPlaying) {
                        TimelineRepository.pause()
                    } else {
                        TimelineRepository.play()
                    }
                },

            contentAlignment = Alignment.Center,
        ) {
            Crossfade(isPlaying) {
                Icon(
                    imageVector = if (it) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(12.dp)
                        .size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .height(44.dp)
                .clickable {
                    TimelineRepository.stop()
                },

            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop Timeline",
                modifier = Modifier
                    .clip(CircleShape)
                    .padding(12.dp)
                    .size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}