package dev.anthonyhfm.amethyst.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.elements.isOn
import dev.anthonyhfm.amethyst.core.engine.elements.SignalReceiver
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncCoordinator
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.workspace.chain.ui.CollapsedChainDevice
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

val LocalChainDevice = staticCompositionLocalOf<GenericChainDevice<*>?> { null }

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

    internal fun configure(
        sampleRate: Int,
        absoluteFrame: Long,
        transportFrame: Long,
    ) {
        this.sampleRate = sampleRate
        this.absoluteFrame = absoluteFrame
        this.transportFrame = transportFrame
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

    val dialAutomations = MutableStateFlow<Map<String, dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane>>(emptyMap())
    private val dialAutomationRuntimes = mutableMapOf<String, dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationRuntime>()

    fun setDialAutomation(parameterId: String, lane: dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane?) {
        val current = dialAutomations.value.toMutableMap()
        if (lane == null) {
            current.remove(parameterId)
            dialAutomationRuntimes.remove(parameterId)
        } else {
            current[parameterId] = lane
            val runtime = dialAutomationRuntimes[parameterId]
            if (runtime != null) {
                runtime.lane = lane
            } else {
                dialAutomationRuntimes[parameterId] = dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationRuntime(lane)
            }
        }
        dialAutomations.value = current
    }

    fun getDialAutomation(parameterId: String): dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane? {
        return dialAutomations.value[parameterId]
    }

    /**
     * Loads persisted automation lanes from DeviceState into the runtime map.
     * Called automatically by ChainDeviceFactory.unpack().
     */
    fun restoreAutomationsFromState() {
        state.value.automations.forEach { (id, lane) ->
            setDialAutomation(id, lane)
        }
    }

    /**
     * Writes current runtime automation lanes back into DeviceState for persistence.
     * Called automatically by DeviceRegistry.pack().
     */
    @Suppress("UNCHECKED_CAST")
    fun persistAutomationsToState() {
        val currentAutomations = dialAutomations.value
        if (currentAutomations.isEmpty() && state.value.automations.isEmpty()) return
        val updated = state.value.withAutomations(currentAutomations) as State
        state.value = updated
    }

    private val automationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var automationTickerJob: Job? = null

    fun triggerDialAutomations(nowMs: Long = Clock.System.now().toEpochMilliseconds()) {
        dialAutomationRuntimes.values.forEach { it.trigger(nowMs) }
        startAutomationTicker()
    }

    open fun onAutomationTick() {
        // Subclasses override to re-evaluate parameters during active signal processing
    }

    private fun startAutomationTicker() {
        if (automationTickerJob?.isActive == true) return
        automationTickerJob = automationScope.launch {
            while (dialAutomationRuntimes.values.any { it.isRunning }) {
                onAutomationTick()
                val frameIntervalMs = (1000.0 / Heaven.fps.coerceAtLeast(1)).toLong().coerceAtLeast(1L)
                delay(frameIntervalMs)
            }
            onAutomationTick()
        }
    }

    fun evaluateAutomatedDialValue(
        parameterId: String,
        manualNormalizedValue: Float,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
        bpm: Float = 120f
    ): Float {
        val runtime = dialAutomationRuntimes[parameterId] ?: return manualNormalizedValue
        if (!runtime.isRunning) return manualNormalizedValue
        val progress = runtime.currentProgress(nowMs, bpm)
        val automatedNormalized = runtime.lane.valueAt(progress, manualNormalizedValue * 2f - 1f)
        val automated0to1 = (automatedNormalized + 1f) * 0.5f
        return if (runtime.lane.settings.isAdditive) {
            (manualNormalizedValue + automatedNormalized * 0.5f).coerceIn(0f, 1f)
        } else {
            automated0to1.coerceIn(0f, 1f)
        }
    }

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
    private val deviceLock = SynchronizedObject()

    @Composable
    abstract override fun Content()

    abstract fun ledSignalEnter(n: List<Signal.LED>)

    override fun signalEnter(n: List<Signal>) {
        synchronized(deviceLock) {
            if (n.any { it.isOn() }) {
                triggerDialAutomations()
            }
            n.filterIsInstance<Signal.LED>().let {
                if (it.isNotEmpty()) {
                    ledSignalEnter(it)
                }
            }
        }
    }

    override fun onAutomationTick() {
        synchronized(deviceLock) {
            ledSignalEnter(emptyList())
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
        if (n.isNotEmpty()) {
            triggerDialAutomations()
        }
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
