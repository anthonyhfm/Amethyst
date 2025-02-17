package dev.anthonyhfm.amethyst.devices.effects.filter

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.EffectDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystPlugin
import dev.anthonyhfm.amethyst.ui.previewdevices.LaunchpadPro
import dev.anthonyhfm.amethyst.ui.previewdevices.rememberPreviewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class FilterEffectDevice : EffectDevice<FilterEffectDeviceState>() {
    override val state = MutableStateFlow(FilterEffectDeviceState())

    @Composable
    override fun Content() {
        val previewState = rememberPreviewState()
        val deviceState by state.collectAsState()

        LaunchedEffect(deviceState.filterData) { // Loads the saved filterData into a new previewState when recomposed
            deviceState.filterData.forEachIndexed { x, data ->
                data.forEachIndexed { y, enabled ->
                    previewState.sendToPreview(
                        data = MidiEffectData(
                            x = x,
                            y = y,
                            r = if (enabled) 20 else 0,
                            g = if (enabled) 20 else 0,
                            b = if (enabled) 63 else 0,
                        )
                    )
                }
            }
        }

        AmethystPlugin(
            title = "Filter",
            modifier = Modifier
                .width(230.dp),
        ) {
            LaunchpadPro(
                previewState = previewState,
                onClick = { x, y ->
                    state.update {
                        it.copy(
                            filterData = it.filterData.mapIndexed { x_index, y_list ->
                                y_list.mapIndexed { y_index, enabled ->
                                    if (x_index == x && y_index == y) {
                                        !enabled
                                    } else {
                                        enabled
                                    }
                                }
                            }
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight(0.9f)
            )
        }
    }

    override suspend fun passData(data: MidiEffectData) {
        if (state.value.filterData[data.x][data.y]) {
            midiOutput(data)
        }
    }
}

@Serializable
data class FilterEffectDeviceState(
    val filterData: List<List<Boolean>> = List(
        size = 10,
        init = {
            List(
                size = 10,
                init = {
                    false
                }
            )
        }
    )
) : DeviceState()