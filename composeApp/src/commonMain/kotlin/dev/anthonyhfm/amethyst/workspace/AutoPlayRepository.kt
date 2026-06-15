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
import kotlinx.coroutines.flow.update
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData

enum class AutoPlayState {
    STOPPED,
    PLAYING,
    PAUSED,
    LEARNING
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

    private var learningIndex = 0
    private var sortedActionTimes = listOf<Double>()
    private var previousLearningActions: List<AutoPlayData.Action> = emptyList()

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

        val fromLearning = _state.value == AutoPlayState.LEARNING
        if (fromLearning) {
            playbackOffset = sortedActionTimes.getOrNull(learningIndex) ?: 0.0
            
            // Clear green lights manually
            val clearSignals = previousLearningActions.map {
                Signal.LED(origin = this, x = it.x, y = it.y, color = Color.Black, layer = 101)
            }
            Heaven.midiEnter(clearSignals)
        }

        Heaven.cancelJobsForOwner(this)

        playbackStartTime = Heaven.time
        
        if (_state.value != AutoPlayState.PAUSED && !fromLearning) {
            playbackOffset = 0.0
        }
        
        // Always ensure totalDuration is up to date
        totalDuration = autoplay.actions.keys.maxOrNull() ?: 0.0

        _state.value = AutoPlayState.PLAYING
        startProgressTracking()

        // Automatically stop when the last action has played (+ a small buffer to ensure last keys are turned off)
        Heaven.schedule(totalDuration - playbackOffset + 50.0, this) {
            stopAutoPlay()
        }

        autoplay.actions.entries.forEach { entry ->
            val adjustedDelay = entry.key - playbackOffset
            // Use a small epsilon to handle potential floating point precision issues
            if (adjustedDelay >= -0.001) {
                Heaven.schedule(adjustedDelay.coerceAtLeast(0.0), this) {
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
        Heaven.clear()
        progressJob?.cancel()
        progressJob = null
        _progress.value = 0f
        _state.value = AutoPlayState.STOPPED
        playbackOffset = 0.0
        playbackStartTime = 0.0
        learningIndex = 0
        previousLearningActions = emptyList()
    }

    fun startLearningMode() {
        if (_state.value == AutoPlayState.LEARNING) return
        
        val autoplay = WorkspaceRepository.workspaceMeta?.autoPlay ?: return
        
        val currentPos = if (_state.value == AutoPlayState.PLAYING) {
            (Heaven.time - playbackStartTime) + playbackOffset
        } else {
            playbackOffset
        }

        Heaven.cancelJobsForOwner(this)
        progressJob?.cancel()
        progressJob = null
        Heaven.clear()
        
        _state.value = AutoPlayState.LEARNING
        sortedActionTimes = autoplay.actions.keys.sorted()
        
        // Find the next step to learn based on current position
        learningIndex = sortedActionTimes.indexOfFirst { it >= currentPos }.coerceAtLeast(0)

        totalDuration = sortedActionTimes.lastOrNull() ?: 0.0
        
        showCurrentLearningStep()
    }

    private fun executeActions(actions: List<AutoPlayData.Action>) {
        WorkspaceRepository.samplingChain.signalEnter(
            actions.map {
                Signal.Midi(
                    origin = this,
                    x = it.x,
                    y = it.y,
                    velocity = if (it.down) 127 else 0,
                )
            }
        )
        
        val settings = WorkspaceRepository.workspaceMeta?.settings
        if (settings?.autoPlayShowLights == true) {
            WorkspaceRepository.lightsChain.signalEnter(
                actions.map {
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

    private fun showCurrentLearningStep() {
        val autoplay = WorkspaceRepository.workspaceMeta?.autoPlay ?: return
        val time = sortedActionTimes.getOrNull(learningIndex) ?: return
        val actions = autoplay.actions[time] ?: return

        val expectedDown = actions.filter { it.down }
        
        if (expectedDown.isEmpty()) {
            // Only releases here (no green lights to show), so auto-advance to the next press.
            executeActions(actions)
            learningIndex++
            if (learningIndex < sortedActionTimes.size) {
                _progress.value = (learningIndex.toFloat() / sortedActionTimes.size).coerceIn(0f, 1f)
                showCurrentLearningStep()
            } else {
                stopAutoPlay()
            }
            return
        }

        val clearSignals = previousLearningActions.map {
            Signal.LED(origin = this, x = it.x, y = it.y, color = Color.Black, layer = 101)
        }
        
        val showSignals = actions.map {
            Signal.LED(
                origin = this,
                x = it.x,
                y = it.y,
                color = if (it.down) Color.Green else Color.Black,
                layer = 101
            )
        }

        Heaven.midiEnter(clearSignals + showSignals)
        previousLearningActions = actions
    }

    fun onMidiInput(signals: List<Signal.Midi>) {
        if (_state.value != AutoPlayState.LEARNING) return

        val autoplay = WorkspaceRepository.workspaceMeta?.autoPlay ?: return
        val time = sortedActionTimes.getOrNull(learningIndex) ?: return
        val expectedActions = autoplay.actions[time] ?: return

        // Check if any "down" actions at this step are matched by the input
        val expectedDown = expectedActions.filter { it.down }.map { it.x to it.y }.toSet()
        val inputDown = signals.filter { it.velocity > 0 }.map { it.x to it.y }.toSet()

        if (inputDown.intersect(expectedDown).isNotEmpty()) {
            // Move autoplay according to keys what the user just hit.
            val inputCoords = signals.map { it.x to it.y }.toSet()
            val filteredActions = expectedActions.filter {
                (it.x to it.y) !in inputCoords
            }
            executeActions(filteredActions)

            learningIndex++
            if (learningIndex >= sortedActionTimes.size) {
                stopAutoPlay()
            } else {
                _progress.value = (learningIndex.toFloat() / sortedActionTimes.size).coerceIn(0f, 1f)
                showCurrentLearningStep()
            }
        }
    }

    fun resumeAutoPlay() {
        if (_state.value == AutoPlayState.PAUSED) {
            startAutoPlay()
        }
    }
}