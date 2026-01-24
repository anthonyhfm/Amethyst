package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState

class ApolloPaintAdapter(
    model: ApolloModel.Device.Paint
) : ApolloAdapter<ApolloModel.Device.Paint>(model) {
    override fun toDeviceState(): DeviceState {
        return ColorChainDeviceState(
            r = model.color.r.toInt() / 63f,
            g = model.color.g.toInt() / 63f,
            b = model.color.b.toInt() / 63f
        )
    }
}