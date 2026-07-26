package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BookOpenText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Square
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Progress
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.AutoPlayRepository
import dev.anthonyhfm.amethyst.workspace.AutoPlayState
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.roundToInt

private fun formatTime(millis: Double): String {
    val totalSeconds = (millis / 1000.0).roundToInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    return "$minutes:${secs.toString().padStart(2, '0')}"
}

@Composable
fun AutoPlayButtons() {
    val autoPlayState by AutoPlayRepository.state.collectAsState()
    val progress by AutoPlayRepository.progress.collectAsState()
    val totalDuration = AutoPlayRepository.totalDuration
    var showSettingsDialog by remember { mutableStateOf(false) }

    val hasAutoPlayData = WorkspaceRepository.workspaceMeta?.autoPlay?.actions?.isNotEmpty() == true
    val showProgress = hasAutoPlayData && autoPlayState != AutoPlayState.STOPPED

    if (showSettingsDialog) {
        AutoPlaySettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .widthIn(min = 280.dp, max = 320.dp)
            .background(Theme[colors][card], DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        AnimatedVisibility(
            visible = showProgress,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(progress * totalDuration),
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                    )
                    Text(
                        text = formatTime(totalDuration),
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                    )
                }
                Progress(value = progress)
            }
        }

        AnimatedVisibility(visible = showProgress) {
            Separator(orientation = SeparatorOrientation.Horizontal)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkspaceToolbarIconButton(
                onClick = {
                    when (autoPlayState) {
                        AutoPlayState.STOPPED -> AutoPlayRepository.startAutoPlay()
                        AutoPlayState.PLAYING -> AutoPlayRepository.pauseAutoPlay()
                        AutoPlayState.PAUSED -> AutoPlayRepository.resumeAutoPlay()
                        AutoPlayState.LEARNING -> {
                            AutoPlayRepository.startAutoPlay()
                        }
                    }
                },
                imageVector = if (autoPlayState == AutoPlayState.PLAYING) Lucide.Pause else Lucide.Play,
                contentDescription = when (autoPlayState) {
                    AutoPlayState.STOPPED -> stringResource(Res.string.workspace_autoplay_start)
                    AutoPlayState.PLAYING -> stringResource(Res.string.workspace_autoplay_pause)
                    AutoPlayState.PAUSED -> stringResource(Res.string.workspace_autoplay_resume)
                    AutoPlayState.LEARNING -> stringResource(Res.string.workspace_autoplay_switch_to_normal)
                },
                variant = if (autoPlayState == AutoPlayState.PLAYING) ButtonVariant.Default else ButtonVariant.Ghost,
            )

            WorkspaceToolbarIconButton(
                onClick = {
                    if (autoPlayState == AutoPlayState.LEARNING) {
                        AutoPlayRepository.stopAutoPlay()
                    } else {
                        AutoPlayRepository.startLearningMode()
                    }
                },
                imageVector = Lucide.BookOpenText,
                contentDescription = if (autoPlayState == AutoPlayState.PLAYING) stringResource(Res.string.workspace_autoplay_switch_to_learning) else stringResource(Res.string.workspace_autoplay_learning_mode),
                variant = if (autoPlayState == AutoPlayState.LEARNING) ButtonVariant.Default else ButtonVariant.Ghost,
            )

            WorkspaceToolbarIconButton(
                onClick = { AutoPlayRepository.stopAutoPlay() },
                imageVector = Lucide.Square,
                contentDescription = stringResource(Res.string.workspace_autoplay_stop),
                enabled = autoPlayState != AutoPlayState.STOPPED,
            )

            Spacer(modifier = Modifier.weight(1f))

            WorkspaceToolbarIconButton(
                onClick = { showSettingsDialog = true },
                imageVector = Lucide.Settings,
                contentDescription = stringResource(Res.string.workspace_autoplay_settings),
            )
        }
    }
}

