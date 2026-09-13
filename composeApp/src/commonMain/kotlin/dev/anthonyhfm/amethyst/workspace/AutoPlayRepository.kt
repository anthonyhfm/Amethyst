package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.audio.command.AudioStopTicket
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.devicesDepthFirst
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
import kotlinx.atomicfu.atomic
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.resolveLaunchpadOrigin
import kotlin.concurrent.Volatile
import kotlin.math.roundToLong

enum class AutoPlayState {
    STOPPED,
    PLAYING,
    PAUSED,
    LEARNING
}

internal const val AUTO_PLAY_AUDIO_LOOKAHEAD_MS = 12.0
private const val AUTO_PLAY_RESET_POLL_MS = 1.0
private const val AUTO_PLAY_AUDIO_PUMP_MAX_SLEEP_NANOS = 1_000_000_000L
private const val AUTO_PLAY_AUDIO_PUMP_ID = "autoplay-audio-pump"

private data class AutoPlayAudioTimeline(
    val playbackStartNanos: Long,
    val audioAnchorFrame: Long,
    val sampleRate: Int,
) {
    fun audioTargetFrame(delayMs: Double): Long =
        audioAnchorFrame + autoplayMillisecondsToFrames(delayMs.coerceAtLeast(0.0), sampleRate)
}

private fun createAutoPlayAudioTimeline(
    nowNanos: Long,
    currentAudioFrame: Long,
    sampleRate: Int,
    lookaheadMs: Double = AUTO_PLAY_AUDIO_LOOKAHEAD_MS,
): AutoPlayAudioTimeline {
    require(nowNanos >= 0L)
    require(currentAudioFrame >= 0L)
    require(sampleRate > 0)
    require(lookaheadMs >= 0.0)
    val lookaheadNanos = autoplayMillisecondsToNanos(lookaheadMs)
    return AutoPlayAudioTimeline(
        playbackStartNanos = nowNanos + lookaheadNanos,
        audioAnchorFrame = currentAudioFrame + autoplayMillisecondsToFrames(lookaheadMs, sampleRate),
        sampleRate = sampleRate,
    )
}

private data class AutoPlayAudioPumpStep(
    val readyUntilExclusive: Int,
    val nextDelayNanos: Long?,
)

private fun planAutoPlayAudioPump(
    targetFrames: List<Long>,
    nextIndex: Int,
    currentAudioFrame: Long,
    sampleRate: Int,
    lookaheadFrames: Long,
    maximumSleepNanos: Long = AUTO_PLAY_AUDIO_PUMP_MAX_SLEEP_NANOS,
): AutoPlayAudioPumpStep {
    require(nextIndex in 0..targetFrames.size)
    require(currentAudioFrame >= 0L)
    require(sampleRate > 0)
    require(lookaheadFrames >= 0L)
    require(maximumSleepNanos > 0L)

    val horizonFrame = currentAudioFrame + lookaheadFrames
    var readyUntil = nextIndex
    while (readyUntil < targetFrames.size && targetFrames[readyUntil] <= horizonFrame) {
        readyUntil++
    }
    if (readyUntil >= targetFrames.size) {
        return AutoPlayAudioPumpStep(readyUntilExclusive = readyUntil, nextDelayNanos = null)
    }

    val framesUntilHorizon = (targetFrames[readyUntil] - horizonFrame).coerceAtLeast(0L)
    val delayNanos = (framesUntilHorizon.toDouble() * 1_000_000_000.0 / sampleRate)
        .toLong()
        .coerceIn(0L, maximumSleepNanos)
    return AutoPlayAudioPumpStep(
        readyUntilExclusive = readyUntil,
        nextDelayNanos = delayNanos,
    )
}

private fun autoplayMillisecondsToNanos(milliseconds: Double): Long =
    (milliseconds * 1_000_000.0).roundToLong()

private fun autoplayMillisecondsToFrames(milliseconds: Double, sampleRate: Int): Long =
    (milliseconds * sampleRate / 1_000.0).roundToLong()

object AutoPlayRepository {
    private data class ScheduledAudioActions(
        val targetFrame: Long,
        val actions: List<AutoPlayData.Action>,
    )

    private val _state = MutableStateFlow(AutoPlayState.STOPPED)
    val state: StateFlow<AutoPlayState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    var totalDuration: Double = 0.0
        private set

