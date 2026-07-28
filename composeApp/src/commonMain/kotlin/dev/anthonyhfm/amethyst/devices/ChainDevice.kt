package dev.anthonyhfm.amethyst.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.elements.SignalReceiver
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncCoordinator
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.workspace.chain.ui.CollapsedChainDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

enum class AudioChainDeviceRole {
    Generator,
    Effect,
}

data class AudioConfiguration(
    val sampleRate: Int,
    val channels: Int = 2,
    val periodFrames: Int,
    val maximumBlockFrames: Int = periodFrames,
)

class AudioRenderContext(
    sampleRate: Int,
    absoluteFrame: Long,
    transportFrame: Long = absoluteFrame,
) {
    var sampleRate: Int = sampleRate
        private set
    var absoluteFrame: Long = absoluteFrame
        private set
    var transportFrame: Long = transportFrame
        private set
    var mixContributionEnergy: Float = 0f
        private set

    internal fun configure(
        sampleRate: Int,
        absoluteFrame: Long,
        transportFrame: Long,
    ) {
        this.sampleRate = sampleRate
        this.absoluteFrame = absoluteFrame
        this.transportFrame = transportFrame
        mixContributionEnergy = 0f
    }

    /**
     * Reports a nominal contribution to the adaptive master headroom stage.
     * This is called only by the single render thread.
     */
    fun addMixContribution(gain: Float) {
        if (!gain.isFinite() || gain <= 0f) return
        mixContributionEnergy += gain * gain
    }
}

abstract class GenericChainDevice <State : @Serializable DeviceState> : SignalReceiver(), Selectable {
    override var selectionUUID: String = UUID.randomUUID()

    abstract val state: MutableStateFlow<State>

    open val helpRef: String? = null

    open val title: String
        get() = helpRef ?: this::class.simpleName?.removeSuffix("ChainDevice") ?: "Device"

    @Composable
    open fun CollapsedContent() {
        CollapsedChainDevice(device = this)
    }

    var parentChain: Chain? = null

    val isMuted: Boolean
        get() = state.value.isMuted

    val isCollapsed: Boolean
        get() = isCollapsedState.value

    var isCollapsedState: MutableState<Boolean> = mutableStateOf(false)
    var isDragging: MutableState<Boolean> = mutableStateOf(false)

    open fun onAddedToChain() = Unit

    open fun onAddedToChain(parentChain: Chain) {
        this.parentChain = parentChain
        onAddedToChain()
    }

    open fun onRemovedFromChain() {
        this.parentChain = null
    }

    fun setMuted(muted: Boolean) {
        val current = state.value
        if (current.isMuted != muted) {
            current.isMuted = muted
            state.update { current }
            parentChain?.onDeviceRuntimeStateChanged()
        }
    }

    fun setCollapsed(collapsed: Boolean) {
        val current = state.value
        if (current.isCollapsed != collapsed) {
            current.isCollapsed = collapsed
            isCollapsedState.value = collapsed
            state.update { current }
            parentChain?.onDeviceRuntimeStateChanged()
        }
    }

    open fun onStateRestored() {
        isCollapsedState.value = state.value.isCollapsed
    }

    @Composable
    abstract fun Content()

    abstract override fun signalEnter(n: List<Signal>)

    protected fun pushStateChange(before: State, after: State) {
        if (before != after) {
            UndoManager.addAction(
                UndoableAction.ChangeDeviceState(
                    device = this,
                    beforeState = before,
                    afterState = after
                )
            )

            ChainSyncCoordinator.onDeviceStateChanged(this, after)
        }
    }
}

abstract class LEDChainDevice <State : @Serializable DeviceState> : GenericChainDevice<State>() {
    @Composable
    abstract override fun Content()

    abstract fun ledSignalEnter(n: List<Signal.LED>)

    override fun signalEnter(n: List<Signal>) {
        n.filterIsInstance<Signal.LED>().let {
            if (it.isNotEmpty()) {
                ledSignalEnter(it)
            }
        }
    }
}

abstract class AudioChainDevice <State : @Serializable DeviceState> : GenericChainDevice<State>() {
    open val audioRole: AudioChainDeviceRole = AudioChainDeviceRole.Effect
    open val latencyFrames: Int = 0
    open val tailFrames: Long = 0L

    @Composable
    abstract override fun Content()

    open fun prepareAudio(configuration: AudioConfiguration) = Unit

    open fun processAudio(
        block: AudioProcessingBlock,
        context: AudioRenderContext,
    ) = Unit

    open fun resetAudio() = Unit
    open fun releaseAudio() = Unit

    override fun signalEnter(n: List<Signal>) {
        signalExit?.invoke(n)
    }
}

/**
 * A fixed-size interleaved PCM block used by Echo-capable chain devices.
 *
 * Processing is deliberately in-place so implementations can be promoted to the
 * native render path without changing their public contract.
 */
class AudioProcessingBlock(
    val samples: FloatArray,
    val channels: Int,
    val maximumFrames: Int,
) {
    var frameCount: Int = 0
        internal set
    var frameOffset: Long = 0L
        internal set

    fun configure(frameCount: Int, frameOffset: Long) {
        require(frameCount in 0..maximumFrames)
        this.frameCount = frameCount
        this.frameOffset = frameOffset
    }

    fun clear() {
        samples.fill(0f, 0, frameCount * channels)
    }
}
