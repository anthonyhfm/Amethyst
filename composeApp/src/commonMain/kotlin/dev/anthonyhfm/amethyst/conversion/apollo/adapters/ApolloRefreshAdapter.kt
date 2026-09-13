package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.macro_refresh.MacroRefreshChainDeviceState

class ApolloRefreshAdapter(
    model: ApolloModel.Device.Refresh
) : ApolloAdapter<ApolloModel.Device.Refresh>(model) {
    override fun toDeviceState(): DeviceState = MacroRefreshChainDeviceState
}
