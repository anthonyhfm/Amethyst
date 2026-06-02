package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.clear.ClearChainDeviceState

class ApolloClearAdapter(
    model: ApolloModel.Device.Clear
) : ApolloAdapter<ApolloModel.Device.Clear>(model) {
    override fun toDeviceState(): DeviceState {
        return when (model.mode) {
            0    -> ClearChainDeviceState(clearLights = true,  clearAudio = false, clearMulti = false)
            1    -> ClearChainDeviceState(clearLights = false, clearAudio = false, clearMulti = true)
            else -> ClearChainDeviceState(clearLights = true,  clearAudio = true,  clearMulti = true)
        }
    }
}