    @Volatile private var playbackStartNanos: Long = 0L
    @Volatile private var playbackOffset: Double = 0.0
    @Volatile private var pendingAudioStopTicket: AudioStopTicket? = null
    private val playbackGeneration = atomic(0L)

    private var learningIndex = 0
    private var sortedActionTimes = listOf<Double>()
    private var previousLearningActions: List<AutoPlayData.Action> = emptyList()

    private val repoScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var progressJob: Job? = null

    private fun millisecondsToNanos(milliseconds: Double): Long =
        autoplayMillisecondsToNanos(milliseconds)

    private fun originFor(action: AutoPlayData.Action): Any =
        resolveLaunchpadOrigin(
            origin = null,
            x = action.x,
            y = action.y,
            launchpadId = action.launchpadId,
        ) ?: this

    private fun midiSignals(actions: List<AutoPlayData.Action>): List<Signal.Midi> =
        actions.map { action ->
            Signal.Midi(
                origin = originFor(action),
                x = action.x,
                y = action.y,
                velocity = if (action.down) 127 else 0,
            )
        }

    private fun currentPlaybackPosition(): Double =
        playbackOffset + (
            (Heaven.timeNanos - playbackStartNanos).coerceAtLeast(0L) /
                1_000_000.0
            )

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = repoScope.launch {
            while (isActive) {
                val currentPos = currentPlaybackPosition()
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

        val samplingChain = WorkspaceRepository.samplingChain
        val audioAvailable = Echo.outputStatus.value.available
        val pendingStop = pendingAudioStopTicket
        if (audioAvailable && pendingStop != null && !pendingStop.isComplete) {
            // StopAll is consumed by the audio callback and clears queued sample commands.
            // Wait for that callback before prefetching the first exact-frame trigger.
            val waitingGeneration = playbackGeneration.value
            Heaven.cancelJobsForOwner(this)
            Heaven.schedule(AUTO_PLAY_RESET_POLL_MS, this, identifier = "autoplay-audio-reset") {
                if (playbackGeneration.value == waitingGeneration) startAutoPlay()
            }
            return
        }
        // Without a running output callback there is no audio timeline to prefetch.
        // Continue visual AutoPlay immediately; an unconfigured renderer already
        // returns a completed ticket, while a stalled configured renderer must not
        // receive commands behind its pending StopAll.
        pendingAudioStopTicket = null

        val fromLearning = _state.value == AutoPlayState.LEARNING
        if (fromLearning) {
            playbackOffset = sortedActionTimes.getOrNull(learningIndex) ?: 0.0
            
            // Clear green lights manually
            val clearSignals = previousLearningActions.map {
                Signal.LED(origin = originFor(it), x = it.x, y = it.y, color = Color.Black, layer = 101)
            }
            Heaven.midiEnter(clearSignals)
        }

        Heaven.cancelJobsForOwner(this)
        
        if (_state.value != AutoPlayState.PAUSED && !fromLearning) {
            playbackOffset = 0.0
        }
        
        // Always ensure totalDuration is up to date
        totalDuration = autoplay.actions.keys.maxOrNull() ?: 0.0
        val scheduledActions = autoplay.actions.entries
            .asSequence()
            .map { entry -> entry.key - playbackOffset to entry.value }
            .filter { (adjustedDelay, _) -> adjustedDelay >= -0.001 }
            .sortedBy { (adjustedDelay, _) -> adjustedDelay }
            .toList()
        val runGeneration = playbackGeneration.incrementAndGet()
        val audioTimeline = if (audioAvailable) {
            createAutoPlayAudioTimeline(
                nowNanos = Heaven.timeNanos,
                currentAudioFrame = samplingChain.currentAudioFrame,
                sampleRate = samplingChain.audioSampleRate,
            )
        } else {
            null
        }
        _state.value = AutoPlayState.PLAYING
        playbackStartNanos = audioTimeline?.playbackStartNanos ?: Heaven.timeNanos
        startProgressTracking()

        if (audioTimeline != null) {
            val audioActions = scheduledActions.map { (adjustedDelay, actions) ->
                ScheduledAudioActions(
                    targetFrame = audioTimeline.audioTargetFrame(adjustedDelay),
                    actions = actions,
                )
            }
            val audioTargetFrames = audioActions.map(ScheduledAudioActions::targetFrame)
            // Start the pump before installing thousands of visual jobs. Its targets all
            // share the immutable anchor above, while each wake-up follows the live audio
            // clock so long-run hardware drift cannot consume the dispatch lookahead.
            pumpAudioActions(
                samplingChain = samplingChain,
                audioActions = audioActions,
                targetFrames = audioTargetFrames,
                nextIndex = 0,
                sampleRate = audioTimeline.sampleRate,
                lookaheadFrames = autoplayMillisecondsToFrames(
                    AUTO_PLAY_AUDIO_LOOKAHEAD_MS,
                    audioTimeline.sampleRate,
                ),
                runGeneration = runGeneration,
            )
        }

        scheduledActions.forEach { (adjustedDelay, actions) ->
            val deadlineNanos = playbackStartNanos +
                millisecondsToNanos(adjustedDelay.coerceAtLeast(0.0))

            // Visual feedback remains on the original AutoPlay wall-clock deadline.
            Heaven.scheduleAt(deadlineNanos, this) {
                if (audioTimeline == null) {
                    // Sampling MIDI also contains page and macro controls required by
                    // a lights-only run. Preserve legacy wall-deadline routing when no
                    // audio callback is available, without prefetching sample commands.
                    samplingChain.signalEnter(midiSignals(actions))
                }
                if (settings?.autoPlayShowLights == true) {
                    WorkspaceRepository.lightsChain.signalEnter(
                        actions.map {
                            Signal.LED(
                                origin = originFor(it),
                                x = it.x,
                                y = it.y,
                                color = if (it.down) Color.White else Color.Black,
                            )
                        }
                    )
                }

                if (settings?.autoPlayShowButtonPresses == true) {
                    Heaven.midiEnter(
                        actions.map {
                            Signal.LED(
                                origin = originFor(it),
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

        // Schedule this after all actions so equal deadlines retain action-before-stop ordering.
        val remainingDuration = (totalDuration - playbackOffset).coerceAtLeast(0.0)
        Heaven.scheduleAt(
            targetTimeNanos = playbackStartNanos + millisecondsToNanos(remainingDuration + 50.0),
            owner = this,
        ) {
            stopAutoPlay()
        }
    }

    private fun pumpAudioActions(
        samplingChain: AudioChain,
        audioActions: List<ScheduledAudioActions>,
        targetFrames: List<Long>,
        nextIndex: Int,
        sampleRate: Int,
        lookaheadFrames: Long,
        runGeneration: Long,
    ) {
        if (
            nextIndex >= audioActions.size ||
            _state.value != AutoPlayState.PLAYING ||
            playbackGeneration.value != runGeneration
        ) return
        val step = planAutoPlayAudioPump(
            targetFrames = targetFrames,
            nextIndex = nextIndex,
            currentAudioFrame = samplingChain.currentAudioFrame,
            sampleRate = sampleRate,
            lookaheadFrames = lookaheadFrames,
        )
        var index = nextIndex
        while (index < step.readyUntilExclusive) {
            if (playbackGeneration.value != runGeneration) return
            val action = audioActions[index]
            samplingChain.signalEnterAtFrame(midiSignals(action.actions), action.targetFrame)
            index++
        }
        val delayNanos = step.nextDelayNanos ?: return
        Heaven.scheduleAt(
            targetTimeNanos = Heaven.timeNanos + delayNanos,
            owner = this,
            identifier = AUTO_PLAY_AUDIO_PUMP_ID,
        ) {
            if (playbackGeneration.value != runGeneration) return@scheduleAt
            pumpAudioActions(
                samplingChain = samplingChain,
                audioActions = audioActions,
                targetFrames = targetFrames,
                nextIndex = index,
                sampleRate = sampleRate,
                lookaheadFrames = lookaheadFrames,
                runGeneration = runGeneration,
            )
        }
    }

    fun pauseAutoPlay() {
        if (_state.value != AutoPlayState.PLAYING) return
        
        // Calculate how far into the playback we are
        playbackOffset = currentPlaybackPosition()
        playbackGeneration.incrementAndGet()
        Heaven.cancelJobsForOwner(this)
        pendingAudioStopTicket = Echo.stopAll()
        progressJob?.cancel()
        progressJob = null
        _state.value = AutoPlayState.PAUSED
    }

    fun stopAutoPlay() {
        playbackGeneration.incrementAndGet()
        Heaven.cancelJobsForOwner(this)
        (WorkspaceRepository.lightsChain.devicesDepthFirst() +
            WorkspaceRepository.samplingChain.devicesDepthFirst())
            .filterIsInstance<Chokeable>()
            .forEach(Chokeable::onChoke)
        Heaven.clear()
        pendingAudioStopTicket = Echo.stopAll()
        progressJob?.cancel()
        progressJob = null
        _progress.value = 0f
        _state.value = AutoPlayState.STOPPED
        playbackOffset = 0.0
        playbackStartNanos = 0L
        learningIndex = 0
        sortedActionTimes = emptyList()
        totalDuration = 0.0
        if (previousLearningActions.isNotEmpty()) {
            val clearSignals = previousLearningActions.map {
                Signal.LED(origin = originFor(it), x = it.x, y = it.y, color = Color.Black, layer = 101)
            }
            Heaven.midiEnter(clearSignals)
        }
        previousLearningActions = emptyList()
    }

    fun startLearningMode() {
        if (_state.value == AutoPlayState.LEARNING) return
        
        val autoplay = WorkspaceRepository.workspaceMeta?.autoPlay ?: return
        
        val currentPos = if (_state.value == AutoPlayState.PLAYING) {
            currentPlaybackPosition()
        } else {
            playbackOffset
        }

        playbackGeneration.incrementAndGet()
        Heaven.cancelJobsForOwner(this)
        progressJob?.cancel()
        progressJob = null
        Heaven.clear()
        pendingAudioStopTicket = Echo.stopAll()
        
        _state.value = AutoPlayState.LEARNING
        sortedActionTimes = autoplay.actions.filter { (_, actions) ->
            actions.any { it.down }
        }.keys.sorted()
        
        // Find the next step to learn based on current position
        learningIndex = sortedActionTimes.indexOfFirst { it >= currentPos }.let {
            if (it == -1) (sortedActionTimes.size - 1).coerceAtLeast(0) else it
        }

        totalDuration = autoplay.actions.keys.maxOrNull() ?: 0.0
        _progress.value = if (sortedActionTimes.isNotEmpty()) {
            (learningIndex.toFloat() / sortedActionTimes.size).coerceIn(0f, 1f)
        } else 0f
        
        Heaven.clear {
            if (_state.value == AutoPlayState.LEARNING) {
                showCurrentLearningStep()
            }
        }
    }

    private fun executeActions(actions: List<AutoPlayData.Action>) {
        val downActions = actions.filter { it.down }
        if (downActions.isEmpty()) return

        WorkspaceRepository.samplingChain.signalEnter(
            downActions.map {
                Signal.Midi(
                    origin = originFor(it),
                    x = it.x,
                    y = it.y,
                    velocity = 127,
                )
            }
        )
        
        val settings = WorkspaceRepository.workspaceMeta?.settings
        if (settings?.autoPlayShowLights == true) {
            WorkspaceRepository.lightsChain.signalEnter(
                downActions.map {
                    Signal.LED(
                        origin = originFor(it),
                        x = it.x,
                        y = it.y,
                        color = Color.White,
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
            Signal.LED(origin = originFor(it), x = it.x, y = it.y, color = Color.Black, layer = 101)
        }
        
        val showSignals = expectedDown.map {
            Signal.LED(
                origin = originFor(it),
                x = it.x,
                y = it.y,
                color = Color.Green,
                layer = 101
            )
        }

        Heaven.midiEnter(clearSignals + showSignals)
        previousLearningActions = expectedDown
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
                it.down && (it.x to it.y) !in inputCoords
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

    fun seekTo(fraction: Float) {
        val autoplay = WorkspaceRepository.workspaceMeta?.autoPlay ?: return
        if (autoplay.actions.isEmpty()) return

        totalDuration = autoplay.actions.keys.maxOrNull() ?: 0.0
        if (totalDuration <= 0) return

        val targetMs = (fraction.coerceIn(0f, 1f) * totalDuration)
        playbackOffset = targetMs
        _progress.value = fraction.coerceIn(0f, 1f)
        playbackGeneration.incrementAndGet()
        pendingAudioStopTicket = Echo.stopAll()

        val currentState = _state.value
        if (currentState == AutoPlayState.PLAYING) {
            _state.value = AutoPlayState.PAUSED
            startAutoPlay()
        } else if (currentState == AutoPlayState.LEARNING) {
            learningIndex = sortedActionTimes.indexOfFirst { it >= targetMs }.let {
                if (it == -1) (sortedActionTimes.size - 1).coerceAtLeast(0) else it
            }
            showCurrentLearningStep()
        }
    }
}
