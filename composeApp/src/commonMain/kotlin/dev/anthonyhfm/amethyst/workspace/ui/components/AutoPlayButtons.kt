package dev.anthonyhfm.amethyst.workspace.ui.components

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.AutoPlayRepository
import dev.anthonyhfm.amethyst.workspace.AutoPlayState

@Composable
fun AutoPlayButtons() {
    val autoPlayState by AutoPlayRepository.state.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        AutoPlaySettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }

    Row(
        modifier = Modifier
            .padding(bottom = 24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), CircleShape),

        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Play/Pause button - changes based on state
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .height(44.dp)
                .clickable {
                    when (autoPlayState) {
                        AutoPlayState.STOPPED -> AutoPlayRepository.startAutoPlay()
                        AutoPlayState.PLAYING -> AutoPlayRepository.pauseAutoPlay()
                        AutoPlayState.PAUSED -> AutoPlayRepository.resumeAutoPlay()
                    }
                },

            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (autoPlayState == AutoPlayState.PLAYING) {
                    Icons.Default.Pause
                } else {
                    Icons.Default.PlayArrow
                },
                contentDescription = when (autoPlayState) {
                    AutoPlayState.STOPPED -> "Start AutoPlay"
                    AutoPlayState.PLAYING -> "Pause AutoPlay"
                    AutoPlayState.PAUSED -> "Resume AutoPlay"
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .padding(12.dp)
                    .size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Stop button
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .height(44.dp)
                .clickable {
                    AutoPlayRepository.stopAutoPlay()
                },

            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop AutoPlay",
                modifier = Modifier
                    .clip(CircleShape)
                    .padding(12.dp)
                    .size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Settings button
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .height(44.dp)
                .clickable {
                    showSettingsDialog = true
                },

            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "AutoPlay Settings",
                modifier = Modifier
                    .clip(CircleShape)
                    .padding(12.dp)
                    .size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}