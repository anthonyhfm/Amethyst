package dev.anthonyhfm.amethyst.advanced_editor.trackeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.tracks.AudioTrack
import dev.anthonyhfm.amethyst.core.data.tracks.EffectTrack
import dev.anthonyhfm.amethyst.devices.BaseDevice
import kotlinx.coroutines.launch

class TrackEditorViewModel(
    private val projectRepository: ProjectRepository
) : ViewModel() {
    val state = MutableStateFlow(
        TrackEditorState()
    )

    fun selectTrack(index: Int? = null) {
        viewModelScope.launch {
            /*state.emit(
                state.value.copy(
                    trackSelected = index != null,
                    selectedTrack = index,
                    devices = index?.let {
                        projectRepository.tracks.value[index].devices
                    }
                )
            )*/
        }
    }

    fun onAddDevice(device: BaseDevice<*, *>, atIndex: Int? = null) {
        /*state.value.selectedTrack?.let { selectedTrack ->
            when (projectRepository.tracks.value[selectedTrack]) {
                is EffectTrack -> {
                    projectRepository.tracks.value[selectedTrack].addDevice(
                        device = device,
                        atIndex = atIndex
                    )
                }

                is AudioTrack -> {
                    projectRepository.tracks.value[selectedTrack].addDevice(
                        device = device,
                        atIndex = atIndex
                    )
                }
            }
        }*/
    }
}

data class TrackEditorState(
    val trackSelected: Boolean = false,
    val selectedTrack: Int? = null,
    val devices: StateFlow<List<BaseDevice<*, *>>>? = null
)