package dev.anthonyhfm.amethyst.devices.effects.loop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
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

class LoopChainDevice : ChainDevice<LoopChainDeviceState>() {
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
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepTextDial(
                    headline = "Repeat",
                    text = "${deviceState.repeat}",
                    steps = IntArray(128) { it + 1 }.toList(),
                    value = deviceState.repeat,
                    onResolveTextValue = {
                        val repeatText = it.trim().toIntOrNull()

                        repeatText?.let { repeat ->
                            if (repeat in 1..128) {
                                state.update {
                                    it.copy(repeat = repeat)
                                }
                            }
                        }
                    },
                    onValueChange = { value ->
                        state.update {
                            it.copy(repeat = value)
                        }
                    },
                )

                VerticalDivider(
                    modifier = Modifier
                        .height(80.dp),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp)
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

    override fun midiEnter(n: List<Signal>) {
        val bpm = WorkspaceRepository.bpm.value

        n.forEach { signal ->
            if (signal.color != Color.Black) {
                // Create unique owner for this specific signal/button combination
                val signalOwner = Pair(this, "${signal.x},${signal.y}")

                // Cancel nur die Jobs für diesen spezifischen Button
                Heaven.cancelJobs { job ->
                    job.owner is Pair<*, *> &&
                    (job.owner as Pair<*, *>).first == this &&
                    (job.owner as Pair<*, *>).second == "${signal.x},${signal.y}"
                }

                for (i in 0..state.value.repeat - 1) {
                    val delay = i * (state.value.timing.toMsValue(bpm) * (state.value.gate * 2))

                    Heaven.schedule(delay.toDouble(), owner = signalOwner) {
                        midiExit?.invoke(listOf(signal))
                    }
                }
            }
        }
    }
}

@Serializable
data class LoopChainDeviceState(
    val repeat: Int = 2,
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val gate: Float = 0.5f
) : DeviceState()