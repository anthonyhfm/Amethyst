package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.TwistAdapter.TwistData
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState.TYPE
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MultiAdapter(
    private val blob: ByteArray,
    private val containerXml: XmlElement
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val dataObj: MultiData = Json {
            ignoreUnknownKeys = true
        }.decodeFromString(blob.decodeToString())

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

        val steps = dataObj.steps.first().toInt()

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
                                    addAll(
                                        it.querySelector("DeviceChain").first()
                                            .querySelector("Devices").first()
                                            .children.mapNotNull { child ->
                                                resolveAdapter(child)
                                                    ?.toDeviceStates()
                                                    ?.firstOrNull()
                                            }
                                    )
                                }
                            }
                        )
                    )
                }
            )
        )
    }

    @Serializable
    data class MultiData(
        @SerialName("live.numbox")
        val steps: List<Double>,
    )
}