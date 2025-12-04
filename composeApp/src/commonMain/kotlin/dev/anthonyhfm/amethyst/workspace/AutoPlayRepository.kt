package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoPlayState {
    STOPPED,
    PLAYING,
    PAUSED
}

object AutoPlayRepository {
    private val _state = MutableStateFlow(AutoPlayState.STOPPED)
    val state: StateFlow<AutoPlayState> = _state.asStateFlow()

    private var playbackStartTime: Double = 0.0  // Heaven.time when playback started
    private var playbackOffset: Double = 0.0     // Where in the autoplay timeline we are

    fun startAutoPlay() {
        if (_state.value == AutoPlayState.PLAYING) return
        
        val autoplay = WorkspaceRepository.saveableWorkspaceData?.autoPlay ?: return
        val settings = WorkspaceRepository.saveableWorkspaceData?.settings

        // Cancel any existing jobs
        Heaven.cancelJobsForOwner(this)

        // Record when we're starting
        playbackStartTime = Heaven.time
        
        // If not paused, start from beginning
        if (_state.value != AutoPlayState.PAUSED) {
            playbackOffset = 0.0
        }

        _state.value = AutoPlayState.PLAYING

        autoplay.actions.entries.forEach { entry ->
            val adjustedDelay = entry.key - playbackOffset
            if (adjustedDelay >= 0) {
                Heaven.schedule(adjustedDelay, this) {
                    // Send MIDI signals to sampling chain
                    WorkspaceRepository.samplingChain.signalEnter(
                        entry.value.map {
                            Signal.Midi(
                                origin = this,
                                x = it.x,
                                y = it.y,
                                velocity = if (it.down) 127 else 0,
                            )
                        }
                    )

                    // Send LED signals to lights chain if enabled
                    if (settings?.autoPlayShowLights == true) {
                        WorkspaceRepository.lightsChain.signalEnter(
                            entry.value.map {
                                Signal.LED(
                                    origin = this,
                                    x = it.x,
                                    y = it.y,
                                    color = if (it.down) Color.White else Color.Black,
                                )
                            }
                        )
                    }

                    // Show button presses on layer 100 if enabled
                    // This sends visual feedback to Heaven directly rather than through
                    // the lights chain, allowing button press visualization on devices
                    if (settings?.autoPlayShowButtonPresses == true) {
                        Heaven.midiEnter(
                            entry.value.map {
                                Signal.LED(
                                    origin = this,
                                    x = it.x,
                                    y = it.y,
                                    color = if (it.down) Color.White else Color.Black,
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    fun pauseAutoPlay() {
        if (_state.value != AutoPlayState.PLAYING) return
        
        // Calculate how far into the playback we are
        playbackOffset += (Heaven.time - playbackStartTime)
        Heaven.cancelJobsForOwner(this)
        _state.value = AutoPlayState.PAUSED
    }

    fun stopAutoPlay() {
        Heaven.cancelJobsForOwner(this)
        _state.value = AutoPlayState.STOPPED
        playbackOffset = 0.0
        playbackStartTime = 0.0
    }

    fun resumeAutoPlay() {
        if (_state.value == AutoPlayState.PAUSED) {
            startAutoPlay()
        }
    }
}