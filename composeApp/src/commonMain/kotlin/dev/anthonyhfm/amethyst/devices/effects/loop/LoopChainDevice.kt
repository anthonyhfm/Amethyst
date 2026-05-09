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
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.StepTextDial
import dev.anthonyhfm.amethyst.ui.components.primitives.TextDial
import dev.anthonyhfm.amethyst.ui.components.primitives.TimeDial
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class LoopChainDevice : GenericChainDevice<LoopChainDeviceState>(), Chokeable {
    private val activeHoldKeys = mutableSetOf<String>()
    override val state = MutableStateFlow(LoopChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        ChainDeviceShell(
            title = "Loop",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(200.dp),
            titleBarModifier = LocalTitleBarModifier.current
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
                        var beforeRepeat = deviceState.repeat
                        StepTextDial(
                            headline = "Repeat",
                            text = if (deviceState.onHold) "Unused" else "${deviceState.repeat}",
                            steps = IntArray(128) { it + 1 }.toList(),
                            value = deviceState.repeat,
                            onStartValueChange = { v ->
                                beforeRepeat = v
                            },
                            onFinishValueChange = { v ->
                                pushStateChange(
                                    before = state.value.copy(repeat = beforeRepeat),
                                    after = state.value.copy(repeat = v)
                                )
                            },
                            onResolveTextValue = {
                                if (!deviceState.onHold) {
                                    val repeatText = it.trim().toIntOrNull()

                                    repeatText?.let { repeat ->
                                        if (repeat in 1..128) {
                                            val before = state.value
                                            state.update { it.copy(repeat = repeat) }
                                            pushStateChange(before, state.value)
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
                            enabled = !deviceState.onHold
                        )
                    }

                    Column (
                        horizontalAlignment = Alignment.Start,
                    ){
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Checkbox(
                                checked = deviceState.onHold,
                                onCheckedChange = { checked ->
                                    val before = state.value
                                    state.update { it.copy(onHold = checked) }
                                    pushStateChange(before, state.value)
                                },
                            )

                            Text(
                                text = "Hold",
                                style = Theme[typography][small],
                                color = Theme[colors][foreground],
                            )
                        }
                    }
                }

                Separator(
                    modifier = Modifier.height(160.dp),
                    orientation = SeparatorOrientation.Vertical,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    var beforeTiming: Pair<Timing, Long> = Pair(deviceState.timing, deviceState.timing.toMsValue(WorkspaceRepository.bpm.value))
                    TimeDial(
                        headline = "Delay",
                        timing = deviceState.timing,
                        onStartValueChange = { t, ms ->
                            beforeTiming = Pair(t, ms)
                        },
                        onSelectTiming = { timing, _ ->
                            state.update {
                                it.copy(
                                    timing = timing,
                                )
                            }
                        },
                        onFinishValueChange = { t, _ ->
                            pushStateChange(
                                before = state.value.copy(timing = beforeTiming.first),
                                after = state.value.copy(timing = t)
                            )
                        }
                    )

                    var beforeGate = deviceState.gate
                    TextDial(
                        headline = "Gate",
                        text = "${(deviceState.gate * 200).roundToInt()}%",
                        value = deviceState.gate,
                        onStartValueChange = { v ->
                            beforeGate = v
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(gate = value)
                            }
                        },
                        onFinishValueChange = { v ->
                            pushStateChange(
                                before = state.value.copy(gate = beforeGate),
                                after = state.value.copy(gate = v)
                            )
                        },
                        onResolveTextValue = {
                            val gateText = it.removeSuffix("%").trim().toIntOrNull()

                            gateText?.let { gate ->
                                if (gate in 0..200) {
                                    val before = state.value
                                    state.update {
                                        it.copy(gate = gate / 200f) // Convert to float between 0.0 and 1.0
                                    }
                                    pushStateChange(before, state.value)
                                }
                            }
                        },
                        modifier = Modifier
                            .rightClickable {
                                val before = state.value
                                state.update {
                                    it.copy(gate = 0.5f) // Reset gate to its original state
                                }
                                pushStateChange(before, state.value)
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
                else -> return@forEach
            }

            val coords: Pair<Int, Int> = when (signal) {
                is Signal.LED -> Pair(signal.x, signal.y)
                is Signal.Midi -> Pair(signal.x, signal.y)
                else -> return@forEach
            }

            if (down) {
                val signalOwner = Pair(this, "${coords.first},${coords.second}")
                val ownerKey = signalOwner.second as String

                Heaven.cancelJobs { job ->
                    job.owner is Pair<*, *> &&
                    job.owner.first == this &&
                    job.owner.second == ownerKey
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
                    // Hold mode: mark key active, send first signal immediately
                    activeHoldKeys.add(ownerKey)
                    signalExit?.invoke(listOf(signal))

                    // Then start recursive scheduling for subsequent signals
                    val baseDelay = state.value.timing.toMsValue(bpm) * (state.value.gate * 2)
                    scheduleSignals(signal, signalOwner, baseDelay.toDouble())
                }
            } else { // key up
                if (!state.value.onHold) {
                    return@forEach
                }

                val signalOwner = Pair(this, "${coords.first},${coords.second}")
                val ownerKey = signalOwner.second as String
                activeHoldKeys.remove(ownerKey)

                // Emit a final off to stop the held loop cleanly
                signalExit?.invoke(
                    listOf(
                        when (signal) {
                            is Signal.LED -> signal.copy(color = Color.Black)
                            is Signal.Midi -> signal.copy(velocity = 0)
                            else -> signal
                        }
                    )
                )

                // Cancel any ongoing loops for this key
                Heaven.cancelJobs { job ->
                    job.owner is Pair<*, *> &&
                    job.owner.first == this &&
                    job.owner.second == ownerKey
                }
            }
        }
    }

    fun scheduleSignals(signal: Signal, signalOwner: Any, delay: Double) {
        val ownerKey = (signalOwner as? Pair<*, *>)?.second as? String ?: return

        Heaven.schedule(delay, owner = signalOwner) {
            if (!activeHoldKeys.contains(ownerKey) || !state.value.onHold) {
                return@schedule
            }

            signalExit?.invoke(listOf(signal))

            // Schedule the next iteration
            scheduleSignals(signal, signalOwner, delay)
        }
    }

    override fun onChoke() {
        // Cancel all scheduled Heaven tasks owned by this device
        // The loop device uses Pair(this, "${coords.first},${coords.second}") as owner
        Heaven.cancelJobs { job ->
            job.owner is Pair<*, *> && job.owner.first == this
        }
    }

    companion object : ChainDeviceFactory<LoopChainDeviceState> {
        override val stateClass = LoopChainDeviceState::class
        override val serializer = LoopChainDeviceState.serializer()
        override fun create() = LoopChainDevice()
    }
}

@Serializable
data class LoopChainDeviceState(
    val repeat: Int = 2,
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val gate: Float = 0.5f,
    val onHold: Boolean = false,
) : DeviceState()
