package dev.anthonyhfm.amethyst.devices.effects.composition

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Diamond
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import androidx.compose.runtime.snapshotFlow
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
    private val lock = SynchronizedObject()
    private var nextVoiceId = 0L
    private val activeVoices = mutableMapOf<Long, PlaybackVoice>()
    private var editorPreviewVoiceId: Long? = null
    private var scrubCoordinates: Set<Pair<Int, Int>> = emptySet()
    private var playbackOrigin: Any? = this
    private val playing = mutableStateOf(false)
    private val playbackProgress = mutableStateOf(0f)
    private var workspacePreviewActive = false
    private val stateObserverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
        startPlayback(origin = this, repeat = state.value.playbackOptions.repeat)
    }

    override fun stopTimelineTrigger() = stopAllVoices()

    override fun onChoke() {
        stopAllVoices()
    }

    override fun onStateRestored() {
        super.onStateRestored()
        renderAnimation()
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        if (workspacePreviewActive) return

        val activeSignals = n.filter { it.color != Color.Black }

        activeSignals.forEach { trigger ->
            startPlayback(
                origin = trigger.origin,
                repeat = false,
                triggerOrigin = triggerOriginFor(trigger.x, trigger.y),
                isEditorPreview = false,
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

    fun play() {
        val startProgress = playbackProgress.value.takeUnless { it >= 1f } ?: 0f
        synchronized(lock) {
            editorPreviewVoiceId?.let { finishVoiceLocked(it) }
        }
        startPlayback(
            origin = playbackOrigin,
            progress = startProgress,
            repeat = state.value.playbackOptions.repeat,
            livePreview = true,
            isEditorPreview = true,
        )
    }

    fun pause() {
        synchronized(lock) {
            editorPreviewVoiceId?.let { finishVoiceLocked(it) }
            editorPreviewVoiceId = null
        }
        playing.value = false
    }

    fun isPlaying(): Boolean = playing.value

    fun playbackProgress(): Float = playbackProgress.value

    fun playbackDurationMs(): Long = state.value.playbackOptions.durationMs().toLong()

    internal fun activeVoicesCount(): Int = synchronized(lock) { activeVoices.size }

    /** Starts the editor-only preview from the beginning for a captured pad press. */
    fun triggerWorkspacePreview(x: Int? = null, y: Int? = null) {
        if (!workspacePreviewActive) return
        startPlayback(
            origin = this,
            repeat = state.value.playbackOptions.repeat,
            livePreview = true,
            triggerOrigin = if (x != null && y != null) triggerOriginFor(x, y) else null,
            isEditorPreview = false,
        )
    }

    /**
     * Starts an editor-only preview session. Frames are written to devices via Heaven
     * directly and never leave this device through the lights chain.
     */
    fun startWorkspacePreview() {
        workspacePreviewActive = true
        stopAllVoices()
        Heaven.clear()
    }

    /** Stops the editor-only preview and leaves every device black. */
    fun stopWorkspacePreview() {
        stopAllVoices()
        Heaven.clear()
        workspacePreviewActive = false
    }

    fun seekTo(progress: Float) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        playbackProgress.value = clampedProgress

        val previewSignals = if (state.value.graph.hasOriginBinding()) {
            GraphProcessor.renderFrame(
                graph = state.value.graph,
                progress = clampedProgress,
                outputOrigin = playbackOrigin,
                triggerOrigin = null,
            )
        } else {
            val frame = state.value.renderedAnimation
                .firstOrNull { it.progress >= clampedProgress }
                ?: state.value.renderedAnimation.lastOrNull()
            frame?.signals?.map { it.copy(origin = playbackOrigin) } ?: emptyList()
        }
        emitScrubFrame(previewSignals)

        if (playing.value) {
            val currentId = editorPreviewVoiceId
            if (currentId != null) {
                Heaven.cancelJobsForOwner(this, currentId)
                val voice = synchronized(lock) { activeVoices[currentId] }
                if (voice != null) {
                    schedulePlaybackFrame(currentId, voice.firstFrameAtOrAfter(clampedProgress))
                }
            }
        }
    }

    fun updatePlaybackOptions(transform: (CompositionPlaybackOptions) -> CompositionPlaybackOptions) {
        val before = state.value
        val after = before.copy(playbackOptions = transform(before.playbackOptions), renderedAnimation = emptyList())
        if (before == after) return
        state.value = after
        pushStateChange(before, after)
    }

    fun updateSplitRatio(ratio: Float) {
        val clamped = ratio.coerceIn(MIN_SPLIT_RATIO, MAX_SPLIT_RATIO)
        val before = state.value
        if (before.splitRatio == clamped) return
        state.value = before.copy(splitRatio = clamped)
    }

    private fun startPlayback(
        origin: Any?,
        progress: Float = 0f,
        repeat: Boolean,
        livePreview: Boolean = false,
        triggerOrigin: Vec2? = null,
        isEditorPreview: Boolean = false,
    ): Long {
        val effectiveLivePreview = livePreview || state.value.graph.hasOriginBinding()
        if (!effectiveLivePreview && state.value.renderedAnimation.isEmpty()) {
            renderAnimation()
        }

        val (voiceId, voice) = synchronized(lock) {
            val id = ++nextVoiceId
            val v = PlaybackVoice(
                id = id,
                origin = origin,
                frames = if (effectiveLivePreview) buildLivePreviewFrames() else state.value.renderedAnimation,
                repeat = repeat,
                livePreview = effectiveLivePreview,
                triggerOrigin = if (effectiveLivePreview) triggerOrigin else null,
                isEditorPreview = isEditorPreview,
            )
            activeVoices[id] = v
            if (isEditorPreview) {
                editorPreviewVoiceId = id
                playbackOrigin = origin
                playing.value = true
            }
            Pair(id, v)
        }

        schedulePlaybackFrame(voiceId, voice.firstFrameAtOrAfter(progress.coerceIn(0f, 1f)))
        return voiceId
    }

    private fun schedulePlaybackFrame(voiceId: Long, frameIndex: Int, delayMs: Double = 0.0) {
        Heaven.schedule(delayInMs = delayMs, owner = this, identifier = voiceId) {
            val voice = synchronized(lock) { activeVoices[voiceId] } ?: return@schedule
            val options = state.value.playbackOptions
            val durationMs = options.durationMs().coerceAtLeast(FRAME_INTERVAL_MS)
            val frame = voice.frames.getOrElse(frameIndex) { voice.frames.last() }
            val progress = frame.progress

            if (voice.isEditorPreview) {
                playbackProgress.value = progress
            }

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

            emitVoiceFrame(voiceId, signals)

            when {
                frameIndex < voice.frames.lastIndex -> {
                    val nextProgress = voice.frames[frameIndex + 1].progress
                    schedulePlaybackFrame(
                        voiceId = voiceId,
                        frameIndex = frameIndex + 1,
                        delayMs = ((nextProgress - progress) * durationMs).coerceAtLeast(0.0),
                    )
                }
                voice.repeat -> {
                    // Keep the terminal frame visible for one presentation interval before
                    // restarting; otherwise it is replaced by frame zero in the same tick.
                    Heaven.schedule(
                        delayInMs = FRAME_INTERVAL_MS,
                        owner = this,
                        identifier = voiceId,
                    ) {
                        val currentVoice = synchronized(lock) { activeVoices[voiceId] } ?: return@schedule
                        if (currentVoice.isEditorPreview) {
                            playbackProgress.value = 0f
                        }
                        val updatedVoice = currentVoice.copy(
                            frames = if (currentVoice.livePreview) buildLivePreviewFrames() else state.value.renderedAnimation,
                            activeCoordinates = currentVoice.activeCoordinates,
                        )
                        synchronized(lock) {
                            activeVoices[voiceId] = updatedVoice
                        }
                        schedulePlaybackFrame(voiceId = voiceId, frameIndex = 0, delayMs = 0.0)
                    }
                }
                else -> {
                    // Do not clear the terminal frame in the same callback that emitted it.
                    Heaven.schedule(
                        delayInMs = FRAME_INTERVAL_MS,
                        owner = this,
                        identifier = voiceId,
                    ) {
                        finishVoice(voiceId)
                    }
                }
            }
        }
    }

    private fun finishVoice(voiceId: Long) {
        val (voice, wasEditorPreview) = synchronized(lock) {
            val v = activeVoices.remove(voiceId) ?: return@synchronized null
            val wasEditor = (editorPreviewVoiceId == voiceId)
            if (wasEditor) {
                editorPreviewVoiceId = null
            }
            Pair(v, wasEditor)
        } ?: return

        Heaven.cancelJobsForOwner(this, voiceId)

        if (wasEditorPreview) {
            playing.value = false
        }

        if (voice.activeCoordinates.isNotEmpty()) {
            val outputOrigin = voice.origin ?: this
            val offSignals = voice.activeCoordinates.map { (x, y) ->
                Signal.LED(origin = outputOrigin, x = x, y = y, color = Color.Black)
            }
            if (workspacePreviewActive) {
                Heaven.midiEnter(offSignals)
            } else {
                signalExit?.invoke(offSignals)
            }
        }
    }

    private fun finishVoiceLocked(voiceId: Long) {
        val voice = activeVoices.remove(voiceId) ?: return
        if (editorPreviewVoiceId == voiceId) {
            editorPreviewVoiceId = null
            playing.value = false
        }
        Heaven.cancelJobsForOwner(this, voiceId)
        if (voice.activeCoordinates.isNotEmpty()) {
            val outputOrigin = voice.origin ?: this
            val offSignals = voice.activeCoordinates.map { (x, y) ->
                Signal.LED(origin = outputOrigin, x = x, y = y, color = Color.Black)
            }
            if (workspacePreviewActive) {
                Heaven.midiEnter(offSignals)
            } else {
                signalExit?.invoke(offSignals)
            }
        }
    }

    private fun stopAllVoices() {
        val voicesToClear = synchronized(lock) {
            val list = activeVoices.values.toList()
            activeVoices.clear()
            editorPreviewVoiceId = null
            list
        }

        playing.value = false
        Heaven.cancelJobsForOwner(this)

        val allOffSignals = mutableListOf<Signal.LED>()
        voicesToClear.forEach { voice ->
            if (voice.activeCoordinates.isNotEmpty()) {
                val outputOrigin = voice.origin ?: this
                voice.activeCoordinates.forEach { (x, y) ->
                    allOffSignals.add(Signal.LED(origin = outputOrigin, x = x, y = y, color = Color.Black))
                }
            }
        }

        if (scrubCoordinates.isNotEmpty()) {
            val outputOrigin = playbackOrigin ?: this
            scrubCoordinates.forEach { (x, y) ->
                allOffSignals.add(Signal.LED(origin = outputOrigin, x = x, y = y, color = Color.Black))
            }
            scrubCoordinates = emptySet()
        }

        if (allOffSignals.isNotEmpty()) {
            if (workspacePreviewActive) {
                Heaven.midiEnter(allOffSignals)
            } else {
                signalExit?.invoke(allOffSignals)
            }
        }
    }

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

    private fun emitVoiceFrame(voiceId: Long, signals: List<Signal.LED>) {
        val (previousCoords, outputOrigin) = synchronized(lock) {
            val voice = activeVoices[voiceId] ?: return@synchronized null
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

        if (workspacePreviewActive) {
            Heaven.midiEnter(allSignals)
        } else {
            signalExit?.invoke(allSignals)
        }
    }

    private fun emitScrubFrame(signals: List<Signal.LED>) {
        val previous = scrubCoordinates
        val current = signals.map { it.x to it.y }.toSet()
        val outputOrigin = signals.firstOrNull()?.origin ?: playbackOrigin ?: this
        val offSignals = previous
            .filterNot { it in current }
            .map { (x, y) -> Signal.LED(origin = outputOrigin, x = x, y = y, color = Color.Black) }

        val allSignals = offSignals + signals
        scrubCoordinates = current

        if (workspacePreviewActive) {
            Heaven.midiEnter(allSignals)
        } else {
            signalExit?.invoke(allSignals)
        }
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

    private data class PlaybackVoice(
        val id: Long,
        val origin: Any?,
        val frames: List<RenderedCompositionFrame>,
        val repeat: Boolean,
        val livePreview: Boolean,
        val triggerOrigin: Vec2? = null,
        var activeCoordinates: Set<Pair<Int, Int>> = emptySet(),
        val isEditorPreview: Boolean = false,
    ) {
        fun firstFrameAtOrAfter(progress: Float): Int =
            frames.indexOfFirst { it.progress >= progress }.takeIf { it >= 0 } ?: frames.lastIndex
    }

    companion object : ChainDeviceFactory<CompositionChainDeviceState> {
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
