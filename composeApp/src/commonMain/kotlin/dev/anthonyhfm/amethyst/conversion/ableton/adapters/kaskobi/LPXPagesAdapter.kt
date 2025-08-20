package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

/**
 * LPX Pages is a pretty simple plugin, utilizing the 8 buttons on the right for page switching
 */
class LPXPagesAdapter : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        return listOf(
            GroupChainDeviceState(
                groups = listOf(
                    Group(
                        name = "Passthrough",
                        stateChain = StateChain(
                            devices = listOf()
                        )
                    ),
                    Group(
                        name = "Page Switch",
                        stateChain = StateChain(
                            devices = listOf(
                                GroupChainDeviceState(
                                    groups = List(8) { index ->
                                        Group(
                                            name = "Page ${index + 1}",
                                            stateChain = StateChain(
                                                devices = listOf(
                                                    CoordinateFilterChainDeviceState(
                                                        filters = listOf(
                                                            Pair(9, 1 + index)
                                                        )
                                                    ),
                                                    SwitchChainDeviceState(
                                                        macro = 0,
                                                        value = index
                                                    ),
                                                    ColorChainDeviceState(0f, 0f, 0f)
                                                )
                                            )
                                        )
                                    }
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}