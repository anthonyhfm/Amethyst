package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class MidiEffectGroupAdapter(
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
                                val zoneSettings = branch.localQuerySelector("ZoneSettings")[0]
                                val branchSelectorRange = branch.localQuerySelector("BranchSelectorRange")[0]


                                val minMacro = branchSelectorRange.localQuerySelector("Min")[0].attributes["Value"]?.toInt()
                                val maxMacro = branchSelectorRange.localQuerySelector("Max")[0].attributes["Value"]?.toInt()

                                val minKey = zoneSettings.localQuerySelector("KeyRange")[0].localQuerySelector("Min")[0].attributes["Value"]?.toInt() ?: 0
                                val maxKey = zoneSettings.localQuerySelector("KeyRange")[0].localQuerySelector("Max")[0].attributes["Value"]?.toInt() ?: 127

                                if ((branch.querySelector("UserName")[0].attributes["Value"]).toString().lowercase().contains("page")) {
                                    println("Branch ${index + 1} is a page, adding macro filter device state.")
                                    println("TODO: Handle page branches properly.")

                                    add(
                                        MacroFilterChainDeviceState(
                                            macro = 0,
                                            value = minMacro!!,
                                        )
                                    )
                                }

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