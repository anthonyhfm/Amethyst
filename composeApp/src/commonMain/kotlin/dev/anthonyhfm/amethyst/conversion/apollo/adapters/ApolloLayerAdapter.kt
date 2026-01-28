package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDeviceState

class ApolloLayerAdapter(
    model: ApolloModel.Device.Layer
) : ApolloAdapter<ApolloModel.Device.Layer>(model) {
    override fun toDeviceState(): DeviceState {
        val mode = if (ApolloConverter.version >= 5) {
            when (model.mode) {
                1 -> Signal.LED.BlendingMode.Screen
                2 -> Signal.LED.BlendingMode.Multiply
                3 -> Signal.LED.BlendingMode.Mask
                else -> Signal.LED.BlendingMode.Normal
            }
        } else {
            when (model.mode) {
                1 -> Signal.LED.BlendingMode.Multiply
                2 -> Signal.LED.BlendingMode.Screen
                else -> Signal.LED.BlendingMode.Normal
            }
        }

        return LayerChainDeviceState(
            layer = model.target,
            mode = mode,
            range = model.range
        )
    }
}
