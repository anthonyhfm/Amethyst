package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class DrumGroupDeviceAdapter(
    private val xml: XmlElement
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val branches: List<XmlElement> = xml.localQuerySelector("Branches").first().children

        return listOf(
            GroupChainDeviceState(
                groups = branches.mapIndexed { index, branch ->
                    Group(
                        name = branch.querySelector("UserName")[0].attributes["Value"] ?: "Chain ${index + 1}",
                        stateChain = StateChain(
                            devices = mutableListOf<DeviceState>().apply {
                                val branchInfo = branch.localQuerySelector("BranchInfo")[0]

                                val note = branchInfo.localQuerySelector("ReceivingNote")[0].attributes["Value"]?.toInt() ?: 0

                                val xy = DRUM_RACK_TO_XY[128 - note] // WHYYYYYY
                                val x: Int = xy % 10
                                val y: Int = (xy / 10) - 9

                                add(
                                    CoordinateFilterChainDeviceState(
                                        filters = listOf(
                                            Pair(x,  y)
                                        )
                                    )
                                )

                                addAll(
                                    branch.querySelector("DeviceChain")[0]
                                        .querySelector("Devices")[0]
                                        .children.mapNotNull { child ->
                                            resolveAdapter(child)
                                                ?.toDeviceStates()
                                                ?.firstOrNull()
                                        }
                                )
                            }
                        )
                    )
                }
            )
        )
    }
}