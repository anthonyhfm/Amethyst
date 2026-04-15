package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceToolbarIconButton
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceToolbarSurface

@Composable
fun TimelinePlaybackControls() {
    val isPlaying: Boolean by TimelineRepository.isPlaying.collectAsState()
    val playVariant = if (isPlaying) ButtonVariant.Default else ButtonVariant.Secondary

    WorkspaceToolbarSurface {
        Crossfade(isPlaying) { playing ->
            WorkspaceToolbarIconButton(
                onClick = {
                    if (playing) {
                        TimelineRepository.pause()
                    } else {
                        TimelineRepository.play()
                    }
                },
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                variant = playVariant,
            )
        }

        Separator(
            modifier = Modifier.height(20.dp),
            orientation = SeparatorOrientation.Vertical,
        )

        WorkspaceToolbarIconButton(
            onClick = { TimelineRepository.stop() },
            imageVector = Icons.Default.Stop,
            contentDescription = "Stop Timeline",
        )
    }
}
