package dev.anthonyhfm.amethyst.devices.effects.delay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.TextDial
import dev.anthonyhfm.amethyst.ui.components.primitives.TimeDial
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class DelayChainDevice : GenericChainDevice<DelayChainDeviceState>(), Chokeable {
    override val helpRef = "Delay"
    override val state = MutableStateFlow(DelayChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Delay",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(100.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var beforeDelay = deviceState.copy().let { (t, ms, _) ->
                    Pair(t, ms)
                }

                TimeDial(
                    headline = "Delay",
                    timing = deviceState.timing,
                    onSelectTiming = { timing, msValue ->
                        state.update {
                            it.copy(timing = timing, delayMs = msValue)
                        }
                    },
                    onStartValueChange = { t, ms ->
                        beforeDelay = Pair(t, ms)
                    },
                    onFinishValueChange = { timing, msValue ->
                        pushStateChange(
                            before = state.value.copy(
                                timing = beforeDelay.first,
                                delayMs = beforeDelay.second
                            ),
                            after = state.value.copy(
                                timing = timing,
                                delayMs = msValue
                            )
                        )
                    }
                )

                var beforeGateDrag = deviceState.copy().gate
                TextDial(
                    headline = "Gate",
                    text = "${(deviceState.gate * 200).roundToInt()}%",
                    value = deviceState.gate,
                    onStartValueChange = {
                        beforeGateDrag = deviceState.gate
                    },
                    onValueChange = { value ->
                        state.update {
                            it.copy(gate = value)
                        }
                    },
                    onFinishValueChange = {
                        pushStateChange(state.value.copy(gate = beforeGateDrag), state.value.copy(gate = it))
                    },
                    onResolveTextValue = {
                        val gateText = it.removeSuffix("%").trim().toIntOrNull()

                        gateText?.let { gate ->
                            if (gate in 0..200) {
                                val before = state.value
                                val after = before.copy(gate = gate / 200f)
                                state.value = after
                                pushStateChange(before, after)
                            }
                        }
                    },
                    modifier = Modifier
                        .rightClickable {
                            val before = state.value
                            val after = before.copy(gate = 0.5f)
                            state.value = after
                            pushStateChange(before, after)
                        },
                )
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        val bpm = WorkspaceRepository.bpm.value
        val delay = state.value.timing.toMsValue(bpm).toDouble()
        Heaven.schedule(
            delayInMs = delay * (state.value.gate * 2),
            owner = this
        ) {
            signalExit?.invoke(n)
        }
    }

    override fun onChoke() {
        // Cancel all scheduled Heaven tasks owned by this device
        Heaven.cancelJobsForOwner(this)
    }

    companion object : ChainDeviceFactory<DelayChainDeviceState> {
        override val stateClass = DelayChainDeviceState::class
        override val serializer = DelayChainDeviceState.serializer()
        override fun create() = DelayChainDevice()
    }
}

@Serializable
data class DelayChainDeviceState(
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val delayMs: Long = 0,
    val gate: Float = 0.5f, // 100% = 0.5f, 200% = 1.0f
) : DeviceState()
