package dev.anthonyhfm.amethyst.advanced_editor.tracks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.data.project.ProjectDeviceConfig
import dev.anthonyhfm.amethyst.core.data.tracks.Track
import dev.anthonyhfm.amethyst.advanced_editor.tracks.ui.CreateTrackButton
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Tracks(
    selectedTrack: Int?,
    onSelectTrack: (Int) -> Unit
) {
    val viewModel = koinViewModel<TracksViewModel>()
    // val tracks: List<Track<*>> by viewModel.tracks.collectAsState()
    val deviceConfigs: List<ProjectDeviceConfig> by viewModel.deviceConfigs.collectAsState()

    val verticalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .padding(start = 12.dp, top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.2.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp), RoundedCornerShape(12.dp))
            .padding(12.dp),

    ) {
        Row(
            modifier = Modifier
                .fillMaxSize(),

            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .verticalScroll(verticalScrollState)
            ) {
                /*tracks.forEachIndexed { index, track ->
                    TrackElement(
                        selected = selectedTrack == index,
                        onSelect = {
                            onSelectTrack(index)
                        },
                        track = track,
                        deviceConfigs = deviceConfigs,
                        onChangeDeviceConfig = {
                            viewModel.changeDeviceConfig(index, it)
                        }
                    )
                }*/

                CreateTrackButton(
                    onCreate = {
                        viewModel.createTrack(it)
                    }
                )
            }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp), RoundedCornerShape(6.dp)),
            ) {

            }
        }
    }
}