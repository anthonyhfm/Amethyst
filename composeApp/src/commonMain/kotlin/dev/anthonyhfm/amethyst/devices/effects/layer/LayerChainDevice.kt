package dev.anthonyhfm.amethyst.devices.effects.layer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.StepTextDial
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class LayerChainDevice : LEDChainDevice<LayerChainDeviceState>() {
    override val state = MutableStateFlow(LayerChainDeviceState())
    override val helpRef: String = "layer"

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Layer",
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

                    Box(modifier = Modifier.fillMaxHeight(0.5f)) {
                        Separator(orientation = SeparatorOrientation.Vertical)
                    }

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

                BlendingModeSelectField(
                    selectedMode = deviceState.mode,
                    onModeSelected = { mode ->
                        val before = state.value.copy()
                        state.update { it.copy(mode = mode) }
                        pushStateChange(before, state.value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Composable
    private fun BlendingModeSelectField(
        selectedMode: Signal.LED.BlendingMode,
        onModeSelected: (Signal.LED.BlendingMode) -> Unit,
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
                Signal.LED.BlendingMode.entries.forEach { mode ->
                    SelectItem(
                        text = mode.name,
                        selected = mode == selectedMode,
                        onClick = { onModeSelected(mode) },
                    )
                }
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
