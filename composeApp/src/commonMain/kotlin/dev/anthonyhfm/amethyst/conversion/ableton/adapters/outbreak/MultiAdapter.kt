package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.AbletonMultisamplingAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.devices.DeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MultiAdapter(
    private val device: MxDeviceMidiEffect,
    private val midiContainer: MidiEffectGroupDevice?,
    private val instrumentContainer: InstrumentGroupDevice?,
    private val drumContainer: DrumGroupDevice?,
    private val offset: IntOffset,
    private val outputOffset: IntOffset,
    private val chainDepth: Int,
    private val inputNote: Int? = null,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data = jsonDecoder.decodeFromString<MultiData>(string = device.decodeBlob())
        val steps = data.steps.firstOrNull()?.takeIf { it.isFinite() }?.toInt() ?: return emptyList()
        return AbletonMultisamplingAdapter(
            midiContainer = midiContainer,
            instrumentContainer = instrumentContainer,
            drumContainer = drumContainer,
            steps = if (device.on.manual.value) {
                steps
            } else {
                1
            },
            noteMode = !device.on.manual.value || data.mode.firstOrNull() != 1.0,
            inputNote = inputNote,
            offset = offset,
            outputOffset = outputOffset,
            chainDepth = chainDepth,
        ).toDeviceStates()
    }

    @Serializable
    data class MultiData(
        @SerialName("live.numbox")
        val steps: List<Double>,
        @SerialName("live.text")
        val mode: List<Double> = emptyList(),
    )
}
