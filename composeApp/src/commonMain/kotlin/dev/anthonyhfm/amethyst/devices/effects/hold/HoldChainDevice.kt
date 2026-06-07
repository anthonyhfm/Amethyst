package dev.anthonyhfm.amethyst.devices.effects.hold

import androidx.compose.foundation.layout.*
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
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
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.TextDial
import dev.anthonyhfm.amethyst.ui.components.primitives.TimeDial
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class HoldChainDevice : GenericChainDevice<HoldChainDeviceState>(), Chokeable {
    override val state = MutableStateFlow(HoldChainDeviceState())
    override val helpRef = "Hold"

    private val activeJobOwners = mutableSetOf<Any>()
    private val isDown = mutableSetOf<Any>()

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        ChainDeviceShell(
            title = "Hold",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier
                .width(160.dp),
            titleBarModifier = LocalTitleBarModifier.current
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
                            text = if (deviceState.mode == HoldMode.Infinite) "Infinite" else null,
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
                            enabled = deviceState.mode != HoldMode.Infinite
                        )

                        var beforeGate = deviceState.copy().gate
                        TextDial(
                            headline = "Gate",
                            text = if (deviceState.mode != HoldMode.Infinite) "${(deviceState.gate * 200).toInt()}%" else "Disabled",
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
                            enabled = deviceState.mode != HoldMode.Infinite
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        ) {
                            ModeSelectField(
                                selectedMode = deviceState.mode,
                                onModeSelected = { mode ->
                                    pushStateChange(
                                        before = deviceState,
                                        after = deviceState.copy(mode = mode)
                                    )
                                    state.update { it.copy(mode = mode) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                style = Theme[typography][small],
                                color = Theme[colors][foreground],
                            )
                        }
                    }

                }
            }
        }
    }

    @Composable
    private fun ModeSelectField(
        selectedMode: HoldMode,
        onModeSelected: (HoldMode) -> Unit,
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
                value = selectedMode.name,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                shape = SmallShape,
                triggerHeight = 24.dp,
                triggerContentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                HoldMode.entries.forEach { mode ->
                    SelectItem(
                        text = mode.name,
                        selected = mode == selectedMode,
                        onClick = { onModeSelected(mode) },
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

            val baseDelayMs = state.value.timing.toMsValue(bpm).toDouble() * (state.value.gate * 2)

            val updateSchedule: () -> Unit = {
                Heaven.cancelJobs { job ->
                    job.owner == signalOwner
                }

                activeJobOwners.add(signalOwner)
                Heaven.schedule(baseDelayMs, signalOwner) {
                    if (state.value.mode == HoldMode.Minimum && isDown.contains(signalOwner)) {
                        activeJobOwners.remove(signalOwner)
                        return@schedule
                    }

                    activeJobOwners.remove(signalOwner)
                    if (signal is Signal.LED) {
                        signalExit?.invoke(listOf(signal.copy(color = Color.Black)))
                    } else if (signal is Signal.Midi) {
                        signalExit?.invoke(listOf(signal.copy(velocity = 0)))
                    }
                }
             }

             if (down) {
                 isDown.add(signalOwner)
                 if (state.value.onRelease) {
                     return@forEach
                 }

                signalExit?.invoke(listOf(signal))

                if (state.value.mode == HoldMode.Infinite) {
                     return@forEach
                 }

                updateSchedule()
             } else {
                 isDown.remove(signalOwner)
                 if (!state.value.onRelease) {
                     if (state.value.mode == HoldMode.Minimum) {
                         // In minimum mode, if the key is released, we might need to release the signal
                         // if the minimum duration has already passed.
                         // But the current implementation uses Heaven.schedule which will handle the release.
                         // If the key is released BEFORE baseDelayMs, the scheduled job will handle it.
                         // If the key is released AFTER baseDelayMs, the job already fired and released it.
                         // Wait, if it's Minimum mode and key is released after baseDelayMs, 
                         // we should release it immediately.
                         
                         val isScheduled = activeJobOwners.contains(signalOwner)
                         if (!isScheduled) {
                             if (signal is Signal.LED) {
                                 signalExit?.invoke(listOf(signal.copy(color = Color.Black)))
                             } else if (signal is Signal.Midi) {
                                 signalExit?.invoke(listOf(signal.copy(velocity = 0)))
                             }
                         }
                     }
                     return@forEach
                 }

                Heaven.schedule(0.0) {
                    if (signal is Signal.LED) {
                        signalExit?.invoke(listOf(signal.copy(color = Color.White)))
                    } else if (signal is Signal.Midi) {
                        signalExit?.invoke(listOf(signal.copy(velocity = 127)))
                    }
                }

                if (state.value.mode == HoldMode.Infinite) {
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

    companion object : ChainDeviceFactory<HoldChainDeviceState> {
        override val stateClass = HoldChainDeviceState::class
        override val serializer = HoldChainDeviceState.serializer()
        override fun create() = HoldChainDevice()
    }
}

@Serializable
enum class HoldMode {
    Trigger,
    Minimum,
    Infinite
}

@Serializable
data class HoldChainDeviceState(
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val delayMs: Long = 0,
    val gate: Float = 0.5f, // 100% = 0.5f, 200% = 1.0f
    val mode: HoldMode = HoldMode.Trigger,
    val onRelease: Boolean = false,
) : DeviceState()
