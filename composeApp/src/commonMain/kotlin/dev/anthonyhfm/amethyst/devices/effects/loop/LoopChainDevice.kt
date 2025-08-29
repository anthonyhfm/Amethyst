package dev.anthonyhfm.amethyst.devices.effects.loop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.StepTextDial
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class LoopChainDevice : GenericChainDevice<LoopChainDeviceState>() {
    override val state = MutableStateFlow(LoopChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Loop",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(200.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                Column (
                    modifier = Modifier
                        .fillMaxHeight().padding(start = 8.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, alignment = Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column (
                        Modifier.offset(x = 2.dp)
                    ){
                        StepTextDial(
                            headline = "Repeat",
                            text = if (deviceState.onHold) "Unused" else "${deviceState.repeat}",
                            steps = IntArray(128) { it + 1 }.toList(),
                            value = deviceState.repeat,
                            onResolveTextValue = {
                                if (!deviceState.onHold) {
                                    val repeatText = it.trim().toIntOrNull()

                                    repeatText?.let { repeat ->
                                        if (repeat in 1..128) {
                                            state.update {
                                                it.copy(repeat = repeat)
                                            }
                                        }
                                    }
                                }
                            },
                            onValueChange = { value ->
                                if (!deviceState.onHold) {
                                state.update {
                                    it.copy(repeat = value)
                                }
                                }
                            },
                            modifier = Modifier
                                .alpha(if (deviceState.onHold) 0.4f else 1f)
                                .gesturesDisabled(deviceState.onHold),
                        )
                    }

                    Column (
                        horizontalAlignment = Alignment.Start,
                    ){
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.offset(x = -4.dp)
                        ) {
                            Checkbox(
                                checked = deviceState.onHold,
                                onCheckedChange = { checked ->
                                    state.update {
                                        it.copy(onHold = checked)
                                    }
                                },
                            )

                            Text(
                                text = "Hold",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(160.dp),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    TimeDial(
                        headline = "Delay",
                        timing = deviceState.timing,
                        onSelectTiming = { timing, msValue ->
                            state.update {
                                it.copy(
                                    timing = timing,
                                )
                            }
                        }
                    )

                    TextDial(
                        headline = "Gate",
                        text = "${(deviceState.gate * 200).toInt()}%",
                        value = deviceState.gate,
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
                        modifier = Modifier
                            .rightClickable {
                                state.update {
                                    it.copy(gate = 0.5f) // Reset gate to its original state
                                }
                            },
                    )
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
                else -> return
            }

            val coords: Pair<Int, Int> = when (signal) {
                is Signal.LED -> Pair(signal.x, signal.y)
                is Signal.Midi -> Pair(signal.x, signal.y)
                else -> return
            }

            if (down) {
                val signalOwner = Pair(this, "${coords.first},${coords.second}")

                Heaven.cancelJobs { job ->
                    job.owner is Pair<*, *> &&
                    job.owner.first == this &&
                    job.owner.second == "${coords.first},${coords.second}"
                }

                if (!state.value.onHold) {
                    // Non-hold mode: schedule all signals at once
                    for (i in 0..state.value.repeat - 1) {
                        val delay = i * (state.value.timing.toMsValue(bpm) * (state.value.gate * 2))

                        Heaven.schedule(delay.toDouble(), owner = signalOwner) {
                            signalExit?.invoke(listOf(signal))
                        }
                    }
                } else {
                    // Hold mode: send first signal immediately
                    signalExit?.invoke(listOf(signal))

                    // Then start recursive scheduling for subsequent signals
                    val baseDelay = state.value.timing.toMsValue(bpm) * (state.value.gate * 2)
                    scheduleSignals(signal, signalOwner, baseDelay.toDouble())
                }
            } else { // key up
                if (!state.value.onHold) {
                    return@forEach
                }

                // Cancel any ongoing loops for this key
                Heaven.cancelJobs { job ->
                    job.owner is Pair<*, *> &&
                    job.owner.first == this &&
                    job.owner.second == "${coords.first},${coords.second}"
                }
            }
        }
    }

    fun scheduleSignals(signal: Signal, signalOwner: Any, delay: Double) {
        Heaven.schedule(delay, owner = signalOwner) {
            if (state.value.onHold) { // if onHold is unchecked, we immediately stop looping, do we want this?
                signalExit?.invoke(listOf(signal))

                // Schedule the next iteration
                scheduleSignals(signal, signalOwner, delay)
            }
        }
    }
}

@Serializable
data class LoopChainDeviceState(
    val repeat: Int = 2,
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val gate: Float = 0.5f,
    val onHold: Boolean = false,
) : DeviceState()

fun Modifier.gesturesDisabled(disabled: Boolean = true) =
    if (disabled) {
        pointerInput(Unit) {
            awaitPointerEventScope {
                // we should wait for all new pointer events
                while (true) {
                    awaitPointerEvent(pass = PointerEventPass.Initial)
                        .changes
                        .forEach(PointerInputChange::consume)
                }
            }
        }
    } else {
        this
    }