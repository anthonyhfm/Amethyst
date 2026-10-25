package dev.anthonyhfm.amethyst.timeline.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.timeline_clip_action_consolidate
import amethyst.composeapp.generated.resources.timeline_consolidation_failed
import amethyst.composeapp.generated.resources.timeline_consolidation_progress
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.primaryModifierShortcutLabel
import dev.anthonyhfm.amethyst.timeline.TimelineAudioConsolidation
import dev.anthonyhfm.amethyst.timeline.TimelineCommandSurface
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.ui.components.primitives.Progress
import dev.anthonyhfm.amethyst.ui.components.primitives.ToastProvider
import dev.anthonyhfm.amethyst.ui.components.primitives.ToastVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberToastState
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun TimelineConsolidateAction(onInvoked: () -> Unit = {}) {
    val selections by SelectionManager.selections.collectAsState()
    val progress by TimelineAudioConsolidation.progress.collectAsState()
    val tracks by TimelineRepository.tracks.collectAsState()
    TimelineContextMenuAction(
        label = stringResource(resource = Res.string.timeline_clip_action_consolidate),
        shortcut = primaryModifierShortcutLabel(key = "J"),
        enabled = progress == null && tracks.isNotEmpty() && TimelineCommandSurface.canConsolidateSelection(selections = selections),
        onClick = {
            TimelineCommandSurface.consolidateSelection()
            onInvoked()
        },
    )
}

@Composable
fun TimelineConsolidationFeedback(content: @Composable () -> Unit) {
    val progress by TimelineAudioConsolidation.progress.collectAsState()
    val error by TimelineAudioConsolidation.error.collectAsState()
    val toastState = rememberToastState()
    val failedLabel = stringResource(resource = Res.string.timeline_consolidation_failed)
    LaunchedEffect(key1 = error) {
        if (error != null) {
            toastState.show(title = failedLabel, description = error, variant = ToastVariant.Destructive)
            TimelineAudioConsolidation.dismissError()
        }
    }

    ToastProvider(state = toastState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            content()
            progress?.let { value ->
                Column(
                    modifier = Modifier
                        .align(alignment = Alignment.BottomCenter)
                        .width(width = 280.dp)
                        .background(color = TimelineTheme.palette.canvas)
                        .padding(all = 12.dp),
                ) {
                    Text(text = stringResource(resource = Res.string.timeline_consolidation_progress))

                    Progress(value = value)
                }
            }
        }
    }
}
