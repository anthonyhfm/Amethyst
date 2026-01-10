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

    private var playbackStartTime: Double = 0.0
    private var playbackOffset: Double = 0.0

    fun startAutoPlay() {
        if (_state.value == AutoPlayState.PLAYING) return
        
        val autoplay = WorkspaceRepository.workspaceMeta?.autoPlay ?: return
        val settings = WorkspaceRepository.workspaceMeta?.settings

        Heaven.cancelJobsForOwner(this)

        playbackStartTime = Heaven.time
        
        if (_state.value != AutoPlayState.PAUSED) {
            playbackOffset = 0.0
        }

        _state.value = AutoPlayState.PLAYING

        autoplay.actions.entries.forEach { entry ->
            val adjustedDelay = entry.key - playbackOffset
            if (adjustedDelay >= 0) {
                Heaven.schedule(adjustedDelay, this) {
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

                    if (settings?.autoPlayShowButtonPresses == true) {
                        Heaven.midiEnter(
                            entry.value.map {
                                Signal.LED(
                                    origin = this,
                                    x = it.x,
                                    y = it.y,
                                    color = if (it.down) Color.White else Color.Black,
                                    layer = 100
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