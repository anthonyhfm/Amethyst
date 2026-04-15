package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class ApolloMultiAdapter(
    model: ApolloModel.Device.Multi
) : ApolloAdapter<ApolloModel.Device.Multi>(model) {
    override fun toDeviceState(): DeviceState {
        return MultiGroupChainDeviceState(
            type = when (model.multiMode) {
                0 -> MultiGroupChainDeviceState.TYPE.FORWARD
                1 -> MultiGroupChainDeviceState.TYPE.BACKWARD
                2, 3 -> MultiGroupChainDeviceState.TYPE.RANDOM
                else -> MultiGroupChainDeviceState.TYPE.FORWARD
            },
            groups = model.chains.map { chain ->
                Group(
                    name = chain.name,
                    stateChain = StateChain(
                        devices = chain.devices.map { resolveAdapter(it.device) }
                    )
                )
            },
            preprocessChain = StateChain(
                devices = model.preprocess.devices.map { resolveAdapter(it.device) }
            )
        )
    }
}
