package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState

class ApolloMacroFilterAdapter(
    model: ApolloModel.Device.MacroFilter
) : ApolloAdapter<ApolloModel.Device.MacroFilter>(model) {
    override fun toDeviceState(): DeviceState {
        return MacroFilterChainDeviceState(
            macro = model.macro - 1,
            allowedValues = model.filter.mapIndexedNotNull { index, enabled ->
                if (enabled) index else null
            }.toSet()
        )
    }
}
