package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class InstrumentGroupAdapter(
    private val device: InstrumentGroupDevice,
    private val offset: IntOffset = IntOffset.Zero,
    private val outputOffset: IntOffset = IntOffset.Zero,
    private val chainDepth: Int = 0
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val branches: List<InstrumentGroupDevice.Branches.InstrumentBranch> = device.branches.branches

        val hasChains = device.chainSelector.keyMidi != null

        val groups = mutableListOf<Group>()

        val branch1Name = branches.getOrNull(0)?.name?.effectiveName?.value
        val branch2Name = branches.getOrNull(1)?.name?.effectiveName?.value

        if (branch1Name == "Magic" && branch2Name == "Rate Preview") {
            TODO("Velocity Arpeggiator needs to be fixed for the new system")
            // return VelocityArpeggiatorAdapter(xml).toDeviceStates()
        }

        groups.addAll(
            branches.mapIndexed { index, branch ->
                val enabled = branch.masterDevice.speaker.manual.value

                if (!enabled) return@mapIndexed null

                // TODO: implement multi for lights

                Group(
                    name = branch.name.effectiveName.let {
                        if (it?.value != null) {
                            return@let it.value.ifBlank {
                                "Chain ${index + 1}"
                            }
                        } else {
                            return@let "Chain #"
                        }
                    },
                    stateChain = StateChain(
                        devices = mutableListOf<DeviceState>().apply {
                            val minMacro = branch.branchSelectorRange.min.value
                            val maxMacro = branch.branchSelectorRange.max.value

                            val minKey = branch.zoneSettings.keyRange.min.value
                            val maxKey = branch.zoneSettings.keyRange.max.value

                            if (hasChains || (AbletonConverter.special.kaskobiWeirdAssPageSwitch && chainDepth == 0)) {
                                if (maxMacro - minMacro == 0) {
                                    add(
                                        MacroFilterChainDeviceState(
                                            macro = 0,
                                            value = minMacro,
                                        )
                                    )
                                } else if (maxMacro - minMacro != 127) {
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

                                            Pair(x + offset.x,  (9 - y) + offset.y)
                                        }
                                    )
                                )
                            }

                            // Multisampling logic
                            val branchElements = branch.deviceChain.deviceChain.devices.devices

                            if (branchElements.size >= 2) {
                                /*val potentialMultiDevice: MxDeviceMidiEffect? = branchElements.find {
                                    it is MxDeviceMidiEffect
                                } as MxDeviceMidiEffect?

                                val patchSlot = potentialMultiDevice?.patchSlot

                                val potentialMultiDeviceHash = potentialMultiDevice.let {
                                    val path = patchSlot?.value?.patchRef?.fileRef?.resolvePath() ?: error("No patch ref found")

                                    val hash: String = if (AbletonConverter.isZip) {
                                        AbletonConverter.zipEntries[path]?.data?.toFileHash() ?: ""
                                    } else {
                                        val file = PlatformFile(path)
                                        file.getFileHash()
                                    }

                                    hash
                                }
                                val outbreakMultiHashMatches = MULTI_HASHES.contains(potentialMultiDeviceHash)
                                val kaskobiMultiHashMatches = KASKOBI_MULTI_HASHES.contains(potentialMultiDeviceHash)
                                val multiHashMatches = outbreakMultiHashMatches || kaskobiMultiHashMatches

                                val randomDevice = branchElements.find {
                                    it.name == "MidiRandom"
                                }

                                val lightsContainer = branchElements.find {
                                    it is MidiEffectGroupDevice
                                } as MidiEffectGroupDevice?

                                if (potentialMultiDevice != null && multiHashMatches && lightsContainer != null) {
                                    println("Found multi and container, using MultiAdapter")
                                    val multiDataBlob = potentialMultiDevice.localQuerySelector("BlobSlot")[0]
                                        .localQuerySelector("Value")[0]
                                        .localQuerySelector("MxDBlob")[0]
                                        .localQuerySelector("Blob")[0]

                                    addAll(
                                        try {
                                            if (outbreakMultiHashMatches) {
                                                MultiAdapter(
                                                    blob = readDataBlob(multiDataBlob.text!!),
                                                    containerXml = lightsContainer
                                                ).toDeviceStates()
                                            } else if (kaskobiMultiHashMatches) {
                                                MultiEffectAdapter(
                                                    deviceXml = potentialMultiDevice,
                                                    containerXml = lightsContainer
                                                ).toDeviceStates()
                                            } else {
                                                listOf()
                                            }
                                        } catch (e: Exception) {
                                            println("Error reading multi plugin with hash $potentialMultiDeviceHash, falling back to normal chain")
                                            println("Error: ${e.message}")
                                            listOf()
                                        }
                                    )

                                    return@apply
                                } else if (randomDevice != null && lightsContainer != null) {
                                    println("Found random and container, using RandomDeviceMultisamplingAdapter")
                                    addAll(
                                        try {
                                            RandomDeviceMultisamplingAdapter(
                                                randomDeviceXml = randomDevice,
                                                containerXml = lightsContainer
                                            ).toDeviceStates()
                                        } catch (e: Exception) {
                                            println("Error reading random multisampling plugin, falling back to normal chain")
                                            println("Error: ${e.message}")
                                            listOf()
                                        }
                                    )

                                    return@apply
                                }*/
                            }

                            addAll(
                                branch.deviceChain.deviceChain.devices.devices.flatMap {
                                    resolveAdapter(
                                        device = it,
                                        offset = offset,
                                        outputOffset = outputOffset,
                                        chainDepth = chainDepth + 1
                                    )?.toDeviceStates() ?: emptyList()
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
                                                                Pair(9 + offset.x, (1 + i) + offset.y)
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
                                                                Pair(0 + offset.x, (1 + i) + offset.y)
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