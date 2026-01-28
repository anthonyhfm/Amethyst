package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDeviceState

class ApolloRotateAdapter(
    model: ApolloModel.Device.Rotate
) : ApolloAdapter<ApolloModel.Device.Rotate>(model) {
    override fun toDeviceState(): DeviceState {
        return RotateChainDeviceState(
            bypass = model.bypass,
            mode = when (model.mode) {
                0 -> RotateChainDeviceState.RotateMode.DEGREES_90
                1 -> RotateChainDeviceState.RotateMode.DEGREES_180
                2 -> RotateChainDeviceState.RotateMode.DEGREES_270
                else -> RotateChainDeviceState.RotateMode.DEGREES_90
            }
        )
    }
}
