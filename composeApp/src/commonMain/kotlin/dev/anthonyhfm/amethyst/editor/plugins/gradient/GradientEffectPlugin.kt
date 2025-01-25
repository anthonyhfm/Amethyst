package dev.anthonyhfm.amethyst.editor.plugins.gradient

import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.editor.plugins.EffectPlugin
import kotlinx.coroutines.flow.MutableStateFlow

class GradientEffectPlugin : EffectPlugin() {
    override var isEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    @Composable
    override fun Content() {

    }

    override suspend fun passData(data: MidiEffectData) {

    }
}