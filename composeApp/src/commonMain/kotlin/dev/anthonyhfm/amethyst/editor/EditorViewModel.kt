package dev.anthonyhfm.amethyst.editor

import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.tracks.EffectTrack
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiInput
import kotlinx.coroutines.launch

class EditorViewModel(
    private val projectRepository: ProjectRepository,
    midiAccess: MidiAccess
) : ViewModel() {
    val state = MutableStateFlow(EditorState())

    init {
        viewModelScope.launch {
            projectRepository.launchpadConfigs.collect {
                it.forEachIndexed { deviceIndex, deviceConfig ->
                    projectRepository.tracks.emit(
                        projectRepository.tracks.value.filterIsInstance<EffectTrack>().map {
                            if (it.projectDeviceIndex == deviceIndex) {
                                it.midiOutput = deviceConfig.output

                                return@map it
                            } else {
                                return@map it
                            }
                        }
                    )

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
        projectRepository.tracks.value.filterIsInstance<EffectTrack>().forEach { track ->
            track.projectDeviceIndex?.let { projectDeviceIndex ->
                if (projectRepository.launchpadConfigs.value[projectDeviceIndex].input == midiInput) {
                    track.processMidiInputData(
                        midiInputData = MidiInputData(
                            pitch = data[1].toInt(),
                            velocity = data[2].toInt()
                        )
                    )
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