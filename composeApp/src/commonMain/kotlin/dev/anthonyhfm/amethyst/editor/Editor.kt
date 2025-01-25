package dev.anthonyhfm.amethyst.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.editor.ui.projectsettings.ProjectSettingsPanel
import dev.anthonyhfm.amethyst.editor.ui.trackeditor.TrackEditor
import dev.anthonyhfm.amethyst.editor.ui.tracks.Tracks
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun Editor() {
    val viewModel = koinViewModel<EditorViewModel>()
    val state by viewModel.state.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Tracks(
                        selectedTrack = state.selectedTrack,
                        onSelectTrack = {
                            viewModel.selectTrack(it)
                        }
                    )
                }

                ProjectSettingsPanel()
            }

            TrackEditor(
                selectedTrack = state.selectedTrack
            )
        }
    }
}