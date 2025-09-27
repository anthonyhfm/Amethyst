package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter.Companion.readDataBlob
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.MultiAdapter
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
                                val y: Int = 9 - xy / 10

                                add(
                                    CoordinateFilterChainDeviceState(
                                        filters = listOf(
                                            Pair(x,  y)
                                        )
                                    )
                                )

                                // Multisampling logic
                                // TODO: replace simple multi name checking for max plugin with hash check
                                val branchElements = branch.querySelector("DeviceChain")[0]
                                    .querySelector("Devices")[0]
                                    .children

                                if (branchElements.size >= 2) {
                                    val multiDevice = branchElements.find {
                                        (it.querySelector("Name")[0].attributes["Value"]?.lowercase()?.contains("multi")
                                            ?: false)
                                        && !it.querySelector("Name")[0].attributes["Value"]?.lowercase()
                                            ?.contains("reset")!!
                                    }
                                    val randomDevice = branchElements.find {
                                        it.name == "MidiRandom"
                                    }
                                    val samplesContainer = branchElements.find {
                                        it.name == "InstrumentGroupDevice"
                                                || it.name == "DrumGroupDevice"
                                    }

                                    if (multiDevice != null && samplesContainer != null) {
                                        println("Found multi and container, using MultiAdapter")
                                        val multiDataBlob = multiDevice.localQuerySelector("BlobSlot")[0]
                                            .localQuerySelector("Value")[0]
                                            .localQuerySelector("MxDBlob")[0]
                                            .localQuerySelector("Blob")[0]

                                        addAll(
                                            MultiAdapter(
                                                blob = readDataBlob(multiDataBlob.text!!),
                                                containerXml = samplesContainer
                                            ).toDeviceStates()
                                        )

                                        return@apply
                                    } else if (randomDevice != null && samplesContainer != null) {
                                        println("Found random and container, using RandomDeviceMultisamplingAdapter")
                                        addAll(
                                            RandomDeviceMultisamplingAdapter(
                                                randomDeviceXml = randomDevice,
                                                containerXml = samplesContainer
                                            ).toDeviceStates()
                                        )

                                        return@apply
                                    }
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