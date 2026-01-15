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
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class DelayChainDevice : GenericChainDevice<DelayChainDeviceState>(), Chokeable {
    override val state = MutableStateFlow(DelayChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Delay",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(100.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp)
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
                    text = "${(deviceState.gate * 200).toInt()}%",
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
        n.forEach { signal ->
            when (signal) {
                is Signal.LED -> handleSignal(signal, bpm)
                is Signal.Midi -> handleSignal(signal, bpm)
                else -> return@forEach
            }
        }
    }

    private fun handleSignal(signal: Signal, bpm: Double) {
        val owner = Pair(this, signal.hashCode())
        Heaven.cancelJobsForOwner(owner)

        if (signal is Signal.Midi && signal.velocity == 0) {
            return
        }

        val delayMs = state.value.timing.toMsValue(bpm) * (state.value.gate * 2)
        Heaven.schedule(delayMs.toDouble(), owner = owner) {
            signalExit?.invoke(listOf(signal))
        }
    }

    override fun onChoke() {
        Heaven.cancelJobsForOwner(this)
    }
}

@Serializable
data class DelayChainDeviceState(
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val delayMs: Long = 0,
    val gate: Float = 0.5f, // 100% = 0.5f, 200% = 1.0f
) : DeviceState()