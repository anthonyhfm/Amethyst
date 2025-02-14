package dev.anthonyhfm.amethyst.devices.effects.filter

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.devices.effects.EffectDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystPlugin
import dev.anthonyhfm.amethyst.ui.previewdevices.LaunchpadPro
import dev.anthonyhfm.amethyst.ui.previewdevices.rememberPreviewState
import kotlinx.coroutines.launch

class FilterEffectDevice : EffectDevice() {
    private val filterData: MutableList<MutableList<Boolean>> = MutableList(
        size = 10,
        init = {
            MutableList(
                size = 10,
                init = {
                    false
                }
            )
        }
    )

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val previewState = rememberPreviewState()

        LaunchedEffect(Unit) { // Loads the saved filterData into a new previewState when recomposed
            filterData.forEachIndexed { x, data ->
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
                    scope.launch {
                        if (filterData[x][y]) {
                            previewState.sendToPreview(
                                data = MidiEffectData(
                                    x = x,
                                    y = y,
                                    r = 0,
                                    g = 0,
                                    b = 0,
                                )
                            )
                        } else {
                            previewState.sendToPreview(
                                data = MidiEffectData(
                                    x = x,
                                    y = y,
                                    r = 20,
                                    g = 20,
                                    b = 63,
                                )
                            )
                        }

                        filterData[x][y] = !filterData[x][y]
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight(0.9f)
            )
        }
    }

    override suspend fun passData(data: MidiEffectData) {
        if (filterData[data.x][data.y]) {
            midiOutput(data)
        }
    }
}