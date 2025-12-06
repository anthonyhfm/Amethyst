package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState.TYPE
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

/*class MultiEffectAdapter (
    private val deviceXml: XmlElement,
    private val containerXml: XmlElement
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        var branches = containerXml.localQuerySelector("Branches").let {
            if (it.isNotEmpty()) {
                it.first().children
            } else {
                emptyList()
            }
        }

        if (branches.isEmpty()) {
            println("No branches found in Multi container!")
            return listOf()
        }

        if (containerXml.name == "MidiEffectGroupDevice" || containerXml.name == "InstrumentGroupDevice") {
            // check each chain for range of notes, if it's bigger than 1, duplicate the chain for each note
            branches = branches.map { branch ->
                val zoneSettings = branch.localQuerySelector("ZoneSettings")[0]
                val minKey = zoneSettings.localQuerySelector("KeyRange")[0].localQuerySelector("Min")[0].attributes["Value"]?.toInt() ?: 127
                val maxKey = zoneSettings.localQuerySelector("KeyRange")[0].localQuerySelector("Max")[0].attributes["Value"]?.toInt() ?: 127

                if (maxKey > minKey) {
                    // duplicate chain for each note
                    List(maxKey - minKey + 1) { note ->
                        branch.copy()
                    }
                } else {
                    listOf(branch)
                }
            }.flatten()
        }

        val steps = deviceXml.querySelector("ParameterList").last()
            .querySelector("Timeable").first()
            .querySelector("Manual").first()
            .attributes["Value"]?.toInt() ?: 1

        return listOf(
            MultiGroupChainDeviceState(
                type = TYPE.FORWARD,
                groups = List(steps) { step ->
                    val branch = branches.getOrNull(step)

                    val name = branch?.querySelector("UserName")?.getOrNull(0)?.attributes?.get("Value")
                        ?: "Chain ${step + 1}"

                    Group(
                        name = name,
                        stateChain = StateChain(
                            devices = mutableListOf<DeviceState>().apply {
                                branch?.let {
                                    /*addAll(
                                        it.querySelector("DeviceChain").first()
                                            .querySelector("Devices").first()
                                            .children.flatMap { child ->
                                                resolveAdapter(child)
                                                    ?.toDeviceStates()
                                                    ?: emptyList()
                                            }
                                    )*/
                                }
                            }
                        )
                    )
                }
            )
        )
    }
}*/