package dev.anthonyhfm.amethyst.devices.effects.hold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class HoldChainDevice : ChainDevice<HoldChainDeviceState>() {
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
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row (
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeDial(
                        headline = "Hold",
                        timing = deviceState.timing,
                        onSelectTiming = { timing, msValue ->
                            state.update {
                                it.copy(
                                    timing = timing,
                                    delayMs = msValue
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.scale(0.9f)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = deviceState.infinite,
                        onCheckedChange = { checked ->
                            state.update {
                                it.copy(infinite = checked)
                            }
                        },
                    )

                    androidx.compose.material3.Text(
                        text = "Infinite",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    override fun midiEnter(n: List<Signal>) {
        n.forEach { signal ->
            if (signal.color != Color.Black) {
                val signalOwner = Pair(this, "${signal.x},${signal.y}")

                midiExit?.invoke(listOf(signal))

                if (state.value.infinite) {
                    return@forEach;
                }

                Heaven.schedule(state.value.delayMs.toDouble() * (state.value.gate * 2), owner = signalOwner) {
                    midiExit?.invoke(listOf(signal.copy(color = Color.Black)))
                }
            }
        }
    }
}

@Serializable
data class HoldChainDeviceState(
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val delayMs: Int = 0,
    val gate: Float = 0.5f, // 100% = 0.5f, 200% = 1.0f
    val infinite: Boolean = false,
) : DeviceState()