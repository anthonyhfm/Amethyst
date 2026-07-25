package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiArpeggiator
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiChord
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiPitcher
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.ableton.AbletonPitcherChainDeviceState

class MidiPitcherAdapter(
    private val device: MidiPitcher
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        return listOf(
            AbletonPitcherChainDeviceState(
                pitch = device.pitch.manual.value
            )
        ).withMuteState(device.on.manual.value)
    }
}