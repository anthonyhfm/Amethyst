package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.TimelineViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlaybackControls(
    modifier: Modifier = Modifier
) {
    val viewModel: TimelineViewModel = koinViewModel()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play/Pause Button
        FilledIconButton(
            onClick = {
                if (isPlaying) {
                    viewModel.pause()
                } else {
                    viewModel.play()
                }
            },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // Stop Button
        FilledIconButton(
            onClick = { viewModel.stop() },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop",
                tint = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}
