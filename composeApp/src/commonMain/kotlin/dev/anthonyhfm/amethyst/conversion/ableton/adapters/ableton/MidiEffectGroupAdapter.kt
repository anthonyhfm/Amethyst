package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter.Companion.readDataBlob
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.MultiAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class MidiEffectGroupAdapter(
    private val xml: XmlElement
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val branches: List<XmlElement> = xml.localQuerySelector("Branches").first().children

        val hasChains = xml.localQuerySelector("ChainSelector")
            .first()
            .querySelector("KeyMidi")
            .firstOrNull() != null

        val groups = mutableListOf<Group>()

        // Check for Velocity Arpeggiator preset
        val branch1Name = branches.getOrNull(0)?.querySelector("UserName")?.first()?.attributes?.get("Value")?.ifEmpty { null }
        val branch2Name = branches.getOrNull(1)?.querySelector("UserName")?.first()?.attributes?.get("Value")?.ifEmpty { null }
        if (branch1Name == "Magic" && branch2Name == "Rate Preview") {
            return VelocityArpeggiatorAdapter(xml).toDeviceStates()
        }

        groups.addAll(
            branches.mapIndexed { index, branch ->
                val enabled = branch.querySelector("Speaker")
                    .last()
                    .querySelector("Manual")
                    .first()
                    .attributes["Value"]?.toBoolean() ?: true

                if (!enabled) return@mapIndexed null

                // TODO: implement multi for lights

                Group(
                    name = branch.querySelector("UserName")[0].attributes["Value"].let {
                        if (it != null) {
                            return@let it.ifEmpty {
                                "Chain ${index + 1}"
                            }
                        } else {
                            return@let "Chain #"
                        }
                    },
                    stateChain = StateChain(
                        devices = mutableListOf<DeviceState>().apply {
                            val zoneSettings = branch.localQuerySelector("ZoneSettings")[0]
                            val branchSelectorRange = branch.localQuerySelector("BranchSelectorRange")[0]

                            val minMacro = branchSelectorRange.localQuerySelector("Min")[0].attributes["Value"]?.toInt() ?: 0
                            val maxMacro = branchSelectorRange.localQuerySelector("Max")[0].attributes["Value"]?.toInt() ?: 0

                            val minKey = zoneSettings.localQuerySelector("KeyRange")[0].localQuerySelector("Min")[0].attributes["Value"]?.toInt() ?: 0
                            val maxKey = zoneSettings.localQuerySelector("KeyRange")[0].localQuerySelector("Max")[0].attributes["Value"]?.toInt() ?: 127

                            if (hasChains) {
                                if (maxMacro - minMacro == 0) {
                                    add(
                                        MacroFilterChainDeviceState(
                                            macro = 0,
                                            value = minMacro,
                                        )
                                    )
                                } else {
                                    add(
                                        GroupChainDeviceState(
                                            groups = (minMacro..maxMacro).map { key ->

                                                Group(
                                                    name = "Key $key",
                                                    stateChain = StateChain(
                                                        devices = listOf(
                                                            MacroFilterChainDeviceState(
                                                                macro = 0,
                                                                value = key,
                                                            )
                                                        )
                                                    )
                                                )
                                            }
                                        )
                                    )
                                }
                            }

                            if (maxKey - minKey != 127 || minKey == maxKey) {
                                add(
                                    CoordinateFilterChainDeviceState(
                                        filters = IntArray(maxKey + 1 - minKey) {
                                            minKey + it
                                        }.map {
                                            val xy = DRUM_RACK_TO_XY[it]

                                            val x: Int = xy % 10
                                            val y: Int = xy / 10

                                            Pair(x,  9 - y)
                                        }
                                    )
                                )
                            }

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
                                val lightsContainer = branchElements.find {
                                    it.name == "MidiEffectGroupDevice"
                                }

                                if (multiDevice != null && lightsContainer != null) {
                                    println("Found multi and container, using MultiAdapter")
                                    val multiDataBlob = multiDevice.localQuerySelector("BlobSlot")[0]
                                        .localQuerySelector("Value")[0]
                                        .localQuerySelector("MxDBlob")[0]
                                        .localQuerySelector("Blob")[0]

                                    addAll(
                                        MultiAdapter(
                                            blob = readDataBlob(multiDataBlob.text!!),
                                            containerXml = lightsContainer
                                        ).toDeviceStates()
                                    )

                                    return@apply
                                } else if (randomDevice != null && lightsContainer != null) {
                                    println("Found random and container, using RandomDeviceMultisamplingAdapter")
                                    addAll(
                                        RandomDeviceMultisamplingAdapter(
                                            randomDeviceXml = randomDevice,
                                            containerXml = lightsContainer
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
            }.filterNotNull()
        )

        if (hasChains) {
            groups.add(
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
        }

        return listOf(
            GroupChainDeviceState(
                groups = groups
            )
        )
    }
}