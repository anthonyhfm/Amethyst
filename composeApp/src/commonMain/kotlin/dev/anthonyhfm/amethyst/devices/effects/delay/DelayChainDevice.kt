package dev.anthonyhfm.amethyst.devices.effects.delay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.selection.SelectionManager
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
import kotlin.time.Duration.Companion.milliseconds

class DelayChainDevice : ChainDevice<DelayChainDeviceState>() {
    override val state = MutableStateFlow(DelayChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Delay",
            isSelected = selections.contains(this),
            modifier = Modifier
                .width(100.dp)
        ) {
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

    override fun midiEnter(n: List<Signal>) {
        Heaven.schedule(
            job = {
                midiExit?.invoke(n)
            },
            delayInMs = state.value.delayMs.toDouble() * (state.value.gate * 2)
        )
    }
}

@Serializable
data class DelayChainDeviceState(
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val delayMs: Int = 0,
    val gate: Float = 0.5f, // 100% = 0.5f, 200% = 1.0f
) : DeviceState()