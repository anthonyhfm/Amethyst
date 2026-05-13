package dev.anthonyhfm.amethyst.conversion.ableton.adapters.dovitate

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.devices.DeviceState

class CycleLightsAdapter(
    val device: MxDeviceMidiEffect
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {


        return listOf()
    }
}