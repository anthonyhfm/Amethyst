package dev.anthonyhfm.amethyst.devices.effects.choke

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.elements.isOn
import dev.anthonyhfm.amethyst.core.engine.elements.isSilentReplay
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurface
import dev.anthonyhfm.amethyst.ui.theme.chainSurfaceRaised
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceCapability
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.devices.ableton.AbletonNoteSpace
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KEYFRAMES_NOTE_ZERO_HANDLED
import dev.anthonyhfm.amethyst.devices.timelineDuration
import dev.anthonyhfm.amethyst.workspace.chain.ui.ChainView
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

class ChokeChainDevice : GenericChainDevice<ChokeChainDeviceState>(), NestedChainDevice, Chokeable {
    override val state = MutableStateFlow(ChokeChainDeviceState())
    override val helpRef = "Choke"
    override val capabilities: Set<DeviceCapability> = setOf(
        DeviceCapability.Container,
        DeviceCapability.TriggerTool,
    )
    private val outputLock = SynchronizedObject()
    private val outputSignals = LedOutputBuffer()

    override fun timelineDuration(context: TimelineDurationContext) =
        state.value.chain.timelineDuration(context)

    init {
        state.value.chain.signalExit = {
            emitSignals(it)
        }

        synchronized(registryLock) {
            chokeDevices.add(this)
        }
    }

    override fun onAddedToChain(parentChain: Chain) {
        super.onAddedToChain(parentChain)
        bindPlaybackCallbacks()
        synchronized(registryLock) {
            chokeDevices.add(this)
        }
    }

    override fun onRemovedFromChain() {
        performChoke()
        synchronized(registryLock) {
            chokeDevices.remove(this)
        }
        super.onRemovedFromChain()
    }

    override fun onStateRestored() {
        super.onStateRestored()
        state.value.chain.signalExit = { emitSignals(it) }
        bindPlaybackCallbacks()
        parentChain?.onDeviceRuntimeStateChanged()
    }

    companion object : ChainDeviceFactory<ChokeChainDeviceState> {
        override val stateClass = ChokeChainDeviceState::class
        override val serializer = ChokeChainDeviceState.serializer()
        override fun create() = ChokeChainDevice()
        override fun pack(device: GenericChainDevice<ChokeChainDeviceState>): ChokeChainDeviceState =
            device.state.value.copy(stateChain = StateChain.pack(device.state.value.chain))

        override fun unpack(state: ChokeChainDeviceState): ChokeChainDevice =
            ChokeChainDevice().apply {
                val unpackedChain = state.stateChain.unpack()
                unpackedChain.signalExit = {
                    emitSignals(it)
                }
                this.state.update { state.copy(chain = unpackedChain) }
                bindPlaybackCallbacks()
            }

        private val registryLock = SynchronizedObject()
        private val chokeDevices = mutableSetOf<ChokeChainDevice>()

        fun chokeChannel(channel: Int, triggeringDevice: ChokeChainDevice) {
            val targets = synchronized(registryLock) {
                chokeDevices.filter { it !== triggeringDevice && it.state.value.target == channel }
            }

            targets.forEach { it.performChoke() }
        }
    }

    override fun onChoke() {
        performChoke()
    }

    private fun performChoke() {
        val visited = mutableSetOf<Chain>()
        fun stop(chain: Chain) {
            if (!visited.add(element = chain)) {
                return
            }
            chain.devices.value.forEach { device ->
                if (device is Chokeable) {
                    device.onChoke()
                } else if (device is NestedChainDevice) {
                    device.nestedChains().forEach { stop(chain = it) }
                }
            }
        }
        stop(chain = state.value.chain)

        synchronized(outputLock) {
            val offSignals = outputSignals.drainOffSignals()
            if (offSignals.isNotEmpty()) {
                signalExit?.invoke(offSignals)
            }
        }
    }

    private fun emitSignals(signals: List<Signal>) {
        if (state.value.mode == ChokeChainDeviceState.ChokeMode.NoteZero &&
            signals.any {
                it.isOn() && it.extras[KEYFRAMES_NOTE_ZERO_HANDLED] != 1 && AbletonNoteSpace.note(it)?.pitch == 0
            }
        ) {
            chokeChannel(state.value.target, this)
        }

        synchronized(outputLock) {
            signals.forEach { signal ->
                if (signal is Signal.LED) {
                    if (signal.color.red > 0f || signal.color.green > 0f || signal.color.blue > 0f) {
                        outputSignals.put(signal)
                    } else {
                        outputSignals.remove(signal)
                    }
                }
            }
            signalExit?.invoke(signals)
        }
    }

    @Composable
    override fun Content() {
        Content(rememberDragAndDropState())
    }

