package dev.anthonyhfm.amethyst.devices.effects.flip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystCheckbox
import dev.anthonyhfm.amethyst.ui.components.DropdownSelect
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class FlipChainDevice : LEDChainDevice<FlipChainDeviceState>() {
    override val state = MutableStateFlow(FlipChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Flip",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(140.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DropdownSelect(
                    label = "Mode",
                    options = FlipChainDeviceState.FlipMode.entries,
                    selectedOption = deviceState.mode,
                    onOptionSelected = { mode ->
                        val before = state.value
                        state.update { it.copy(mode = mode) }
                        pushStateChange(before, state.value)
                    },
                    optionToString = {
                        when (it) {
                            FlipChainDeviceState.FlipMode.HORIZONTAL -> "Horizontal"
                            FlipChainDeviceState.FlipMode.VERTICAL -> "Vertical"
                            FlipChainDeviceState.FlipMode.DIAGONAL_PLUS -> "Diagonal+"
                            FlipChainDeviceState.FlipMode.DIAGONAL_MINUS -> "Diagonal-"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmethystCheckbox(
                        checked = deviceState.bypass,
                        onCheckedChange = { checked ->
                            val before = state.value
                            state.update {
                                it.copy(bypass = checked)
                            }

                            pushStateChange(before, state.value)
                        },
                    )

                    Text(
                        text = "Bypass",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val signals = mutableListOf<Signal.LED>()

        if (state.value.bypass) {
            signals.addAll(n.map { it.copy() })
        }

        signals.addAll(n.flatMap { signal ->
            if (signal.x + signal.y * 10 == 100) {
                return@flatMap if (state.value.bypass) emptyList() else listOf(signal.copy())
            }

            var x = signal.x
            var y = signal.y

            when (state.value.mode) {
                FlipChainDeviceState.FlipMode.HORIZONTAL -> x = 9 - x
                FlipChainDeviceState.FlipMode.VERTICAL -> y = 9 - y
                FlipChainDeviceState.FlipMode.DIAGONAL_PLUS -> {
                    val temp = x
                    x = y
                    y = temp
                }
                FlipChainDeviceState.FlipMode.DIAGONAL_MINUS -> {
                    x = 9 - x
                    y = 9 - y
                    val temp = x
                    x = y
                    y = temp
                }
            }

            listOf(signal.copy(x = x, y = y))
        })

        signalExit?.invoke(signals)
    }
}

@Serializable
data class FlipChainDeviceState(
    val bypass: Boolean = false,
    val mode: FlipMode = FlipMode.HORIZONTAL,
) : DeviceState() {
    enum class FlipMode {
        HORIZONTAL,
        VERTICAL,
        DIAGONAL_PLUS,
        DIAGONAL_MINUS,
    }
}