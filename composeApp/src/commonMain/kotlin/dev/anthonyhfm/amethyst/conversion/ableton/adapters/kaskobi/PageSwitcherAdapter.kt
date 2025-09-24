package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class PageSwitcherAdapter(
    private val blob: String
) : AbletonAdapter() {
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
                        name = "Page Switching",
                        stateChain = StateChain(
                            devices = listOf(
                                GroupChainDeviceState(
                                    groups = mutableListOf<Group>().apply {
                                        for (i in 0..7) { // Page 1-8
                                            add(
                                                Group(
                                                    name = "Page ${i + 1}",
                                                    stateChain = StateChain(
                                                        devices = listOf(
                                                            CoordinateFilterChainDeviceState(
                                                                filters = listOf(
                                                                    Pair(9, 1 + i)
                                                                )
                                                            ),
                                                            SwitchChainDeviceState(
                                                                macro = 0,
                                                                value = i
                                                            ),
                                                            ColorChainDeviceState(
                                                                r = 0f,
                                                                g = 0f,
                                                                b = 0f,
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        }

                                        for (i in 0..7) { // Page 1-8
                                            add(
                                                Group(
                                                    name = "Page ${8 + 1}",
                                                    stateChain = StateChain(
                                                        devices = listOf(
                                                            CoordinateFilterChainDeviceState(
                                                                filters = listOf(
                                                                    Pair(0, 1 + i)
                                                                )
                                                            ),
                                                            SwitchChainDeviceState(
                                                                macro = 0,
                                                                value = i + 8
                                                            ),
                                                            ColorChainDeviceState(
                                                                r = 0f,
                                                                g = 0f,
                                                                b = 0f,
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        }
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