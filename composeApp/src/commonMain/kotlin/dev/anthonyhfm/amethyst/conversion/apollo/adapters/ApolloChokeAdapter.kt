package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class ApolloChokeAdapter(
    model: ApolloModel.Device.Choke
) : ApolloAdapter<ApolloModel.Device.Choke>(model) {
    override fun toDeviceState(): DeviceState {
        return ChokeChainDeviceState(
            target = model.target,
            stateChain = resolveChain(model = model.chain)
        )
    }
}