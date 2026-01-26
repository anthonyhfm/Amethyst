package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldMode

class InfinityAdapter: AbletonAdapter() {
    override fun toDeviceStates(): List<dev.anthonyhfm.amethyst.devices.DeviceState> {
        return listOf(
            HoldChainDeviceState(
                mode = HoldMode.Infinite
            )
        )
    }
}