@Composable
fun MobileAutoPlayButtons() {
    val autoPlayState by AutoPlayRepository.state.collectAsState()
    val progress by AutoPlayRepository.progress.collectAsState()
    val totalDuration = AutoPlayRepository.totalDuration
    var showSettingsDialog by remember { mutableStateOf(false) }

    val hasAutoPlayData = WorkspaceRepository.workspaceMeta?.autoPlay?.actions?.isNotEmpty() == true
    val title = WorkspaceRepository.workspaceMeta?.title?.takeIf { it.isNotBlank() } ?: "AutoPlay"

    if (showSettingsDialog) {
        AutoPlaySettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme[colors][card], RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header Row: Track Info, Status Badge & Settings Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Theme[typography][p],
                    color = Theme[colors][foreground],
                    maxLines = 1,
                )
                Text(
                    text = when (autoPlayState) {
                        AutoPlayState.STOPPED -> "Stopped"
                        AutoPlayState.PLAYING -> "Playing"
                        AutoPlayState.PAUSED -> "Paused"
                        AutoPlayState.LEARNING -> "Learning Mode"
                    },
                    style = Theme[typography][small],
                    color = if (autoPlayState == AutoPlayState.LEARNING) Theme[colors][primary] else Theme[colors][mutedForeground],
                )
            }

            WorkspaceToolbarIconButton(
                onClick = { showSettingsDialog = true },
                imageVector = Lucide.Settings,
                contentDescription = stringResource(Res.string.workspace_autoplay_settings),
                variant = ButtonVariant.Ghost,
            )
        }

        // Timeline Progress Bar Row
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Progress(
                value = if (hasAutoPlayData) progress else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(if (hasAutoPlayData) progress * totalDuration else 0.0),
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                )
                Text(
                    text = formatTime(if (hasAutoPlayData) totalDuration else 0.0),
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                )
            }
        }

        // Spotify Style Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Learning Mode Toggle Button
            WorkspaceToolbarIconButton(
                onClick = {
                    if (autoPlayState == AutoPlayState.LEARNING) {
                        AutoPlayRepository.stopAutoPlay()
                    } else {
                        AutoPlayRepository.startLearningMode()
                    }
                },
                imageVector = Lucide.BookOpenText,
                contentDescription = if (autoPlayState == AutoPlayState.PLAYING) stringResource(Res.string.workspace_autoplay_switch_to_learning) else stringResource(Res.string.workspace_autoplay_learning_mode),
                variant = if (autoPlayState == AutoPlayState.LEARNING) ButtonVariant.Default else ButtonVariant.Ghost,
            )

            // Prominent Center Spotify Play / Pause Circular Button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Theme[colors][primary])
                    .clickable {
                        when (autoPlayState) {
                            AutoPlayState.STOPPED -> AutoPlayRepository.startAutoPlay()
                            AutoPlayState.PLAYING -> AutoPlayRepository.pauseAutoPlay()
                            AutoPlayState.PAUSED -> AutoPlayRepository.resumeAutoPlay()
                            AutoPlayState.LEARNING -> AutoPlayRepository.startAutoPlay()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (autoPlayState == AutoPlayState.PLAYING) Lucide.Pause else Lucide.Play,
                    contentDescription = when (autoPlayState) {
                        AutoPlayState.STOPPED -> stringResource(Res.string.workspace_autoplay_start)
                        AutoPlayState.PLAYING -> stringResource(Res.string.workspace_autoplay_pause)
                        AutoPlayState.PAUSED -> stringResource(Res.string.workspace_autoplay_resume)
                        AutoPlayState.LEARNING -> stringResource(Res.string.workspace_autoplay_switch_to_normal)
                    },
                    tint = Theme[colors][primaryForeground],
                    modifier = Modifier.size(28.dp),
                )
            }

            // Stop Button
            WorkspaceToolbarIconButton(
                onClick = { AutoPlayRepository.stopAutoPlay() },
                imageVector = Lucide.Square,
                contentDescription = stringResource(Res.string.workspace_autoplay_stop),
                enabled = autoPlayState != AutoPlayState.STOPPED,
                variant = ButtonVariant.Ghost,
            )
        }
    }
}