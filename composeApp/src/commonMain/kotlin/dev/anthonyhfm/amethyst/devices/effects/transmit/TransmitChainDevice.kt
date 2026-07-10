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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class TransmitChainDevice : LEDChainDevice<TransmitChainDeviceState>() {
    private val channels = (1..MAX_CHANNELS).toList()
    private var isAttachedToChain = false

    override val state = MutableStateFlow(TransmitChainDeviceState())
    override val helpRef = "Transmit"

    override fun onAddedToChain() {
        isAttachedToChain = true
        updateRegistration()
    }

    override fun onRemovedFromChain() {
        isAttachedToChain = false
        unregisterReceiver()
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        DisposableEffect(deviceState.mode, deviceState.channel) {
            updateRegistration()

            onDispose {
                unregisterReceiver()
            }
        }

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
                                state.update { it.copy(channel = channel) }
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
                        state.update {
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
                        state.update { it.copy(mode = mode) }
                        updateRegistration()
                        pushStateChange(before, state.value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        when (state.value.mode) {
            TransmitChainDeviceState.Mode.Send -> {
                matchingReceivers(channel = state.value.channel).forEach { receiver ->
                    receiver.receiveSignals(n)
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

    private fun receiveSignals(signals: List<Signal.LED>) {
        if (isAttachedToChain && state.value.mode == TransmitChainDeviceState.Mode.Receive) {
            signalExit?.invoke(signals)
        }
    }

    private fun updateRegistration() {
        if (isAttachedToChain && state.value.mode == TransmitChainDeviceState.Mode.Receive) {
            receivers[selectionUUID] = this
        } else {
            receivers.remove(selectionUUID)
        }
    }

    private fun unregisterReceiver() {
        receivers.remove(selectionUUID)
    }

    companion object : ChainDeviceFactory<TransmitChainDeviceState> {
        override val stateClass = TransmitChainDeviceState::class
        override val serializer = TransmitChainDeviceState.serializer()
        override fun create() = TransmitChainDevice()

        private const val MAX_CHANNELS = 16
        private val receivers: MutableMap<String, TransmitChainDevice> = mutableMapOf()

        internal fun clearReceiversForTesting() {
            receivers.clear()
        }

        private fun matchingReceivers(channel: Int): List<TransmitChainDevice> {
            return receivers.values
                .filter { it.state.value.mode == TransmitChainDeviceState.Mode.Receive && it.state.value.channel == channel }
                .toList()
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
    val channel: Int = 1
) : DeviceState() {
    enum class Mode {
        Send,
        Receive
    }
}