    @Composable
    fun Content(
        dragAndDropState: DragAndDropState<GenericChainDevice<*>> = rememberDragAndDropState()
    ) {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        Row(
            modifier = Modifier
                .clip(DefaultShape)
                .fillMaxHeight()
                .background(Theme[chainColorTokens][chainSurface])
        ) {
            ChainDeviceShell(
                title = "Choke",
                isSelected = isSelected,
                modifier = Modifier
                    .width(100.dp),
                titleBarModifier = LocalTitleBarModifier.current
            ) {
                Dial(
                    title = "Target",
                    value = deviceState.target,
                    type = DialType.Steps(IntArray(32) { it + 1 }.toList()),
                    text = "${deviceState.target}",
                    onResolveTextValue = {
                        val chokeChannel = it.trim().toIntOrNull()

                        chokeChannel?.let { channel ->
                            if (chokeChannel in 0..32) {
                                updateStateFromUser {
                                    it.copy(target = channel)
                                }
                            }
                        }
                    },
                    onValueChange = { value ->
                        updateStateFromUser {
                            it.copy(target = value)
                        }
                    }
                )
            }

            key( // Trigger recomposition on selected group change
                state.collectAsState().value
            ) {
                GroupContent(dragAndDropState)
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(28.dp)
                    .clip(DefaultShape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(Theme[colors][selectionSurface], DefaultShape)
                                .border(1.dp, Theme[colors][selectionSurface], DefaultShape)
                        } else {
                            Modifier
                                .background(Theme[chainColorTokens][chainSurfaceRaised], DefaultShape)
                                .border(1.dp, Theme[chainColorTokens][chainBorder], DefaultShape)
                        }
                    )
            )
        }
    }

    @Composable
    private fun GroupContent(dragAndDropState: DragAndDropState<GenericChainDevice<*>>) {
        ChainView(
            chain = state.value.chain,
            dragAndDropState = dragAndDropState,
            parentSelectionUUID = selectionUUID,
            showContextMenu = false,
            showRemoteFocus = false,
        )
    }

    override fun signalEnter(n: List<Signal>) {
        bindPlaybackCallbacks()
        if (!n.isSilentReplay()) {
            val hasPress = n.any { it.isOn() }
            if (hasPress) {
                performChoke()
            }
            if (state.value.mode == ChokeChainDeviceState.ChokeMode.Legacy ||
                state.value.mode == ChokeChainDeviceState.ChokeMode.Start && hasPress
            ) {
                chokeChannel(state.value.target, this)
            }
        }

        state.value.chain.signalEnter(n)
    }

    private fun bindPlaybackCallbacks() {
        playbackDevices().forEach { device ->
            device.onPlaybackNoteZero = {
                if (state.value.mode == ChokeChainDeviceState.ChokeMode.NoteZero && device in playbackDevices()) {
                    chokeChannel(state.value.target, this)
                }
            }
            device.onPlaybackEnd = {
                if (state.value.mode == ChokeChainDeviceState.ChokeMode.End && device in playbackDevices()) {
                    chokeChannel(state.value.target, this)
                }
            }
        }
    }

    private fun playbackDevices(): List<KeyframesChainDevice> = buildList {
        val visited = mutableSetOf<Chain>()

        fun visit(chain: Chain) {
            if (!visited.add(chain)) {
                return
            }
            chain.devices.value.forEach { device ->
                if (device is KeyframesChainDevice) {
                    add(device)
                } else if (device is NestedChainDevice && device !is ChokeChainDevice) {
                    device.nestedChains().forEach(::visit)
                }
            }
        }

        visit(chain = state.value.chain)
    }

    override fun nestedChains() = listOf(state.value.chain)
}

private class LedOutputBuffer {
    private var signals = arrayOfNulls<Signal.LED>(16)
    private var slots = ByteArray(16)
    private var size = 0
    private var used = 0

    fun put(signal: Signal.LED) {
        if (used * 2 >= signals.size) {
            rebuild(capacity = if (size * 4 >= signals.size) signals.size * 2 else signals.size)
        }

        val index = findSlot(x = signal.x, y = signal.y, layer = signal.layer)
        if (slots[index] != 1.toByte()) {
            if (slots[index] == 0.toByte()) {
                used++
            }
            slots[index] = 1
            size++
        }
        signals[index] = signal
    }

    fun remove(signal: Signal.LED) {
        val index = findSlot(x = signal.x, y = signal.y, layer = signal.layer)
        if (slots[index] == 1.toByte()) {
            slots[index] = 2
            signals[index] = null
            size--
        }
    }

    fun drainOffSignals(): List<Signal.LED> {
        if (size == 0) {
            signals.fill(null)
            slots.fill(0)
            used = 0
            return emptyList()
        }

        val offSignals = ArrayList<Signal.LED>(size)
        for (index in signals.indices) {
            if (slots[index] == 1.toByte()) {
                offSignals.add(signals[index]!!.copy(color = Color.Black, opacity = 1f))
            }
        }
        signals.fill(null)
        slots.fill(0)
        size = 0
        used = 0
        return offSignals
    }

    private fun findSlot(x: Int, y: Int, layer: Int): Int {
        var index = (((x * 31 + y) * 31 + layer) xor (layer ushr 16)) and (signals.size - 1)
        var removed = -1

        while (slots[index] != 0.toByte()) {
            val current = signals[index]
            if (slots[index] == 1.toByte() && current!!.x == x && current.y == y && current.layer == layer) {
                return index
            }
            if (slots[index] == 2.toByte() && removed == -1) {
                removed = index
            }
            index = (index + 1) and (signals.size - 1)
        }

        return if (removed != -1) removed else index
    }

    private fun rebuild(capacity: Int) {
        val oldSignals = signals
        val oldSlots = slots
        signals = arrayOfNulls(capacity)
        slots = ByteArray(capacity)
        size = 0
        used = 0

        for (index in oldSignals.indices) {
            if (oldSlots[index] == 1.toByte()) {
                put(oldSignals[index]!!)
            }
        }
    }
}

@Serializable
data class ChokeChainDeviceState(
    val target: Int = 0,
    @Transient
    val chain: Chain = Chain(),
    var stateChain: StateChain = StateChain(),
    val mode: ChokeMode = ChokeMode.Legacy,
) : DeviceState() {
    @Serializable
    enum class ChokeMode {
        Legacy, Start, NoteZero, End, Receive
    }
}
