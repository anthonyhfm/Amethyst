package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class ApolloGroupAdapter(
    model: ApolloModel.Device.Group
) : ApolloAdapter<ApolloModel.Device.Group>(model) {
    override fun toDeviceState(): DeviceState {
        return GroupChainDeviceState(
            groups = model.chains.map {
                Group(
                    name = it.name,
                    stateChain = StateChain(
                        devices = it.devices.map {
                            resolveAdapter(it.device)
                        }
                    )
                )
            }
        )
    }
}