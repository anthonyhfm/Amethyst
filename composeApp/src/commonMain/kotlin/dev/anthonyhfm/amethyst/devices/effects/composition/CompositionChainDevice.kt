package dev.anthonyhfm.amethyst.devices.effects.composition

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Diamond
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import androidx.compose.runtime.snapshotFlow
import com.composeunstyled.Icon
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.devices.TimelineTriggerable
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionGraph
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.GraphProcessor
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.defaultCompositionGraph
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.hasOriginBinding
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ViewportRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class CompositionChainDevice : LEDChainDevice<CompositionChainDeviceState>(), Chokeable, TimelineTriggerable {
    override val state = MutableStateFlow(CompositionChainDeviceState())
    override val helpRef = "Composition"

    private val customMode = CompositionWorkspaceMode(this)
    private var activeFrame: ActiveFrame? = null
    private var playbackRun: PlaybackRun? = null
    private var playbackOrigin: Any? = this
    private val playing = mutableStateOf(false)
    private val playbackProgress = mutableStateOf(0f)
    private var workspacePreviewActive = false
    private val stateObserverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Polyphonic chain playback
    private val chainVoicesLock = SynchronizedObject()
    private var nextChainVoiceId = 0L
    private val activeChainVoices = mutableMapOf<Long, ChainVoice>()

    init {
        renderAnimation()

        // Re-render the animation whenever workspace bounds or device placement changes
        stateObserverScope.launch {
            snapshotFlow {
                val currentBounds = WorkspaceRepository.bounds
                val devices = ViewportRepository.devices.value.map { it.selectionUUID to it.position.value }
                currentBounds to devices
            }.drop(1).collect {
                renderAnimation()
            }
        }

        // Re-render whenever BPM changes
        stateObserverScope.launch {
            WorkspaceRepository.bpm.drop(1).collect {
                renderAnimation()
            }
        }
    }

    override fun timelineDuration(context: TimelineDurationContext): TimelineDuration {
        val options = state.value.playbackOptions
        if (options.repeat) return TimelineDuration.Unbounded
        val duration = options.timing.toMsValue(context.bpm.toDouble()) * options.gate.coerceIn(0.05f, 4f)
        return TimelineDuration.Finite(duration.toLong().coerceAtLeast(0L))
    }

    override fun startTimelineTrigger() {
        if (workspacePreviewActive) {
            play()
        } else {
            startChainVoice(origin = this, triggerOrigin = null)
        }
    }

    override fun stopTimelineTrigger() {
        if (workspacePreviewActive) {
            pause()
        } else {
            stopAllChainVoices()
        }
    }

    override fun onChoke() {
        stopAllChainVoices()
    }

    override fun onStateRestored() {
        super.onStateRestored()
        renderAnimation()
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        if (workspacePreviewActive) return

        val activeSignals = n.filter { it.color != Color.Black }
        if (activeSignals.isEmpty()) return

        activeSignals.forEach { trigger ->
            startChainVoice(
                origin = trigger.origin,
                triggerOrigin = triggerOriginFor(trigger.x, trigger.y),
            )
        }
    }

    private fun triggerOriginFor(x: Int, y: Int): Vec2 {
        val bounds = GraphProcessor.resolveBounds()
        val width = (bounds.second.width - 1).coerceAtLeast(1)
        val height = (bounds.second.height - 1).coerceAtLeast(1)
        return Vec2(
            x = ((x - bounds.first.x).toFloat() / width).coerceIn(0f, 1f),
            y = ((y - bounds.first.y).toFloat() / height).coerceIn(0f, 1f),
        )
    }

    // ==========================================
    // Workspace / Editor Transport & Playback
    // ==========================================

    fun play() {
        val startProgress = playbackProgress.value.takeUnless { it >= 1f } ?: 0f
        startEditorPlayback(
            origin = playbackOrigin,
            progress = startProgress,
            repeat = state.value.playbackOptions.repeat,
            livePreview = true,
        )
    }

    fun pause() {
        playbackRun = null
        playing.value = false
        Heaven.cancelJobsForOwner(this, PLAYBACK_IDENTIFIER)
        clearActiveFrame()
    }

    fun isPlaying(): Boolean = playing.value

    fun playbackProgress(): Float = playbackProgress.value

    fun playbackDurationMs(): Long = state.value.playbackOptions.durationMs().toLong()

    /** Starts the editor-only preview from the beginning for a captured pad press. */
    fun triggerWorkspacePreview(x: Int? = null, y: Int? = null) {
        if (!workspacePreviewActive) return
        startEditorPlayback(
            origin = this,
            repeat = state.value.playbackOptions.repeat,
            livePreview = true,
            triggerOrigin = if (x != null && y != null) triggerOriginFor(x, y) else null,
        )
    }

    /**
     * Starts an editor-only preview session. Frames are written to devices via Heaven
     * directly and never leave this device through the lights chain.
     */
    fun startWorkspacePreview() {
        workspacePreviewActive = true
        playbackRun = null
        playing.value = false
        Heaven.cancelJobsForOwner(this, PLAYBACK_IDENTIFIER)
        activeFrame = null
        stopAllChainVoices()
        Heaven.clear()
    }

    /** Stops the editor-only preview and leaves every device black. */
    fun stopWorkspacePreview() {
        playbackRun = null
        playing.value = false
        Heaven.cancelJobsForOwner(this, PLAYBACK_IDENTIFIER)
        clearActiveFrame()
        Heaven.clear()
        activeFrame = null
        stopAllChainVoices()
        workspacePreviewActive = false
    }

    fun seekTo(progress: Float) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        playbackProgress.value = clampedProgress
        val run = playbackRun
        if (run?.livePreview != false) {
            renderLivePlaybackFrame(
                progress = clampedProgress,
                origin = run?.origin ?: playbackOrigin,
                triggerOrigin = run?.triggerOrigin,
            )
        } else {
            renderPlaybackFrame(progress = clampedProgress, origin = run.origin)
        }

        if (playing.value) {
            Heaven.cancelJobsForOwner(this, PLAYBACK_IDENTIFIER)
            val activeRun = playbackRun ?: return
            scheduleEditorPlaybackFrame(activeRun.firstFrameAtOrAfter(clampedProgress))
        }
    }

    fun updatePlaybackOptions(transform: (CompositionPlaybackOptions) -> CompositionPlaybackOptions) {
        val before = state.value
        val after = before.copy(playbackOptions = transform(before.playbackOptions), renderedAnimation = emptyList())
        if (before == after) return
        state.value = after
        renderAnimation()
        pushStateChange(before, after)
    }

    fun updateSplitRatio(ratio: Float) {
        val clamped = ratio.coerceIn(MIN_SPLIT_RATIO, MAX_SPLIT_RATIO)
        val before = state.value
        if (before.splitRatio == clamped) return
        state.value = before.copy(splitRatio = clamped)
    }

    private fun startEditorPlayback(
        origin: Any?,
        progress: Float = 0f,
        repeat: Boolean,
        livePreview: Boolean = false,
        triggerOrigin: Vec2? = null,
    ) {
        Heaven.cancelJobsForOwner(this, PLAYBACK_IDENTIFIER)
        val effectiveLivePreview = livePreview || state.value.graph.hasOriginBinding()
        if (!effectiveLivePreview && state.value.renderedAnimation.isEmpty()) renderAnimation()
        playbackOrigin = origin
        val run = PlaybackRun(
            origin = origin,
            frames = if (effectiveLivePreview) buildLivePreviewFrames() else state.value.renderedAnimation,
            repeat = repeat,
            livePreview = effectiveLivePreview,
            triggerOrigin = if (effectiveLivePreview) triggerOrigin else null,
        )
        playbackRun = run
        playing.value = true
        scheduleEditorPlaybackFrame(run.firstFrameAtOrAfter(progress.coerceIn(0f, 1f)))
    }

    private fun scheduleEditorPlaybackFrame(frameIndex: Int, delayMs: Double = 0.0) {
        Heaven.schedule(delayInMs = delayMs, owner = this, identifier = PLAYBACK_IDENTIFIER) {
            val run = playbackRun ?: return@schedule
            val options = state.value.playbackOptions
            val durationMs = options.durationMs().coerceAtLeast(FRAME_INTERVAL_MS)
            val frame = run.frames.getOrElse(frameIndex) { run.frames.last() }
            val progress = frame.progress
            playbackProgress.value = progress
            if (run.livePreview) {
                renderLivePlaybackFrame(progress = progress, origin = run.origin, triggerOrigin = run.triggerOrigin)
            } else {
                emitFrame(frame.signals.map { it.copy(origin = run.origin) })
            }

            when {
                frameIndex < run.frames.lastIndex -> {
                    val nextProgress = run.frames[frameIndex + 1].progress
                    scheduleEditorPlaybackFrame(
                        frameIndex = frameIndex + 1,
                        delayMs = ((nextProgress - progress) * durationMs).coerceAtLeast(0.0),
                    )
                }
                run.repeat -> {
                    Heaven.schedule(
                        delayInMs = FRAME_INTERVAL_MS,
                        owner = this,
                        identifier = PLAYBACK_IDENTIFIER,
                    ) {
                        if (playbackRun !== run) return@schedule
                        playbackProgress.value = 0f
                        playbackRun = run.copy(
                            frames = if (run.livePreview) buildLivePreviewFrames() else state.value.renderedAnimation,
                        )
                        scheduleEditorPlaybackFrame(0)
                    }
                }
                else -> {
                    Heaven.schedule(
                        delayInMs = FRAME_INTERVAL_MS,
                        owner = this,
                        identifier = PLAYBACK_IDENTIFIER,
                    ) {
                        if (playbackRun === run) finishEditorPlayback()
                    }
                }
            }
        }
    }

    private fun finishEditorPlayback() {
        playbackRun = null
        playing.value = false
        Heaven.cancelJobsForOwner(this, PLAYBACK_IDENTIFIER)
        clearActiveFrame()
    }

    private fun renderPlaybackFrame(progress: Float, origin: Any?) {
        val frame = state.value.renderedAnimation
            .firstOrNull { it.progress >= progress }
            ?: state.value.renderedAnimation.lastOrNull()
        if (frame != null) {
            emitFrame(frame.signals.map { it.copy(origin = origin) })
        }
    }

    private fun renderLivePlaybackFrame(progress: Float, origin: Any?, triggerOrigin: Vec2? = null) {
        emitFrame(
            GraphProcessor.renderFrame(
                graph = state.value.graph,
                progress = progress,
                outputOrigin = origin,
                triggerOrigin = triggerOrigin,
            )
        )
    }

    private fun emitFrame(signals: List<Signal.LED>) {
        val previousFrame = activeFrame
        val previous = previousFrame?.coordinates.orEmpty()
        val current = signals.map { it.x to it.y }.toSet()
        val outputOrigin = signals.firstOrNull()?.origin ?: previousFrame?.origin ?: this
        val offSignals = previous
            .filterNot { it in current }
            .map { (x, y) -> Signal.LED(origin = outputOrigin, x = x, y = y, color = Color.Black) }

        val allSignals = offSignals + signals
        activeFrame = ActiveFrame(coordinates = current, origin = outputOrigin)

        if (workspacePreviewActive) {
            Heaven.midiEnter(allSignals)
        } else {
            signalExit?.invoke(allSignals)
        }
    }

    private fun clearActiveFrame() {
        val previousFrame = activeFrame ?: return
        activeFrame = null
        if (previousFrame.coordinates.isEmpty()) return

        val offSignals = previousFrame.coordinates.map { (x, y) ->
            Signal.LED(origin = previousFrame.origin, x = x, y = y, color = Color.Black)
        }

        if (workspacePreviewActive) {
            Heaven.midiEnter(offSignals)
        } else {
            signalExit?.invoke(offSignals)
        }
    }

    // ==========================================
    // Polyphonic Chain Playback
    // ==========================================

    private fun startChainVoice(origin: Any?, triggerOrigin: Vec2?): Long {
        val effectiveLivePreview = state.value.graph.hasOriginBinding()
        if (!effectiveLivePreview && state.value.renderedAnimation.isEmpty()) {
            renderAnimation()
        }

        val (voiceId, voice) = synchronized(chainVoicesLock) {
            val id = ++nextChainVoiceId
            val v = ChainVoice(
                id = id,
                origin = origin,
                frames = if (effectiveLivePreview) buildLivePreviewFrames() else state.value.renderedAnimation,
                livePreview = effectiveLivePreview,
                triggerOrigin = if (effectiveLivePreview) triggerOrigin else null,
            )
            activeChainVoices[id] = v
            Pair(id, v)
        }

        scheduleChainVoiceFrame(voiceId, 0, delayMs = 0.0)
        return voiceId
    }

    private fun scheduleChainVoiceFrame(voiceId: Long, frameIndex: Int, delayMs: Double = 0.0) {
        Heaven.schedule(delayInMs = delayMs, owner = this, identifier = voiceId) {
            val voice = synchronized(chainVoicesLock) { activeChainVoices[voiceId] } ?: return@schedule
            val options = state.value.playbackOptions
            val durationMs = options.durationMs().coerceAtLeast(FRAME_INTERVAL_MS)
            val frame = voice.frames.getOrElse(frameIndex) { voice.frames.last() }
            val progress = frame.progress

            val signals = if (voice.livePreview) {
                GraphProcessor.renderFrame(
                    graph = state.value.graph,
                    progress = progress,
                    outputOrigin = voice.origin,
                    triggerOrigin = voice.triggerOrigin,
                )
            } else {
                frame.signals.map { it.copy(origin = voice.origin) }
            }

            emitChainVoiceFrame(voiceId, signals)

            if (frameIndex < voice.frames.lastIndex) {
                val nextProgress = voice.frames[frameIndex + 1].progress
                scheduleChainVoiceFrame(
                    voiceId = voiceId,
                    frameIndex = frameIndex + 1,
                    delayMs = ((nextProgress - progress) * durationMs).coerceAtLeast(0.0),
                )
            } else {
                Heaven.schedule(
                    delayInMs = FRAME_INTERVAL_MS,
                    owner = this,
                    identifier = voiceId,
                ) {
                    finishChainVoice(voiceId)
                }
            }
        }
    }

    private fun emitChainVoiceFrame(voiceId: Long, signals: List<Signal.LED>) {
        val (previousCoords, outputOrigin) = synchronized(chainVoicesLock) {
            val voice = activeChainVoices[voiceId] ?: return@synchronized null
            val prev = voice.activeCoordinates
            val current = signals.map { it.x to it.y }.toSet()
            voice.activeCoordinates = current
            val origin = signals.firstOrNull()?.origin ?: voice.origin ?: this
            Pair(prev, origin)
        } ?: return

        val currentCoords = signals.map { it.x to it.y }.toSet()
        val offSignals = previousCoords
            .filterNot { it in currentCoords }
            .map { (x, y) -> Signal.LED(origin = outputOrigin, x = x, y = y, color = Color.Black) }

        val allSignals = offSignals + signals
        signalExit?.invoke(allSignals)
    }

    private fun finishChainVoice(voiceId: Long) {
        val voice = synchronized(chainVoicesLock) {
            activeChainVoices.remove(voiceId)
        } ?: return

        Heaven.cancelJobsForOwner(this, voiceId)

        if (voice.activeCoordinates.isNotEmpty()) {
            val outputOrigin = voice.origin ?: this
            val offSignals = voice.activeCoordinates.map { (x, y) ->
                Signal.LED(origin = outputOrigin, x = x, y = y, color = Color.Black)
            }
            signalExit?.invoke(offSignals)
        }
    }

    private fun stopAllChainVoices() {
        val voicesToClear = synchronized(chainVoicesLock) {
            val list = activeChainVoices.values.toList()
            activeChainVoices.clear()
            list
        }

        val allOffSignals = mutableListOf<Signal.LED>()
        voicesToClear.forEach { voice ->
            Heaven.cancelJobsForOwner(this, voice.id)
            if (voice.activeCoordinates.isNotEmpty()) {
                val outputOrigin = voice.origin ?: this
                voice.activeCoordinates.forEach { (x, y) ->
                    allOffSignals.add(Signal.LED(origin = outputOrigin, x = x, y = y, color = Color.Black))
                }
            }
        }

        if (allOffSignals.isNotEmpty()) {
            signalExit?.invoke(allOffSignals)
        }
    }

    internal fun activeVoicesCount(): Int = synchronized(chainVoicesLock) { activeChainVoices.size }

    // ==========================================
    // Common helpers & Graph mutations
    // ==========================================

    private fun CompositionPlaybackOptions.durationMs(): Double {
        val base = timing.toMsValue(WorkspaceRepository.bpm.value).toDouble()
        return base * gate.coerceIn(0.05f, 4f).toDouble()
    }

    private fun buildLivePreviewFrames(): List<RenderedCompositionFrame> {
        val durationMs = playbackDurationMs().coerceAtLeast(1L)
        val frameCount = kotlin.math.ceil(durationMs / (1_000.0 / RENDER_FPS)).toInt().coerceAtLeast(1)
        return (0..frameCount).map { index ->
            RenderedCompositionFrame(progress = index.toFloat() / frameCount, signals = emptyList())
        }
    }

    /** Renders the graph once into a transient, chain-playback cache at a fixed 120 FPS. */
    fun renderAnimation() {
        val durationMs = playbackDurationMs().coerceAtLeast(1L)
        val intervalMs = 1_000.0 / RENDER_FPS
        val frameCount = kotlin.math.ceil(durationMs / intervalMs).toInt().coerceAtLeast(1)
        val graph = state.value.graph
        val bounds = GraphProcessor.resolveBounds()
        val rendered = (0..frameCount).map { index ->
            val progress = index.toFloat() / frameCount
            RenderedCompositionFrame(
                progress = progress,
                signals = GraphProcessor.renderFrame(
                    graph = graph,
                    progress = progress,
                    outputOrigin = this,
                    bounds = bounds,
                ),
            )
        }
        state.value = state.value.copy(renderedAnimation = rendered)
    }

    fun updateGraph(undoable: Boolean = true, transform: (CompositionGraph) -> CompositionGraph): Boolean {
        val before = state.value
        val after = before.copy(graph = transform(before.graph), renderedAnimation = emptyList())
        if (before == after) return false
        state.value = after
        if (undoable) {
            pushStateChange(before, after)
        }
        return true
    }

    /** Commits a graph which was updated live during a drag as one history entry. */
    fun commitGraphEdit(beforeGraph: CompositionGraph) {
        val after = state.value
        val before = after.copy(graph = beforeGraph)
        if (before != after) pushStateChange(before, after)
    }

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Composition",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(120.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Button(
                onClick = {
                    WorkspaceRepository.switchMode(mode = customMode)
                },
                variant = ButtonVariant.Default,
                size = ButtonSize.IconLarge,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Diamond,
                    contentDescription = "Open Composition Workspace",
                    modifier = Modifier.size(36.dp),
                    tint = Theme[colors][primaryForeground],
                )
            }
        }
    }

    private data class ActiveFrame(
        val coordinates: Set<Pair<Int, Int>>,
        val origin: Any?,
    )

    private data class PlaybackRun(
        val origin: Any?,
        val frames: List<RenderedCompositionFrame>,
        val repeat: Boolean,
        val livePreview: Boolean,
        val triggerOrigin: Vec2? = null,
    ) {
        fun firstFrameAtOrAfter(progress: Float): Int =
            frames.indexOfFirst { it.progress >= progress }.takeIf { it >= 0 } ?: frames.lastIndex
    }

    private data class ChainVoice(
        val id: Long,
        val origin: Any?,
        val frames: List<RenderedCompositionFrame>,
        val livePreview: Boolean,
        val triggerOrigin: Vec2? = null,
        var activeCoordinates: Set<Pair<Int, Int>> = emptySet(),
    )

    companion object : ChainDeviceFactory<CompositionChainDeviceState> {
        private const val PLAYBACK_IDENTIFIER = "composition-playback"
        private const val FRAME_INTERVAL_MS = 16.0
        private const val RENDER_FPS = 120
        const val MIN_SPLIT_RATIO = 0.25f
        const val MAX_SPLIT_RATIO = 0.75f

        override val stateClass = CompositionChainDeviceState::class
        override val serializer = CompositionChainDeviceState.serializer()
        override fun create() = CompositionChainDevice()

        override fun unpack(state: CompositionChainDeviceState): CompositionChainDevice =
            create().apply {
                this.state.value = state
                renderAnimation()
            }
    }
}

@Serializable
data class CompositionChainDeviceState(
    val graph: CompositionGraph = defaultCompositionGraph(),
    val playbackOptions: CompositionPlaybackOptions = CompositionPlaybackOptions(),
    val splitRatio: Float = 0.5f,
    @kotlinx.serialization.Transient
    val renderedAnimation: List<RenderedCompositionFrame> = emptyList(),
) : DeviceState()

data class RenderedCompositionFrame(
    val progress: Float,
    val signals: List<Signal.LED>,
)

@Serializable
data class CompositionPlaybackOptions(
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val repeat: Boolean = false,
    val gate: Float = 0.5f,
)
