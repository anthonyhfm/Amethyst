package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.devices.DeviceState

class PagesAdapter(
    private val blob: String
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        return listOf()
    }
}