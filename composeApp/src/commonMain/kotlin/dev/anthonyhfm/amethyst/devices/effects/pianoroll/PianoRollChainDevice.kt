package dev.anthonyhfm.amethyst.devices.effects.pianoroll

import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

class PianoRollChainDevice : LEDChainDevice<PianoRollChainDeviceState>() {
    override val state = MutableStateFlow(PianoRollChainDeviceState())

    @Composable
    override fun Content() {

    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        state.value.midiEntry.notes.forEach {

        }
    }
}

@Serializable
data class PianoRollChainDeviceState(
    val midiEntry: MidiEntry = MidiEntry(
        startTimeMs = 0,
        durationMs = 0,
    )
) : DeviceState()