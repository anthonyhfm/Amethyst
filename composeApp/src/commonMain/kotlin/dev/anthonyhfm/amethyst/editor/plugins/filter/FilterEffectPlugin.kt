package dev.anthonyhfm.amethyst.editor.plugins.filter

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.editor.plugins.EffectPlugin
import dev.anthonyhfm.amethyst.ui.components.AmethystPlugin
import dev.anthonyhfm.amethyst.ui.previewdevices.LaunchpadPro
import dev.anthonyhfm.amethyst.ui.previewdevices.PreviewState
import dev.anthonyhfm.amethyst.ui.previewdevices.rememberPreviewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class FilterEffectPlugin : EffectPlugin() {
    override var isEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    private var previewState: PreviewState? = null

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()

        previewState = rememberPreviewState()

        AmethystPlugin(
            title = "Filter",
            enabled = isEnabled.collectAsState().value,
            modifier = Modifier
                .width(230.dp),
            onChangeEnabled = {
                scope.launch {
                    isEnabled.emit(it)
                }
            }
        ) {
            previewState?.let {
                LaunchpadPro(
                    previewState = it,
                    onClick = { x, y ->

                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight(0.9f)
                )
            }
        }
    }

    override suspend fun passData(data: MidiEffectData) {
        previewState?.sendToPreview(data = data)

        midiOutput(data)
    }
}