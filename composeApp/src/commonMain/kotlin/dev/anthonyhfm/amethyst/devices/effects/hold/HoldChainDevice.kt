package dev.anthonyhfm.amethyst.devices.effects.hold

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class HoldChainDevice : GenericChainDevice<HoldChainDeviceState>(), Chokeable {
    override val state = MutableStateFlow(HoldChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Hold",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(160.dp)
        ) {
            Column (
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ){
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            space = 16.dp,
                            alignment = Alignment.CenterHorizontally
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        var beforeHold = deviceState.copy().let { (t, ms) ->
                            Pair(t, ms)
                        }

                        TimeDial(
                            headline = "Hold",
                            text = if (deviceState.infinite) "Infinite" else null,
                            timing = deviceState.timing,
                            onStartValueChange = { t, ms ->
                                beforeHold = Pair(t, ms)
                            },
                            onSelectTiming = { timing, msValue ->
                                state.update {
                                    it.copy(
                                        timing = timing,
                                        delayMs = msValue
                                    )
                                }
                            },
                            onFinishValueChange = { t, ms ->
                                pushStateChange(
                                    before = state.value.copy(
                                        timing = beforeHold.first,
                                        delayMs = beforeHold.second
                                    ),
                                    after = state.value
                                )
                            },
                            enabled = !deviceState.infinite
                        )

                        var beforeGate = deviceState.copy().gate
                        TextDial(
                            headline = "Gate",
                            text = if (!deviceState.infinite) "${(deviceState.gate * 200).toInt()}%" else "Disabled",
                            value = deviceState.gate,
                            onStartValueChange = {
                                beforeGate = it
                            },
                            onValueChange = { value ->
                                state.update {
                                    it.copy(gate = value)
                                }
                            },
                            onResolveTextValue = {
                                val gateText = it.removeSuffix("%").trim().toIntOrNull()

                                gateText?.let { gate ->
                                    if (gate in 0..200) {
                                        state.update {
                                            it.copy(gate = gate / 200f) // Convert to float between 0.0 and 1.0
                                        }
                                    }
                                }
                            },
                            onFinishValueChange = {
                                pushStateChange(
                                    before = state.value.copy(gate = beforeGate),
                                    after = state.value
                                )
                            },
                            modifier = Modifier
                                .rightClickable {
                                    pushStateChange(
                                        before = state.value,
                                        after = state.value.copy(gate = 0.5f)
                                    )

                                    state.update {
                                        it.copy(gate = 0.5f) // Reset gate to its original state
                                    }
                                },
                            enabled = !deviceState.infinite
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.offset(x = (-8).dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.offset(y = 6.dp)
                        ) {
                            Checkbox(
                                checked = deviceState.onRelease,
                                onCheckedChange = { checked ->
                                    pushStateChange(
                                        before = deviceState,
                                        after = deviceState.copy(onRelease = checked)
                                    )

                                    state.update {
                                        it.copy(onRelease = checked)
                                    }
                                },
                            )

                            Text(
                                text = "On Release",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = deviceState.infinite,
                                onCheckedChange = { checked ->
                                    pushStateChange(
                                        before = deviceState,
                                        after = deviceState.copy(infinite = checked)
                                    )

                                    state.update {
                                        it.copy(infinite = checked)
                                    }
                                },
                            )

                            Text(
                                text = "Infinite",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                }
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
         val bpm = WorkspaceRepository.bpm.value

         n.forEach { signal ->
             val down: Boolean = when (signal) {
                 is Signal.LED -> signal.color != Color.Black
                 is Signal.Midi -> signal.velocity != 0
                 else -> false
             }

             val signalX = when (signal) {
                 is Signal.LED -> signal.x
                 is Signal.Midi -> signal.x
                 else -> null
             }
             val signalY = when (signal) {
                 is Signal.LED -> signal.y
                 is Signal.Midi -> signal.y
                 else -> null
             }

            val signalOwner: Any = if (signalX != null && signalY != null) {
                Pair(this, "${signalX},${signalY}")
            } else {
                Pair(this, signal.hashCode()) // fallback for signals without coordinates
            }

            val holdDelayMs = state.value.timing.toMsValue(bpm).toDouble() * (state.value.gate * 2)

            val updateSchedule: () -> Unit = {
                Heaven.cancelJobs { job ->
                    job.owner == signalOwner
                }

                Heaven.schedule(holdDelayMs, signalOwner) {
                    if (signal is Signal.LED) {
                        signalExit?.invoke(listOf(signal.copy(color = Color.Black)))
                    } else if (signal is Signal.Midi) {
                        signalExit?.invoke(listOf(signal.copy(velocity = 0)))
                    }
                }
             }

             if (down) {
                 if (state.value.onRelease) {
                     return@forEach
                 }

                signalExit?.invoke(listOf(signal))

                if (state.value.infinite) {
                     return@forEach
                 }

                updateSchedule()
             } else {
                 if (!state.value.onRelease) {
                     return@forEach
                 }

                Heaven.schedule(0.0) {
                    if (signal is Signal.LED) {
                        signalExit?.invoke(listOf(signal.copy(color = Color.White)))
                    } else if (signal is Signal.Midi) {
                        signalExit?.invoke(listOf(signal.copy(velocity = 127)))
                    }
                }

                if (state.value.infinite) {
                     return@forEach
                 }

                updateSchedule()
             }
         }
     }

    override fun onChoke() {
        // Cancel all scheduled Heaven tasks owned by this device
        // The hold device uses Pair(this, "${signalX},${signalY}") as owner
        Heaven.cancelJobs { job ->
            job.owner is Pair<*, *> && job.owner.first == this
        }
    }
}

@Serializable
data class HoldChainDeviceState(
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val delayMs: Long = 0,
    val gate: Float = 0.5f, // 100% = 0.5f, 200% = 1.0f
    val infinite: Boolean = false,
    val onRelease: Boolean = false,
) : DeviceState()