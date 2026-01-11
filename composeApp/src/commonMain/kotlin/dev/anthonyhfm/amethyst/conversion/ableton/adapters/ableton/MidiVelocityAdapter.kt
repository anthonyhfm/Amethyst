package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiVelocity
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState

class MidiVelocityAdapter(
    private val device: MidiVelocity
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val velocity = device.maxOut.manual.value
        val palette = AbletonConverter.palette

        return listOf(
            ColorChainDeviceState(
                r = palette[velocity].first / 63f,
                g = palette[velocity].second / 63f,
                b = palette[velocity].third / 63f,
            )
        )
    }
}