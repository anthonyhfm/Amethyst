package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import dev.anthonyhfm.amethyst.timeline.TimelineViewModel
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.ui.components.TimelineRuler
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TimelineView(
    viewModel: TimelineViewModel
) {
    val tracks by viewModel.tracks.collectAsState()
    val viewport by viewModel.viewport.collectAsState()
    val bpm by WorkspaceRepository.bpm.collectAsState()
    val gridType by WorkspaceRepository.gridType.collectAsState()
    val timelinePalette = TimelineTheme.palette
    val timelineDimensions = TimelineTheme.dimensions
    val openChainEffectClipId by viewModel.openChainEffectClipId.collectAsState()
    val timelineSourceIds = tracks
        .filterIsInstance<AudioTimelineTrack>()
        .flatMap { track -> track.entries.values.map { it.sourceId } }
        .filter(String::isNotBlank)
        .distinct()

    LaunchedEffect(timelineSourceIds) {
        withContext(Dispatchers.Default) {
            Echo.prepareSources(timelineSourceIds)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(timelinePalette.canvas)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(timelineDimensions.rulerHeight)
        ) {
            Box(
                modifier = Modifier
                    .width(timelineDimensions.trackHeaderWidth)
                    .fillMaxHeight()
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(
                            color = timelinePalette.rulerHighlight.copy(alpha = 0.92f),
                            start = Offset(0f, stroke / 2f),
                            end = Offset(size.width, stroke / 2f),
                            strokeWidth = stroke
                        )
                        drawLine(
                            color = timelinePalette.shellBorder,
                            start = Offset(size.width - stroke / 2f, 0f),
                            end = Offset(size.width - stroke / 2f, size.height),
                            strokeWidth = stroke
                        )
                        drawLine(
                            color = timelinePalette.shellBorder,
                            start = Offset(0f, size.height - stroke / 2f),
                            end = Offset(size.width, size.height - stroke / 2f),
                            strokeWidth = stroke
                        )
                    }
            )
            TimelineRuler(
                modifier = Modifier.weight(1f),
                viewport = viewport,
                bpm = bpm,
                gridType = gridType
            )
        }

        val verticalScrollState = rememberScrollState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            TrackListView(
                tracks = tracks,
                verticalScrollState = verticalScrollState,
                onTrackVolumeChange = { trackIndex, value ->
                    viewModel.setTrackBaseAutomation(trackIndex, TimelineTrackAutomationTarget.VOLUME, value)
                },
                onTrackSoloToggle = { trackIndex ->
                    viewModel.toggleTrackSolo(trackIndex)
                },
                onTrackMuteToggle = { trackIndex ->
                    viewModel.toggleTrackMute(trackIndex)
                },
                onTrackReorder = { fromIndex, toIndex ->
                    viewModel.reorderTracks(fromIndex, toIndex)
                },
                onAddLightsTrack = { viewModel.addMidiTrack() },
                onAddAudioTrack = { viewModel.addAudioTrack() }
            )

            TimelineLaneView(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                verticalScrollState = verticalScrollState,
                onOpenMidiEntryAtTime = { trackIndex, timeMs ->
                    viewModel.onDoubleClickMidiTrack(trackIndex, timeMs)
                },
                onCreateMidiEntry = { trackIndex, startMs, endMs ->
                    viewModel.createMidiEntry(trackIndex, startMs, endMs)
                }
            )
        }

        val experimentalChainEffectEnabled by dev.anthonyhfm.amethyst.settings.data.ExperimentalSettings.timelineChainEffects.flow.collectAsState()
        if (experimentalChainEffectEnabled && openChainEffectClipId != null) {
            TimelineChainEffectPanel(
                viewModel = viewModel,
                clipId = openChainEffectClipId!!,
                modifier = Modifier.fillMaxWidth().height(320.dp),
            )
        }
    }
}
