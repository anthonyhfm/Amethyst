package dev.anthonyhfm.amethyst.ui.previewdevices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import kotlinx.coroutines.flow.asStateFlow

class PreviewState {
    private val _grid: MutableStateFlow<MutableList<MutableList<MidiEffectData>>> = MutableStateFlow(
        value = MutableList(
            size = 10,
            init = { x ->
                MutableList(
                    size = 10,
                    init = { y ->
                        MidiEffectData(
                            x = x,
                            y = y,
                            r = 0,
                            g = 0,
                            b = 0
                        )
                    }
                )
            }
        )
    )

    val grid: StateFlow<List<List<MidiEffectData>>> = _grid.asStateFlow()

    suspend fun sendToPreview(data: MidiEffectData) {
        // This probably pulls performance like a bitch
        _grid.emit(
            value = _grid.value.mapIndexed { x, y_data ->
                if (data.x == x) {
                    y_data.mapIndexed { y, effectData ->
                        if (y == data.y) {
                            data
                        } else {
                            effectData
                        }
                    }.toMutableList()
                } else {
                    y_data
                }
            }.toMutableList()
        )
    }
}

@Composable
fun rememberPreviewState(): PreviewState {
    val state = remember {
        PreviewState()
    }

    return state
}