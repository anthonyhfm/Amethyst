package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.conversion.apollo.utils.toTiming
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldMode

class ApolloHoldAdapter(
    model: ApolloModel.Device.Hold
) : ApolloAdapter<ApolloModel.Device.Hold>(model) {
    override fun toDeviceState(): DeviceState {
        val timing: Timing = model.time.toTiming()

        return HoldChainDeviceState(
            timing = timing,
            gate = (model.gate / 2.0).toFloat(),
            mode = when (model.mode) {
                0 -> HoldMode.Trigger
                1 -> HoldMode.Minimum
                2 -> HoldMode.Infinite
                else -> HoldMode.Trigger
            },
            onRelease = model.release
        )
    }
}