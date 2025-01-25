package dev.anthonyhfm.amethyst.editor.plugins.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.editor.plugins.EffectPlugin
import dev.anthonyhfm.amethyst.ui.previewdevices.LaunchpadPro
import dev.anthonyhfm.amethyst.ui.previewdevices.PreviewState
import dev.anthonyhfm.amethyst.ui.previewdevices.rememberPreviewState
import kotlinx.coroutines.flow.MutableStateFlow

class PreviewEffectPlugin : EffectPlugin() {
    override var isEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    private var previewState: PreviewState? = null

    @Composable
    override fun Content() {
        previewState = rememberPreviewState()

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .fillMaxHeight()
                .width(230.dp)
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp), RoundedCornerShape(6.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            ) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.labelLarge,
                    lineHeight = MaterialTheme.typography.labelLarge.fontSize,
                    modifier = Modifier
                        .align(Alignment.Center)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),

                contentAlignment = Alignment.Center
            ) {
                previewState?.let {
                    LaunchpadPro(
                        previewState = it,
                        modifier = Modifier
                            .fillMaxHeight(0.9f)
                    )
                }
            }
        }
    }

    override suspend fun passData(data: MidiEffectData) {
        previewState?.sendToPreview(data = data)

        midiOutput(data)
    }
}