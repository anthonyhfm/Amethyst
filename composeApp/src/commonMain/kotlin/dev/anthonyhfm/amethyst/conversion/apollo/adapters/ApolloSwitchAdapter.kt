package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState

class ApolloSwitchAdapter(
    model: ApolloModel.Device.Switch
) : ApolloAdapter<ApolloModel.Device.Switch>(model) {
    override fun toDeviceState(): DeviceState {
        return MacroControlChainDeviceState(
            macro = model.target - 1,
            value = model.value - 1
        )
    }
}