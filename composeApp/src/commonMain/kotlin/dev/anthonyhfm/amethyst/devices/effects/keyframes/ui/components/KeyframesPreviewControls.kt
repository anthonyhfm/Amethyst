package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.timeline_playback_pause
import amethyst.composeapp.generated.resources.timeline_playback_play
import amethyst.composeapp.generated.resources.ui_primitive_carousel_next
import amethyst.composeapp.generated.resources.ui_primitive_carousel_previous
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceToolbarIconButton
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceToolbarSurface
import org.jetbrains.compose.resources.stringResource

@Composable
fun KeyframesPreviewControls(
    currentFrameIndex: Int,
    frameCount: Int,
    isPlaying: Boolean,
    onPreviousFrame: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNextFrame: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkspaceToolbarSurface {
            WorkspaceToolbarIconButton(
                onClick = onPreviousFrame,
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = stringResource(Res.string.ui_primitive_carousel_previous),
                enabled = currentFrameIndex > 0,
                showTooltip = false,
            )

            Crossfade(isPlaying) { playing ->
                WorkspaceToolbarIconButton(
                    onClick = onTogglePlayback,
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (playing) Res.string.timeline_playback_pause else Res.string.timeline_playback_play
                    ),
                    variant = if (playing) ButtonVariant.Default else ButtonVariant.Ghost,
                    enabled = frameCount > 0,
                    showTooltip = false,
                )
            }

            WorkspaceToolbarIconButton(
                onClick = onNextFrame,
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(Res.string.ui_primitive_carousel_next),
                enabled = currentFrameIndex < frameCount - 1,
                showTooltip = false,
            )
        }
    }
}
