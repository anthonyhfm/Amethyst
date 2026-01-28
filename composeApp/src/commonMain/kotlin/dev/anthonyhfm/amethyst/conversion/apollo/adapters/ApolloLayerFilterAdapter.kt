package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.layer_filter.LayerFilterChainDeviceState

class ApolloLayerFilterAdapter(
    model: ApolloModel.Device.LayerFilter
) : ApolloAdapter<ApolloModel.Device.LayerFilter>(model) {
    override fun toDeviceState(): DeviceState {
        return LayerFilterChainDeviceState(
            layer = model.target,
            range = model.range
        )
    }
}
