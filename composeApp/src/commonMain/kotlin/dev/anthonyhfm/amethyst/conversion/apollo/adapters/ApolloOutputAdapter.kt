package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDeviceState

class ApolloOutputAdapter(
    model: ApolloModel.Device.Output
) : ApolloAdapter<ApolloModel.Device.Output>(model) {
    override fun toDeviceState(): DeviceState {
        return TransmitChainDeviceState(
            mode = TransmitChainDeviceState.Mode.Send,
            channel = (model.target + 1).coerceIn(1, 16)
        )
    }
}
