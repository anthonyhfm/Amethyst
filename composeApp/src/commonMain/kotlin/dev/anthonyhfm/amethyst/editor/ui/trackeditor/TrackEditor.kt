package dev.anthonyhfm.amethyst.editor.ui.trackeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TrackEditor(
    selectedTrack: Int? = null
) {
    val viewModel = koinViewModel<TrackEditorViewModel>()
    val state: TrackEditorState by viewModel.state.collectAsState()

    LaunchedEffect(selectedTrack) {
        viewModel.selectTrack(selectedTrack)
    }

    Row(
        modifier = Modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .height(280.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.5.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp), RoundedCornerShape(12.dp))
            .padding(12.dp),

        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.trackSelected && state.effects != null) {
            val effects by state.effects!!.collectAsState()

            effects.forEach {
                it.Content()
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight(),

                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        viewModel.onAddEffect()
                    }
                ) {
                    Icon(Icons.Default.Add, null)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize(),

                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a track for editing it's contents".uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}