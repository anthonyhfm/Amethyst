package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    var totalDuration: Double = 0.0
        private set

    private var playbackStartTime: Double = 0.0
    private var playbackOffset: Double = 0.0

    private val repoScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var progressJob: Job? = null

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = repoScope.launch {
            while (isActive) {
                val currentPos = (Heaven.time - playbackStartTime) + playbackOffset
                _progress.value = if (totalDuration > 0) {
                    (currentPos / totalDuration).toFloat().coerceIn(0f, 1f)
                } else 0f
                delay(16L)
            }
        }
    }

    fun startAutoPlay() {
        if (_state.value == AutoPlayState.PLAYING) return
        
        val autoplay = WorkspaceRepository.workspaceMeta?.autoPlay ?: return
        val settings = WorkspaceRepository.workspaceMeta?.settings

        Heaven.cancelJobsForOwner(this)

        playbackStartTime = Heaven.time
        
        if (_state.value != AutoPlayState.PAUSED) {
            playbackOffset = 0.0
            totalDuration = autoplay.actions.keys.maxOrNull() ?: 0.0
        }

        _state.value = AutoPlayState.PLAYING
        startProgressTracking()

        // Automatically stop when the last action has played
        Heaven.schedule(totalDuration - playbackOffset, this) {
            stopAutoPlay()
        }

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
        progressJob?.cancel()
        progressJob = null
        _state.value = AutoPlayState.PAUSED
    }

    fun stopAutoPlay() {
        Heaven.cancelJobsForOwner(this)
        progressJob?.cancel()
        progressJob = null
        _progress.value = 0f
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