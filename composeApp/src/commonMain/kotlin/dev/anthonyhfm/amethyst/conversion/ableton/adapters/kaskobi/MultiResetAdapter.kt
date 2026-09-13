package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.clear.ClearChainDeviceState

class MultiResetAdapter(private val device: MxDevice) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        return listOf(ClearChainDeviceState(
            clearLights = false,
            clearAudio = false,
            clearMulti = true,
        ))
    }
}
