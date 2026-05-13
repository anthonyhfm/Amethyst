package dev.anthonyhfm.amethyst.conversion.ableton.adapters.dovitate

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.devices.DeviceState

class CycleLightsAdapter(
    val device: MxDevice
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {


        return listOf()
    }
}