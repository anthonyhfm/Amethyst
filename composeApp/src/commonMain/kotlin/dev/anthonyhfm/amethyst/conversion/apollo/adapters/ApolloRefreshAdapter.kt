package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class ApolloRefreshAdapter(
    model: ApolloModel.Device.Refresh
) : ApolloAdapter<ApolloModel.Device.Refresh>(model) {
    override fun toDeviceState(): DeviceState {
        // Refresh has no Amethyst equivalent; signal passes through unchanged
        return GroupChainDeviceState(
            groups = listOf(Group(name = "Refresh", stateChain = StateChain(devices = emptyList())))
        )
    }
}
