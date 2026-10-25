package dev.anthonyhfm.amethyst.timeline.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.audio_export_selection_action
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.workspace.audio.export.AudioExport
import dev.anthonyhfm.amethyst.workspace.audio.export.captureTimelineExport
import org.jetbrains.compose.resources.stringResource

@Composable
fun TimelineExportAction(onInvoked: () -> Unit = {}) {
    if (!AudioExport.isAvailable) {
        return
    }
    val selections by SelectionManager.selections.collectAsState()
    val tracks by TimelineRepository.tracks.collectAsState()
    val hasAudio = remember(key1 = selections, key2 = tracks) {
        captureTimelineExport(
            tracks = tracks,
            selections = selections,
        ) != null
    }
    TimelineContextMenuAction(
        label = stringResource(resource = Res.string.audio_export_selection_action),
        enabled = hasAudio,
        onClick = {
            AudioExport.openTimeline()
            onInvoked()
        },
    )
}
