package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDeviceState

class ApolloRotateAdapter(
    model: ApolloModel.Device.Rotate
) : ApolloAdapter<ApolloModel.Device.Rotate>(model) {
    override fun toDeviceState(): DeviceState {
        val (mode, angle) = when (model.mode) {
            0 -> Pair(RotateChainDeviceState.RotateMode.DEGREES_90, 90f)
            1 -> Pair(RotateChainDeviceState.RotateMode.DEGREES_180, 180f)
            2 -> Pair(RotateChainDeviceState.RotateMode.DEGREES_270, 270f)
            else -> Pair(RotateChainDeviceState.RotateMode.DEGREES_90, 90f)
        }
        return RotateChainDeviceState(
            bypass = model.bypass,
            mode = mode,
            angleDegrees = angle
        )
    }
}
