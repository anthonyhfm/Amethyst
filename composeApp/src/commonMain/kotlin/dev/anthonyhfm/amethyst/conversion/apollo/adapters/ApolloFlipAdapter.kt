package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDeviceState

class ApolloFlipAdapter(
    model: ApolloModel.Device.Flip
) : ApolloAdapter<ApolloModel.Device.Flip>(model) {
    override fun toDeviceState(): DeviceState {
        return FlipChainDeviceState(
            mode = when (model.mode) {
                0 -> FlipChainDeviceState.FlipMode.HORIZONTAL
                1 -> FlipChainDeviceState.FlipMode.VERTICAL
                2 -> FlipChainDeviceState.FlipMode.DIAGONAL_PLUS
                3 -> FlipChainDeviceState.FlipMode.DIAGONAL_MINUS
                else -> FlipChainDeviceState.FlipMode.HORIZONTAL
            },
            bypass = model.bypass
        )
    }
}
