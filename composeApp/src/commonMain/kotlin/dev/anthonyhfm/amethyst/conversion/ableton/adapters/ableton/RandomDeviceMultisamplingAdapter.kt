package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiRandom
import dev.anthonyhfm.amethyst.devices.DeviceState

class RandomDeviceMultisamplingAdapter(
    private val random: MidiRandom,
    private val midiContainer: MidiEffectGroupDevice?,
    private val instrumentContainer: InstrumentGroupDevice?,
    private val drumContainer: DrumGroupDevice?,
    private val inputNote: Int? = null,
    private val offset: IntOffset = IntOffset.Zero,
    private val outputOffset: IntOffset = IntOffset.Zero,
    private val chainDepth: Int = 0,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        if (random.chance.manual.value != 1.0 || !random.alternate.manual.value) {
            return emptyList()
        }
        val steps = random.choices.manual.value.takeIf { it.isFinite() }?.toInt() ?: return emptyList()
        val direction = if (random.sign.manual.value == 1) {
            -1
        } else {
            1
        }
        val scale = random.scale.manual.value.takeIf { it.isFinite() }?.toInt() ?: return emptyList()
        return AbletonMultisamplingAdapter(
            midiContainer = midiContainer,
            instrumentContainer = instrumentContainer,
            drumContainer = drumContainer,
            steps = if (random.on.manual.value) {
                steps
            } else {
                1
            },
            noteMode = true,
            inputNote = inputNote,
            stepPitch = scale * direction,
            offset = offset,
            outputOffset = outputOffset,
            chainDepth = chainDepth,
        ).toDeviceStates()
    }
}
