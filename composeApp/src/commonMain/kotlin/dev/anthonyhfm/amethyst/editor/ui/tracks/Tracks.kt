package dev.anthonyhfm.amethyst.editor.ui.tracks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.data.tracks.Track
import dev.anthonyhfm.amethyst.editor.ui.tracks.ui.TrackElement
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Tracks(
    selectedTrack: Int?,
    onSelectTrack: (Int) -> Unit
) {
    val viewModel = koinViewModel<TracksViewModel>()
    val tracks: List<Track> by viewModel.tracks.collectAsState()

    Column(
        modifier = Modifier
            .padding(start = 12.dp, top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.2.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),

        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tracks.forEachIndexed { index, track ->
            TrackElement(
                selected = selectedTrack == index,
                onSelect = {
                    onSelectTrack(index)
                },
                track = track
            )
        }
    }
}