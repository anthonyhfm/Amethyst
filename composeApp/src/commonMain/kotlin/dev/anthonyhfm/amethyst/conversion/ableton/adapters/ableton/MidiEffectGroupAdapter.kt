package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.utils.MultiPluginHashes.KASKOBI_MULTI_HASHES
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.utils.MultiPluginHashes.MULTI_HASHES
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.MultiEffectAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.MultiAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiRandom
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonPageIndexing
import dev.anthonyhfm.amethyst.conversion.ableton.utils.getFileHash
import dev.anthonyhfm.amethyst.conversion.ableton.utils.toFileHash
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import io.github.vinceglb.filekit.PlatformFile

class MidiEffectGroupAdapter(
    private val device: MidiEffectGroupDevice,
    private val offset: IntOffset = IntOffset.Zero,
    private val outputOffset: IntOffset = IntOffset.Zero,
    private val chainDepth: Int = 0,
    private val isInsideDrumRack: Boolean = false,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val branches: List<MidiEffectGroupDevice.Branches.MidiEffectBranch> = device.branches.branches

        val selectorRanges = branches.map {
            it.branchSelectorRange.min.value to it.branchSelectorRange.max.value
        }
        val selectorControlsPages = AbletonPageIndexing.controlsPages(
            hasKeyMidiMapping = device.chainSelector.keyMidi != null,
            selectorRanges = selectorRanges,
        )
        val hasMacroFilter = selectorControlsPages

        val hasPageSwitching = !isInsideDrumRack && chainDepth == 0 && selectorControlsPages

        val pageSelectorOffset = if (hasPageSwitching) {
            AbletonPageIndexing.sourceOffset(
                selectorMinimum = device.chainSelector.midiControllerRange?.min?.value,
            )
        } else {
            0
        }

        val groups = mutableListOf<Group>()

        val branch1Name = branches.getOrNull(0)?.name?.effectiveName?.value
        val branch2Name = branches.getOrNull(1)?.name?.effectiveName?.value

        if (branch1Name == "Magic" && branch2Name == "Rate Preview") {
            return VelocityArpeggiatorAdapter(device).toDeviceStates().withMuteState(device.on.manual.value)
        }

        groups.addAll(
            branches.mapIndexed { index, branch ->
                val enabled = branch.masterDevice.speaker.manual.value

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
                            val minMacro = AbletonPageIndexing.normalizeSelectorValue(
                                value = branch.branchSelectorRange.min.value,
                                sourceOffset = pageSelectorOffset,
                            )
                            val maxMacro = AbletonPageIndexing.normalizeSelectorValue(
                                value = branch.branchSelectorRange.max.value,
                                sourceOffset = pageSelectorOffset,
                            )

                            val minKey = branch.zoneSettings.keyRange.min.value
                            val maxKey = branch.zoneSettings.keyRange.max.value

                            if (hasMacroFilter) {
                                if (maxMacro - minMacro != 127) {
                                    add(
                                        MacroFilterChainDeviceState(
                                            macro = chainDepth,
                                            allowedValues = (minMacro..maxMacro).toSet(),
                                        )
                                    )
                                }
                            }

                            if (maxKey - minKey != 127 || minKey == maxKey) {
                                add(
                                    AbletonConverter.coordinateFilter(
                                        launchpad = AbletonConverter.launchpadTarget(offset),
                                        localCoordinates = IntArray(maxKey + 1 - minKey) {
                                            minKey + it
                                        }.map {
                                            val xy = DRUM_RACK_TO_XY[it]

                                            val x: Int = xy % 10
                                            val y: Int = xy / 10

                                            Pair(x, 9 - y)
                                        },
                                    )
                                )
                            }

                            // Multisampling logic
                            val branchElements = branch.deviceChain.deviceChain.devices.devices

                            if (branchElements.size >= 2) {
                                val multiMatch = branchElements
                                    .filterIsInstance<MxDeviceMidiEffect>()
                                    .mapNotNull { candidate ->
                                        val path = candidate.patchSlot.value.patchRef?.fileRef?.resolvePath()
                                            ?: return@mapNotNull null
                                        val hash = MxDeviceMidiEffectAdapter.fileHashMap[path] ?: (
                                            if (AbletonConverter.isZip) {
                                                AbletonConverter.readZipEntry(path)?.toFileHash() ?: ""
                                            } else {
                                                PlatformFile(path).getFileHash()
                                            }
                                        ).also { MxDeviceMidiEffectAdapter.fileHashMap[path] = it }
                                        if (hash in MULTI_HASHES || hash in KASKOBI_MULTI_HASHES) {
                                            Triple(candidate, hash, branchElements.indexOf(candidate))
                                        } else null
                                    }
                                    .firstOrNull()
                                val potentialMultiDevice = multiMatch?.first
                                val potentialMultiDeviceHash = multiMatch?.second
                                val multiDeviceIndex = multiMatch?.third ?: -1
                                val outbreakMultiHashMatches = MULTI_HASHES.contains(potentialMultiDeviceHash)
                                val kaskobiMultiHashMatches = KASKOBI_MULTI_HASHES.contains(potentialMultiDeviceHash)
                                val multiHashMatches = outbreakMultiHashMatches || kaskobiMultiHashMatches

                                val randomDevice: MidiRandom? = branchElements.find {
                                    it is MidiRandom
                                } as? MidiRandom

                                val lightsContainer: MidiEffectGroupDevice? = branchElements.find {
                                    it is MidiEffectGroupDevice
                                } as? MidiEffectGroupDevice

                                if (potentialMultiDevice != null && multiHashMatches && lightsContainer != null) {
                                    println("Found multi and container, using MultiAdapter")

                                    addAll(
                                        branchElements.take(multiDeviceIndex).flatMap { prefixDevice ->
                                            resolveAdapter(
                                                device = prefixDevice,
                                                offset = offset,
                                                outputOffset = outputOffset,
                                                chainDepth = chainDepth + 1,
                                            )?.toDeviceStates() ?: emptyList()
                                        }
                                    )

                                    addAll(
                                        try {
                                            if (outbreakMultiHashMatches) {
                                                MultiAdapter(
                                                    device = potentialMultiDevice,
                                                    midiContainer = lightsContainer,
                                                    instrumentContainer = null,
                                                    drumContainer = null,
                                                    offset = offset,
                                                    outputOffset = outputOffset,
                                                    chainDepth = chainDepth,
                                                ).toDeviceStates()
                                            } else if (kaskobiMultiHashMatches) {
                                                MultiEffectAdapter(
                                                    device = potentialMultiDevice,
                                                    midiContainer = lightsContainer,
                                                    instrumentContainer = null,
                                                    drumContainer = null,
                                                    offset = offset,
                                                    outputOffset = outputOffset,
                                                    chainDepth = chainDepth,
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

                                    val containerIndex = branchElements.indexOf(lightsContainer)
                                    if (containerIndex >= 0) {
                                        addAll(
                                            branchElements.drop(containerIndex + 1).flatMap { suffixDevice ->
                                                resolveAdapter(
                                                    device = suffixDevice,
                                                    offset = offset,
                                                    outputOffset = outputOffset,
                                                    chainDepth = chainDepth + 1,
                                                )?.toDeviceStates() ?: emptyList()
                                            }
                                        )
                                    }

                                    return@apply
                                } else if (randomDevice != null && lightsContainer != null) {
                                    println("Found random and container, using RandomDeviceMultisamplingAdapter")
                                    addAll(
                                        try {
                                            RandomDeviceMultisamplingAdapter(
                                                random = randomDevice,
                                                midiContainer = lightsContainer,
                                                instrumentContainer = null,
                                                drumContainer = null
                                            ).toDeviceStates()
                                        } catch (e: Exception) {
                                            println("Error reading random multisampling plugin, falling back to normal chain")
                                            println("Error: ${e.message}")
                                            listOf()
                                        }
                                    )

                                    return@apply
                                }
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
                        }.withMuteState(enabled)
                    )
                )
            }
        )

        if (hasPageSwitching) {
            groups.add(
                0,
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
                                                        AbletonConverter.coordinateFilter(
                                                            launchpad = AbletonConverter.launchpadTarget(offset),
                                                            localCoordinates = listOf(Pair(9, 1 + i)),
                                                        ),
                                                        MacroControlChainDeviceState(
                                                            macro = 0,
                                                            value = i,
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

                                    for (i in 0..7) { // Page 9-16
                                        add(
                                            Group(
                                                name = "Page ${9 + i}",
                                                stateChain = StateChain(
                                                    devices = listOf(
                                                        AbletonConverter.coordinateFilter(
                                                            launchpad = AbletonConverter.launchpadTarget(offset),
                                                            localCoordinates = listOf(Pair(0, 1 + i)),
                                                        ),
                                                        MacroControlChainDeviceState(
                                                            macro = 0,
                                                            value = i + 8,
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
        ).withMuteState(device.on.manual.value)
    }
}
