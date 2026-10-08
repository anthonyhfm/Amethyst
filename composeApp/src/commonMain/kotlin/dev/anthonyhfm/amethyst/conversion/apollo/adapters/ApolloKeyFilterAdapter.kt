package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.conversion.apollo.utils.apolloPadCoordinates
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState

class ApolloKeyFilterAdapter(
    model: ApolloModel.Device.KeyFilter
) : ApolloAdapter<ApolloModel.Device.KeyFilter>(model) {
    override fun toDeviceState(): DeviceState {
        return CoordinateFilterChainDeviceState(
            filters = model.filters.mapIndexedNotNull { index, enabled ->
                if (enabled) {
                    apolloPadCoordinates(index = index)
                } else {
                    null
                }
            }
        )
    }
}