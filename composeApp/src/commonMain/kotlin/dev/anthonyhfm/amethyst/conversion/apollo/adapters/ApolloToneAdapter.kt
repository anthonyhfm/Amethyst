package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.shift.ShiftChainDeviceState

class ApolloToneAdapter(
    model: ApolloModel.Device.Tone
) : ApolloAdapter<ApolloModel.Device.Tone>(model) {
    override fun toDeviceState(): DeviceState {
        return ShiftChainDeviceState(
            hue = model.hue.toFloat(),
            saturationMax = model.satHigh.toFloat(),
            saturationLow = model.satLow.toFloat(),
            valueHigh = model.valueHigh.toFloat(),
            valueLow = model.valueLow.toFloat()
        )
    }
}
