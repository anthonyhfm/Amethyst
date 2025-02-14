package dev.anthonyhfm.amethyst.devices.effects

import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.devices.BaseDevice

abstract class EffectDevice : BaseDevice<MidiEffectData> {
    var midiOutput: (MidiEffectData) -> Unit = { }

    @Composable
    abstract fun Content()
}