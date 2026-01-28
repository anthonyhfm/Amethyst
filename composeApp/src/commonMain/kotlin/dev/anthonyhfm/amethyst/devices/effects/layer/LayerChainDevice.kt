package dev.anthonyhfm.amethyst.devices.effects.layer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.DropdownSelect
import dev.anthonyhfm.amethyst.ui.components.StepTextDial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class LayerChainDevice : LEDChainDevice<LayerChainDeviceState>() {
    override val state = MutableStateFlow(LayerChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Layer",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(160.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var beforeState = deviceState.copy()

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepTextDial(
                        headline = "Layer",
                        value = deviceState.layer,
                        steps = IntArray(41) { -20 + it }.toList(),
                        text = "${deviceState.layer}",
                        onResolveTextValue = {
                            val layerText = it.trim().toIntOrNull()

                            layerText?.let { layer ->
                                if (layer in -20..20) {
                                    state.update {
                                        it.copy(layer = layer)
                                    }
                                }
                            }
                        },
                        onStartValueChange = {
                            beforeState = state.value.copy()
                        },
                        onFinishValueChange = {
                            pushStateChange(
                                before = beforeState.copy(layer = beforeState.layer),
                                after = state.value.copy(layer = it)
                            )
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(layer = value)
                            }
                        },
                    )

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(0.5f)
                    )

                    StepTextDial(
                        headline = "Range",
                        value = deviceState.range,
                        steps = IntArray(21) { it }.toList(),
                        text = "${deviceState.range}",
                        enabled = deviceState.mode != Signal.LED.BlendingMode.Normal,
                        onResolveTextValue = {
                            val rangeText = it.trim().toIntOrNull()
                            rangeText?.let { range ->
                                if (range in 0..20) {
                                    state.update { it.copy(range = range) }
                                }
                            }
                        },
                        onStartValueChange = {
                            beforeState = state.value.copy()
                        },
                        onFinishValueChange = {
                            pushStateChange(
                                before = beforeState.copy(range = beforeState.range),
                                after = state.value.copy(range = it)
                            )
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(range = value)
                            }
                        },
                    )
                }

                DropdownSelect(
                    label = "Mode",
                    options = Signal.LED.BlendingMode.entries,
                    selectedOption = deviceState.mode,
                    onOptionSelected = { mode ->
                        val before = state.value.copy()
                        state.update { it.copy(mode = mode) }
                        pushStateChange(before, state.value)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        signalExit?.invoke(
            n.map {
                it.copy(
                    layer = state.value.layer,
                    blendingMode = state.value.mode,
                    blendingRange = state.value.range
                )
            }
        )
    }
}

@Serializable
data class LayerChainDeviceState(
    val layer: Int = 0,
    val mode: Signal.LED.BlendingMode = Signal.LED.BlendingMode.Normal,
    val range: Int = 1
) : DeviceState()