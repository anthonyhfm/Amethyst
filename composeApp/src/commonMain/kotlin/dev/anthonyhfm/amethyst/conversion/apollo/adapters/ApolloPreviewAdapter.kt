package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.preview.PreviewChainDeviceState

class ApolloPreviewAdapter(
    model: ApolloModel.Device.Preview
) : ApolloAdapter<ApolloModel.Device.Preview>(model) {
    override fun toDeviceState(): DeviceState {
        return PreviewChainDeviceState
    }
}
