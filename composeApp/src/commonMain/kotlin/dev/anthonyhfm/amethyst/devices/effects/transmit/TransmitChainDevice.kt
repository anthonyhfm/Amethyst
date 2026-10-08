package dev.anthonyhfm.amethyst.devices.effects.transmit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.elements.isOn
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext

class TransmitChainDevice : LEDChainDevice<TransmitChainDeviceState>() {
    override fun timelineDuration(context: TimelineDurationContext) =
        TimelineDuration.None
    private val channels = (1..MAX_CHANNELS).toList()
    private val isAttachedToChain = atomic(false)
    private val automationLock = SynchronizedObject()

    override val state = MutableStateFlow(TransmitChainDeviceState())
    override val helpRef = "Transmit"

    override fun onAddedToChain() {
        isAttachedToChain.value = true
        updateRegistration()
    }

    override fun onRemovedFromChain() {
        isAttachedToChain.value = false
        unregisterReceiver()
        super.onRemovedFromChain()
    }

    override fun onStateRestored() {
        super.onStateRestored()
        updateRegistration()
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        ChainDeviceShell(
            title = "Transmit",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(160.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                var beforeState = deviceState.copy()

                Spacer(Modifier.weight(1f))

                Dial(
                    title = "Channel",
                    value = deviceState.channel,
                    type = DialType.Steps(channels),
                    text = "${deviceState.channel}",
                    onResolveTextValue = { text ->
                        text.trim().toIntOrNull()?.let { channel ->
                            if (channel in channels) {
                                updateStateFromUser { it.copy(channel = channel) }
                                updateRegistration()
                            }
                        }
                    },
                    onStartValueChange = {
                        beforeState = state.value.copy()
                    },
                    onFinishValueChange = {
                        pushStateChange(before = beforeState, after = state.value)
                    },
                    onValueChange = { channel ->
                        updateStateFromUser {
                            it.copy(channel = channel.coerceIn(1, MAX_CHANNELS))
                        }
                        updateRegistration()
                    },
                )

                Spacer(Modifier.weight(1f))

                ModeSelectField(
                    selectedMode = deviceState.mode,
                    onModeSelected = { mode ->
                        val before = state.value.copy()
                        updateStateFromUser { it.copy(mode = mode) }
                        updateRegistration()
                        pushStateChange(before, state.value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        synchronized(automationLock) {
            if (n.any { it.isOn() }) {
                triggerDialAutomations()
            }
        }
        val signals = n.filterIsInstance<Signal.LED>()
        if (signals.isNotEmpty()) {
            ledSignalEnter(n = signals)
        }
    }

    override fun onAutomationTick() {
        ledSignalEnter(n = emptyList())
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val current = state.value
        when (current.mode) {
            TransmitChainDeviceState.Mode.Send -> {
                val dispatch = currentTransmitDispatch()
                val rootDispatch = dispatch.activeSenders.isEmpty()
                if (!dispatch.activeSenders.add(element = this)) {
                    return
                }

                try {
                    matchingReceivers(
                        channel = current.channel,
                        busId = current.busId,
                    ).forEach { receiver ->
                        if (dispatch.receivers.add(element = receiver)) {
                            receiver.receiveSignals(
                                signals = n,
                                channel = current.channel,
                                busId = current.busId,
                            )
                        }
                    }
                } finally {
                    dispatch.activeSenders.remove(element = this)
                    if (rootDispatch) {
                        dispatch.receivers.clear()
                    }
                }
            }

            TransmitChainDeviceState.Mode.Receive -> {
                signalExit?.invoke(n)
            }
        }
    }

    @Composable
    private fun ModeSelectField(
        selectedMode: TransmitChainDeviceState.Mode,
        onModeSelected: (TransmitChainDeviceState.Mode) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Mode",
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )

            Select(
                value = selectedMode.label,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                shape = SmallShape,
                triggerHeight = 24.dp,
                triggerContentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                TransmitChainDeviceState.Mode.entries.forEach { mode ->
                    SelectItem(
                        text = mode.label,
                        selected = mode == selectedMode,
                        onClick = { onModeSelected(mode) },
                    )
                }
            }
        }
    }

    private fun receiveSignals(
        signals: List<Signal.LED>,
        channel: Int,
        busId: String,
    ) {
        val current = state.value
        if (
            isAttachedToChain.value &&
            !current.isMuted &&
            current.mode == TransmitChainDeviceState.Mode.Receive &&
            current.channel == channel &&
            current.busId == busId
        ) {
            signalExit?.invoke(signals)
        }
    }

    private fun updateRegistration() {
        synchronized(registryLock) {
            if (isAttachedToChain.value && state.value.mode == TransmitChainDeviceState.Mode.Receive) {
                receivers[selectionUUID] = this
            } else {
                receivers.remove(key = selectionUUID)
            }
        }
    }

    private fun unregisterReceiver() {
        synchronized(registryLock) {
            receivers.remove(key = selectionUUID)
        }
    }

    companion object : ChainDeviceFactory<TransmitChainDeviceState> {
        override val stateClass = TransmitChainDeviceState::class
        override val serializer = TransmitChainDeviceState.serializer()
        override fun create() = TransmitChainDevice()

        private const val MAX_CHANNELS = 16
        private val registryLock = SynchronizedObject()
        private val receivers: MutableMap<String, TransmitChainDevice> = mutableMapOf()

        internal fun clearReceivers() {
            synchronized(registryLock) {
                receivers.clear()
            }
        }

        private fun matchingReceivers(channel: Int, busId: String): List<TransmitChainDevice> {
            return synchronized(registryLock) {
                receivers.values.filter { receiver ->
                    val current = receiver.state.value
                    current.mode == TransmitChainDeviceState.Mode.Receive &&
                        !current.isMuted &&
                        current.channel == channel &&
                        current.busId == busId
                }
            }
        }
    }
}

private val TransmitChainDeviceState.Mode.label: String
    get() = when (this) {
        TransmitChainDeviceState.Mode.Send -> "Sender"
        TransmitChainDeviceState.Mode.Receive -> "Receiver"
    }

@Serializable
data class TransmitChainDeviceState(
    val mode: Mode = Mode.Send,
    val channel: Int = 1,
    val busId: String = "",
) : DeviceState() {
    enum class Mode {
        Send,
        Receive
    }
}
