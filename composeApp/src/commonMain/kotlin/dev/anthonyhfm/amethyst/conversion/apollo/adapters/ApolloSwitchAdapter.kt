package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState

class ApolloSwitchAdapter(
    model: ApolloModel.Device.Switch
) : ApolloAdapter<ApolloModel.Device.Switch>(model) {
    override fun toDeviceState(): DeviceState {
        return SwitchChainDeviceState(
            macro = model.target,
            value = model.value
        )
    }
}