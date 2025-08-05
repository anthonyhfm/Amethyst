package dev.anthonyhfm.amethyst.devices.effects.layer_filter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.StepTextDial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class LayerFilterChainDevice : ChainDevice<LayerFilterChainDeviceState>() {
    override val state = MutableStateFlow(LayerFilterChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Layer Filter",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(120.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),

                contentAlignment = Alignment.Center
            ) {
                StepTextDial(
                    headline = "Layer Filter",
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
                    onValueChange = { value ->
                        state.update {
                            it.copy(layer = value)
                        }
                    }
                )
            }
        }
    }

    override fun midiEnter(n: List<Signal>) {
        midiExit?.invoke(
            n.filter {
                it.layer == state.value.layer
            }
        )
    }
}

@Serializable
data class LayerFilterChainDeviceState(
    val layer: Int = 0,
) : DeviceState()