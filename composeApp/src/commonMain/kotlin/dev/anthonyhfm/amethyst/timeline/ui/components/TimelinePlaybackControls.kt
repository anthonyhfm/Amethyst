package dev.anthonyhfm.amethyst.timeline.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.TimelineMetronome
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.ui.icons.AmethystIcons
import dev.anthonyhfm.amethyst.ui.icons.outlined.Metronome
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceToolbarIconButton
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceToolbarSurface

@Composable
fun TimelinePlaybackControls() {
    val isPlaying: Boolean by TimelineRepository.isPlaying.collectAsState()
    val metronomeEnabled by TimelineMetronome.enabled.collectAsState()
    val metronomeState = stringResource(
        resource = if (metronomeEnabled) Res.string.timeline_metronome_enabled else Res.string.timeline_metronome_disabled,
    )
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
                contentDescription = if (playing) stringResource(Res.string.timeline_playback_pause) else stringResource(Res.string.timeline_playback_play),
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
            contentDescription = stringResource(Res.string.timeline_playback_stop),
        )

        Separator(
            modifier = Modifier
                .height(height = 20.dp),
            orientation = SeparatorOrientation.Vertical,
        )

        WorkspaceToolbarIconButton(
            onClick = { TimelineMetronome.setEnabled(enabled = !metronomeEnabled) },
            imageVector = AmethystIcons.Outlined.Metronome,
            contentDescription = stringResource(resource = Res.string.timeline_metronome),
            variant = if (metronomeEnabled) ButtonVariant.Default else ButtonVariant.Ghost,
            modifier = Modifier
                .semantics {
                    role = Role.Switch
                    toggleableState = if (metronomeEnabled) ToggleableState.On else ToggleableState.Off
                    stateDescription = metronomeState
                },
        )
    }
}
