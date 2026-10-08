package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState

class ApolloOutputAdapter(
    model: ApolloModel.Device.Output
) : ApolloAdapter<ApolloModel.Device.Output>(model) {
    override fun toDeviceState(): DeviceState = OffsetChainDeviceState(
        targetLaunchpadIndex = model.target,
    )
}
