package dev.anthonyhfm.amethyst.ui.previewdevices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import kotlinx.coroutines.flow.asStateFlow

class PreviewState { // TODO: Replace with Heaven's screen-class
    val grid: MutableState<List<MidiEffectData>> = mutableStateOf(
        List(100) {
            MidiEffectData(
                x = it % 10,
                y = it / 10,
                r = 0,
                g = 0,
                b = 0
            )
        }
    )

    fun sendToPreview(data: MidiEffectData) {
        grid.value = grid.value.toMutableList().apply {
            this[data.x + data.y * 10] = data
        }
    }
}

@Composable
fun rememberPreviewState(): PreviewState {
    val state = remember {
        PreviewState()
    }

    return state
}