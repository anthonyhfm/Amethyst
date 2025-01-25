package dev.anthonyhfm.amethyst.editor.ui.tracks

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.tracks.Track
import kotlinx.coroutines.flow.asStateFlow

class TracksViewModel(
    private val projectRepository: ProjectRepository
) : ViewModel() {
    val tracks: StateFlow<List<Track>> = projectRepository.tracks
}