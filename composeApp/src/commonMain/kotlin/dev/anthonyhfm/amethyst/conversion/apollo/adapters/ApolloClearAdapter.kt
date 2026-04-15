package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.clear.ClearChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.clear.ClearMode

class ApolloClearAdapter(
    model: ApolloModel.Device.Clear
) : ApolloAdapter<ApolloModel.Device.Clear>(model) {
    override fun toDeviceState(): DeviceState {
        return ClearChainDeviceState(
            mode = when (model.mode) {
                0 -> ClearMode.Lights
                1 -> ClearMode.Multi
                else -> ClearMode.Both
            }
        )
    }
}
