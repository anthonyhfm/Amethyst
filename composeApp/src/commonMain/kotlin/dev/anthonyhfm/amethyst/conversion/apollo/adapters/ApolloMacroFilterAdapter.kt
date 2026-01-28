package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class ApolloMacroFilterAdapter(
    model: ApolloModel.Device.MacroFilter
) : ApolloAdapter<ApolloModel.Device.MacroFilter>(model) {
    override fun toDeviceState(): DeviceState {
        println("MACRO FILTER DETECTED - UPDATE AMETHYSTS MACRO FILTER FOR 100 VALUES SUPPORT")

        return GroupChainDeviceState(
            groups = model.filter.mapIndexedNotNull { index, bool ->
                if (bool) {
                    Group(
                        name = "Filter #",
                        stateChain = StateChain(
                            devices = listOf(
                                MacroFilterChainDeviceState(
                                    macro = model.macro,
                                    value = index + 1,
                                )
                            )
                        )
                    )
                } else null
            }
        )
    }
}