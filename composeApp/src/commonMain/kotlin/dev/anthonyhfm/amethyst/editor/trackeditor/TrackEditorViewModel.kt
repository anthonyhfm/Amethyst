package dev.anthonyhfm.amethyst.editor.trackeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.tracks.EffectTrack
import dev.anthonyhfm.amethyst.editor.plugins.EffectPlugin
import kotlinx.coroutines.launch

class TrackEditorViewModel(
    private val projectRepository: ProjectRepository
) : ViewModel() {
    val state = MutableStateFlow(
        TrackEditorState()
    )

    fun selectTrack(index: Int? = null) {
        viewModelScope.launch {
            state.emit(
                state.value.copy(
                    trackSelected = index != null,
                    selectedTrack = index,
                    effects = if (index != null) {
                        projectRepository
                            .tracks.value.filterIsInstance<EffectTrack>()[index!!]
                            .effects
                    } else {
                        null
                    }
                )
            )
        }
    }

    fun onAddEffect(effect: EffectPlugin, atIndex: Int? = null) {
        state.value.selectedTrack?.let { selectedTrack ->
            (projectRepository.tracks.value[selectedTrack] as EffectTrack).addEffect(
                effect = effect,
                atIndex = atIndex
            )
        }
    }
}

data class TrackEditorState(
    val trackSelected: Boolean = false,
    val selectedTrack: Int? = null,
    val effects: StateFlow<List<EffectPlugin>>? = null
)