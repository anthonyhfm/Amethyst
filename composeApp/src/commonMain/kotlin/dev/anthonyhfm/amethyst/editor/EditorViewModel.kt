package dev.anthonyhfm.amethyst.editor

import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.tracks.EffectTrack
import dev.anthonyhfm.amethyst.core.midi.data.getMidiInputData
import dev.atsushieno.ktmidi.MidiInput
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditorViewModel(
    private val projectRepository: ProjectRepository,
) : ViewModel() {
    val state = MutableStateFlow(EditorState())

    init {
        viewModelScope.launch {
            projectRepository.launchpadConfigs.collect {
                it.forEachIndexed { deviceIndex, deviceConfig ->
                    projectRepository.tracks.update {
                        it.map {
                            if (it.projectDeviceIndex == deviceIndex) {
                                if (it is EffectTrack) {
                                    it.midiOutput = deviceConfig.output
                                    it.deviceType = deviceConfig.type
                                }

                                return@map it
                            } else {
                                return@map it
                            }
                        }
                    }

                    deviceConfig.input?.setMessageReceivedListener(
                        listener = { bytes, b, c, d ->
                            handleMidiInput(deviceConfig.input, bytes)
                        }
                    )
                }
            }
        }
    }

    private fun handleMidiInput(midiInput: MidiInput, data: ByteArray) {
        projectRepository.tracks.value.forEach { track ->
            track.projectDeviceIndex?.let { projectDeviceIndex ->
                if (projectRepository.launchpadConfigs.value[projectDeviceIndex].input == midiInput) {
                    getMidiInputData(data)?.let {
                        track.processMidiInputData(it)
                    }
                }
            }
        }
    }

    fun selectTrack(trackIndex: Int) {
        viewModelScope.launch {
            state.emit(
                value = state.value.copy(
                    selectedTrack = trackIndex
                )
            )
        }
    }
}

data class EditorState(
    val selectedTrack: Int? = null
)