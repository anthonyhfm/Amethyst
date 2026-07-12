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
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionGraph
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.GraphProcessor
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.defaultCompositionGraph
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

class CompositionChainDevice : LEDChainDevice<CompositionChainDeviceState>() {
    override val state = MutableStateFlow(CompositionChainDeviceState())
    override val helpRef = "Composition"

    private val customMode = CompositionWorkspaceMode(this)
    private var activeFrame: ActiveFrame? = null
    private var playbackRun: PlaybackRun? = null
    private var playbackOrigin: Any? = this
    private val playing = mutableStateOf(false)
    private val playbackProgress = mutableStateOf(0f)

    init {
        renderAnimation()
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val activeSignals = n.filter { it.color != Color.Black }

        activeSignals.forEach { trigger ->
            startPlayback(trigger.origin)
        }
    }

    fun play() {
        val startProgress = playbackProgress.value.takeUnless { it >= 1f } ?: 0f
        startPlayback(origin = playbackOrigin, progress = startProgress)
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

    fun seekTo(progress: Float) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        playbackProgress.value = clampedProgress
        renderPlaybackFrame(progress = clampedProgress, origin = playbackRun?.origin ?: playbackOrigin)

        if (playing.value) {
            Heaven.cancelJobsForOwner(this, PLAYBACK_IDENTIFIER)
            val run = playbackRun ?: return
            schedulePlaybackFrame(run.firstFrameAtOrAfter(clampedProgress))
        }
    }

    fun updatePlaybackOptions(transform: (CompositionPlaybackOptions) -> CompositionPlaybackOptions) {
        val before = state.value
        val after = before.copy(playbackOptions = transform(before.playbackOptions))
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

    private fun startPlayback(origin: Any?, progress: Float = 0f) {
        Heaven.cancelJobsForOwner(this, PLAYBACK_IDENTIFIER)
        if (state.value.renderedAnimation.isEmpty()) renderAnimation()
        playbackOrigin = origin
        playbackRun = PlaybackRun(origin = origin, frames = state.value.renderedAnimation)
        playing.value = true
        schedulePlaybackFrame(playbackRun!!.firstFrameAtOrAfter(progress.coerceIn(0f, 1f)))
    }

    private fun schedulePlaybackFrame(frameIndex: Int, delayMs: Double = 0.0) {
        Heaven.schedule(delayInMs = delayMs, owner = this, identifier = PLAYBACK_IDENTIFIER) {
            val run = playbackRun ?: return@schedule
            val options = state.value.playbackOptions
            val durationMs = options.durationMs().coerceAtLeast(FRAME_INTERVAL_MS)
            val frame = run.frames.getOrElse(frameIndex) { run.frames.last() }
            val progress = frame.progress
            playbackProgress.value = progress
            emitFrame(frame.signals.map { it.copy(origin = run.origin) })

            when {
                frameIndex < run.frames.lastIndex -> {
                    val nextProgress = run.frames[frameIndex + 1].progress
                    schedulePlaybackFrame(
                        frameIndex = frameIndex + 1,
                        delayMs = ((nextProgress - progress) * durationMs).coerceAtLeast(0.0),
                    )
                }
                options.repeat -> {
                    // Keep the terminal frame visible for one presentation interval before
                    // restarting; otherwise it is replaced by frame zero in the same tick.
                    Heaven.schedule(
                        delayInMs = FRAME_INTERVAL_MS,
                        owner = this,
                        identifier = PLAYBACK_IDENTIFIER,
                    ) {
                        if (playbackRun !== run) return@schedule
                        playbackProgress.value = 0f
                        playbackRun = PlaybackRun(origin = run.origin, frames = state.value.renderedAnimation)
                        schedulePlaybackFrame(0)
                    }
                }
                else -> {
                    // Do not clear the terminal frame in the same callback that emitted it.
                    Heaven.schedule(
                        delayInMs = FRAME_INTERVAL_MS,
                        owner = this,
                        identifier = PLAYBACK_IDENTIFIER,
                    ) {
                        if (playbackRun === run) finishPlayback()
                    }
                }
            }
        }
    }

    private fun finishPlayback() {
        playbackRun = null
        playing.value = false
        Heaven.cancelJobsForOwner(this, PLAYBACK_IDENTIFIER)
        clearActiveFrame()
    }

    private fun CompositionPlaybackOptions.durationMs(): Double {
        val base = timing.toMsValue(WorkspaceRepository.bpm.value).toDouble()
        return base * gate.coerceIn(0.05f, 4f).toDouble()
    }

    private fun renderPlaybackFrame(progress: Float, origin: Any?) {
        val frame = state.value.renderedAnimation
            .firstOrNull { it.progress >= progress }
            ?: state.value.renderedAnimation.lastOrNull()
        if (frame != null) {
            emitFrame(frame.signals.map { it.copy(origin = origin) })
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

    private fun emitFrame(signals: List<Signal.LED>) {
        val previousFrame = activeFrame
        val previous = previousFrame?.coordinates.orEmpty()
        val current = signals.map { it.x to it.y }.toSet()
        val outputOrigin = signals.firstOrNull()?.origin ?: previousFrame?.origin ?: this
        val offSignals = previous
            .filterNot { it in current }
            .map { (x, y) -> Signal.LED(origin = outputOrigin, x = x, y = y, color = Color.Black) }

        activeFrame = ActiveFrame(coordinates = current, origin = outputOrigin)
        signalExit?.invoke(offSignals + signals)
    }

    private fun clearActiveFrame() {
        val previousFrame = activeFrame ?: return
        activeFrame = null
        if (previousFrame.coordinates.isEmpty()) return

        signalExit?.invoke(
            previousFrame.coordinates.map { (x, y) ->
                Signal.LED(origin = previousFrame.origin, x = x, y = y, color = Color.Black)
            }
        )
    }

    fun updateGraph(undoable: Boolean = true, transform: (CompositionGraph) -> CompositionGraph): Boolean {
        val before = state.value
        val after = before.copy(graph = transform(before.graph))
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
    ) {
        fun firstFrameAtOrAfter(progress: Float): Int =
            frames.indexOfFirst { it.progress >= progress }.takeIf { it >= 0 } ?: frames.lastIndex
    }

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
