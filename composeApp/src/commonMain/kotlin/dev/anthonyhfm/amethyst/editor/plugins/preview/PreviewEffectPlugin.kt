package dev.anthonyhfm.amethyst.editor.plugins.preview

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.editor.plugins.EffectDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystPlugin
import dev.anthonyhfm.amethyst.ui.previewdevices.LaunchpadPro
import dev.anthonyhfm.amethyst.ui.previewdevices.PreviewState
import dev.anthonyhfm.amethyst.ui.previewdevices.rememberPreviewState

class PreviewEffectPlugin : EffectDevice() {
    private var previewState: PreviewState? = null

    @Composable
    override fun Content() {
        previewState = rememberPreviewState()

        AmethystPlugin(
            title = "Preview",
            modifier = Modifier
                .width(230.dp),
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