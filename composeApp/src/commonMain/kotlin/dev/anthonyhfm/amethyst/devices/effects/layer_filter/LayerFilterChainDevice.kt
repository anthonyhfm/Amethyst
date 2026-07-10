package dev.anthonyhfm.amethyst.devices.effects.layer_filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class LayerFilterChainDevice : LEDChainDevice<LayerFilterChainDeviceState>() {
    override val state = MutableStateFlow(LayerFilterChainDeviceState())
    override val helpRef = "LayerFilter"

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Layer Filter",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(160.dp),
            titleBarModifier = LocalTitleBarModifier.current
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
                    Dial(
                        title = "Target",
                        value = deviceState.layer,
                        type = DialType.Steps(IntArray(41) { -20 + it }.toList()),
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
                                before = beforeState,
                                after = state.value
                            )
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(layer = value)
                            }
                        },
                    )

                    Box(modifier = Modifier.fillMaxHeight(0.5f)) {
                        Separator(orientation = SeparatorOrientation.Vertical)
                    }

                    Dial(
                        title = "Range",
                        value = deviceState.range,
                        type = DialType.Steps(IntArray(21) { it }.toList()),
                        text = "${deviceState.range}",
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
                                before = beforeState,
                                after = state.value
                            )
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(range = value)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val target = state.value.layer
        val range = state.value.range

        signalExit?.invoke(
            n.filter {
                if (range == 0) {
                    it.layer == target
                } else {
                    kotlin.math.abs(it.layer - target) <= range
                }
            }
        )
    }

    companion object : ChainDeviceFactory<LayerFilterChainDeviceState> {
        override val stateClass = LayerFilterChainDeviceState::class
        override val serializer = LayerFilterChainDeviceState.serializer()
        override fun create() = LayerFilterChainDevice()
    }
}

@Serializable
data class LayerFilterChainDeviceState(
    val layer: Int = 0,
    val range: Int = 0
) : DeviceState()
