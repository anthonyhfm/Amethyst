package dev.anthonyhfm.amethyst.advanced_editor.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.project.ProjectDeviceConfig
import dev.anthonyhfm.amethyst.core.data.tracks.AudioTrack
import dev.anthonyhfm.amethyst.core.data.tracks.EffectTrack
import dev.anthonyhfm.amethyst.core.data.tracks.Track
import dev.anthonyhfm.amethyst.advanced_editor.tracks.ui.CreateTrackType
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TracksViewModel(
    private val projectRepository: ProjectRepository
) : ViewModel() {
    // val tracks: StateFlow<List<Track<*>>> = projectRepository.tracks
    val deviceConfigs: StateFlow<List<ProjectDeviceConfig>> = projectRepository.launchpadConfigs

    fun createTrack(trackType: CreateTrackType) {
        when (trackType) {
            CreateTrackType.Effect -> {
                viewModelScope.launch {
                    /*projectRepository.tracks.update {
                        it.plus(
                            EffectTrack(
                                name = "Effect Track ${it.size + 1}"
                            )
                        )
                    }*/
                }
            }

            CreateTrackType.Audio -> {
                viewModelScope.launch {
                    /*projectRepository.tracks.update {
                        it.plus(
                            AudioTrack(
                                name = "Audio Track ${it.size + 1}"
                            )
                        )
                    }*/
                }
            }
        }
    }

    fun changeDeviceConfig(trackIndex: Int, deviceIndex: Int) {
        viewModelScope.launch {
            /*projectRepository.tracks.update {
                it.mapIndexed { index, track ->
                    if (index == trackIndex && track is EffectTrack) {
                        track.projectDeviceIndex = deviceIndex
                        track.midiOutput = projectRepository.launchpadConfigs.value[deviceIndex].output
                        track.deviceType = projectRepository.launchpadConfigs.value[deviceIndex].type
                    }

                    track
                }
            }*/
        }
    }
}