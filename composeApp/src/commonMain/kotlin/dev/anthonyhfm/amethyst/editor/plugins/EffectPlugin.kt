package dev.anthonyhfm.amethyst.editor.plugins

import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import kotlinx.coroutines.flow.MutableStateFlow

abstract class EffectPlugin : BasePlugin<MidiEffectData> {
    var midiOutput: (MidiEffectData) -> Unit = { }

    @Composable
    abstract fun Content()
}