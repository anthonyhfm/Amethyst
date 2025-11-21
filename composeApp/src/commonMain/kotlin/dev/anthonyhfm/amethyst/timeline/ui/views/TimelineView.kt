package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.timeline.TimelineViewModel

@Composable
fun TimelineView(
    viewModel: TimelineViewModel
) {
    val scrollState = rememberScrollState()
    val tracks by viewModel.tracks.collectAsState()

    LaunchedEffect(scrollState) {
        viewModel.setScrollState(scrollState)
    }

    Row(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        TrackListView(
            tracks = tracks,
            onAddLightsTrack = { viewModel.addLightsTrack() },
            onAddAudioTrack = { viewModel.addAudioTrack() }
        )

        TimelineLaneView(
            viewModel = viewModel,
            scrollState = scrollState,
            selectionViewportRelative = true,
            onDoubleClickLightsLane = { trackIndex, timeMs ->
                viewModel.onDoubleClickLightsTrack(trackIndex, timeMs)
            }
        )
    }
}
