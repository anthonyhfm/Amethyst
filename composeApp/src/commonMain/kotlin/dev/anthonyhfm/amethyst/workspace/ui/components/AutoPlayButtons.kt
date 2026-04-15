package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.AutoPlayRepository
import dev.anthonyhfm.amethyst.workspace.AutoPlayState
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.roundToInt

private fun formatSeconds(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val minutes = total / 60
    val secs = total % 60
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
            .padding(bottom = 24.dp)
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
                        text = formatSeconds(progress * totalDuration),
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                    )
                    Text(
                        text = formatSeconds(totalDuration),
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
                    }
                },
                imageVector = if (autoPlayState == AutoPlayState.PLAYING) Lucide.Pause else Lucide.Play,
                contentDescription = when (autoPlayState) {
                    AutoPlayState.STOPPED -> "Start AutoPlay"
                    AutoPlayState.PLAYING -> "Pause AutoPlay"
                    AutoPlayState.PAUSED -> "Resume AutoPlay"
                },
                variant = if (autoPlayState == AutoPlayState.PLAYING) ButtonVariant.Default else ButtonVariant.Ghost,
            )

            WorkspaceToolbarIconButton(
                onClick = { AutoPlayRepository.stopAutoPlay() },
                imageVector = Lucide.Square,
                contentDescription = "Stop AutoPlay",
                enabled = autoPlayState != AutoPlayState.STOPPED,
            )

            Spacer(modifier = Modifier.weight(1f))

            WorkspaceToolbarIconButton(
                onClick = { showSettingsDialog = true },
                imageVector = Lucide.Settings,
                contentDescription = "AutoPlay Settings",
            )
        }
    }
}