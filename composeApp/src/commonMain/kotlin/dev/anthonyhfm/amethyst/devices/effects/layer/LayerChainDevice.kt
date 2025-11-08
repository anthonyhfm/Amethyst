package dev.anthonyhfm.amethyst.devices.effects.layer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
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
                .width(100.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),

                contentAlignment = Alignment.Center
            ) {
                var beforeLayer = deviceState.copy().layer

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
                        beforeLayer = deviceState.copy().layer
                    },
                    onFinishValueChange = {
                        pushStateChange(
                            before = state.value.copy(layer = beforeLayer),
                            after = state.value.copy(layer = it)
                        )
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

    override fun ledSignalEnter(n: List<Signal.LED>) {
        signalExit?.invoke(
            n.map {
                it.copy(
                    layer = state.value.layer
                )
            }
        )
    }
}

@Serializable
data class LayerChainDeviceState(
    val layer: Int = 0,
) : DeviceState()