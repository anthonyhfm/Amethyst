package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.utils.MultiPluginHashes.KASKOBI_MULTI_HASHES
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.utils.MultiPluginHashes.MULTI_HASHES
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.MultiEffectAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.MultiAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiRandom
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.conversion.ableton.utils.getFileHash
import dev.anthonyhfm.amethyst.conversion.ableton.utils.toFileHash
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonPageIndexing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.mask.MaskChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import io.github.vinceglb.filekit.PlatformFile

class DrumGroupDeviceAdapter(
    private val device: DrumGroupDevice,
    val offset: IntOffset = IntOffset.Zero,
    val outputOffset: IntOffset = IntOffset.Zero,
    private val chainDepth: Int = 0
) : AbletonAdapter() {
    private val drumRackChokeScopeId = UUID.randomUUID()

    override fun toDeviceStates(): List<DeviceState> {
        val branches: List<DrumGroupDevice.Branches.DrumBranch> = device.branches.branches

        val ownSelectorControlsPages = AbletonPageIndexing.controlsPages(
            hasKeyMidiMapping = device.chainSelector.keyMidi != null,
            selectorRanges = branches.map {
                it.branchSelectorRange.min.value to it.branchSelectorRange.max.value
            },
        )
        val nestedSelectorControlsPages = branches.any { branch ->
            branch.deviceChain.deviceChain.devices.devices.any { subDevice ->
                when (subDevice) {
                    is InstrumentGroupDevice -> AbletonPageIndexing.controlsPages(
                        hasKeyMidiMapping = subDevice.chainSelector.keyMidi != null,
                        selectorRanges = subDevice.branches.branches.map {
                            it.branchSelectorRange.min.value to it.branchSelectorRange.max.value
                        },
                    )
                    is MidiEffectGroupDevice -> AbletonPageIndexing.controlsPages(
                        hasKeyMidiMapping = subDevice.chainSelector.keyMidi != null,
                        selectorRanges = subDevice.branches.branches.map {
                            it.branchSelectorRange.min.value to it.branchSelectorRange.max.value
                        },
                    )
                    else -> false
                }
            }
        }
        val hasPageSwitching = chainDepth == 0 && (
            ownSelectorControlsPages || nestedSelectorControlsPages
        )

        val groups = branches.mapIndexed { index, branch ->
            val chokeGroup = branch.branchInfo.chokeGroup.value
            val chokeOwnerId = "$drumRackChokeScopeId:branch:$index:${branch.id}"
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
                        val note = branch.branchInfo.receivingNote.value

                        val xy = DRUM_RACK_TO_XY[128 - note] // WHYYYYYY
                        val x: Int = xy % 10
                        val y: Int = 9 - xy / 10

                        add(
                            AbletonConverter.coordinateFilter(
                                launchpad = AbletonConverter.launchpadTarget(offset),
                                localCoordinates = listOf(Pair(x, y)),
                            )
                        )

                        // Multisampling logic
                        val branchElements = branch.deviceChain.deviceChain.devices.devices

                        if (branchElements.size >= 2) {
                            val potentialMultiDevice: MxDeviceMidiEffect? = branchElements.find {
                                it is MxDeviceMidiEffect
                            } as MxDeviceMidiEffect?

                            val patchSlot = potentialMultiDevice?.patchSlot

                            val potentialMultiDeviceHash = potentialMultiDevice.let {
                                val path = patchSlot?.value?.patchRef?.fileRef?.resolvePath() ?: return@let null

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

                            val randomDevice: MidiRandom? = branchElements.find {
                                it is MidiRandom
                            } as? MidiRandom

                            val instrumentContainer: InstrumentGroupDevice? = branchElements.find {
                                it is InstrumentGroupDevice
                            } as? InstrumentGroupDevice
                            val drumContainer: DrumGroupDevice? = branchElements.find {
                                it is DrumGroupDevice
                            } as? DrumGroupDevice

                            val anyContainerPresent = instrumentContainer != null || drumContainer != null

                            if (potentialMultiDevice != null && multiHashMatches && anyContainerPresent) {
                                println("Found multi and container, using MultiAdapter")

                                addAll(
                                    try {
                                        if (outbreakMultiHashMatches) {
                                            MultiAdapter(
                                                device = potentialMultiDevice,
                                                midiContainer = null,
                                                instrumentContainer = instrumentContainer,
                                                drumContainer = drumContainer,
                                                offset = offset,
                                                outputOffset = outputOffset,
                                                chainDepth = chainDepth,
                                            ).toDeviceStates()
                                        } else if (kaskobiMultiHashMatches) {
                                            MultiEffectAdapter(
                                                device = potentialMultiDevice,
                                                midiContainer = null,
                                                instrumentContainer = instrumentContainer,
                                                drumContainer = drumContainer,
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

                                return@apply
                            } else if (randomDevice != null && anyContainerPresent) {
                                println("Found random and container, using RandomDeviceMultisamplingAdapter")
                                addAll(
                                    try {
                                        RandomDeviceMultisamplingAdapter(
                                            random = randomDevice,
                                            midiContainer = null,
                                            instrumentContainer = instrumentContainer,
                                            drumContainer = drumContainer
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
                                    chainDepth = chainDepth,
                                    isInsideDrumRack = true,
                                )?.toDeviceStates() ?: emptyList()
                            }
                        )
                    }
                        .withMuteState(branch.masterDevice.speaker.manual.value)
                        .withAbletonDrumChoke(
                            chokeGroup = chokeGroup,
                            chokeScopeId = drumRackChokeScopeId,
                            chokeOwnerId = chokeOwnerId,
                        )
                )
            )
        }.toMutableList()

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

private fun List<DeviceState>.withAbletonDrumChoke(
    chokeGroup: Int,
    chokeScopeId: String,
    chokeOwnerId: String,
): List<DeviceState> {
    if (chokeGroup !in 1..16) return this
    return map { state ->
        state.withAbletonDrumChoke(chokeGroup, chokeScopeId, chokeOwnerId)
    }
}

private fun DeviceState.withAbletonDrumChoke(
    chokeGroup: Int,
    chokeScopeId: String,
    chokeOwnerId: String,
): DeviceState = when (this) {
    is SampleChainDeviceState -> preserveDisplayState(
        copy(
            chokeGroup = chokeGroup,
            chokeScopeId = chokeScopeId,
            chokeOwnerId = chokeOwnerId,
        ),
    )

    is GroupChainDeviceState -> preserveDisplayState(
        copy(
            groups = groups.map { group ->
                group.copy(
                    stateChain = group.stateChain.withAbletonDrumChoke(
                        chokeGroup,
                        chokeScopeId,
                        chokeOwnerId,
                    ),
                )
            },
        ),
    )

    is MultiGroupChainDeviceState -> preserveDisplayState(
        copy(
            groups = groups.map { group ->
                group.copy(
                    stateChain = group.stateChain.withAbletonDrumChoke(
                        chokeGroup,
                        chokeScopeId,
                        chokeOwnerId,
                    ),
                )
            },
            preprocessChain = preprocessChain.withAbletonDrumChoke(
                chokeGroup,
                chokeScopeId,
                chokeOwnerId,
            ),
        ),
    )

    is ChokeChainDeviceState -> preserveDisplayState(
        copy(
            stateChain = stateChain.withAbletonDrumChoke(
                chokeGroup,
                chokeScopeId,
                chokeOwnerId,
            ),
        ),
    )

    is MaskChainDeviceState -> preserveDisplayState(
        copy(
            colorStateChain = colorStateChain.withAbletonDrumChoke(
                chokeGroup,
                chokeScopeId,
                chokeOwnerId,
            ),
            shapeStateChain = shapeStateChain.withAbletonDrumChoke(
                chokeGroup,
                chokeScopeId,
                chokeOwnerId,
            ),
        ),
    )

    else -> this
}

private fun StateChain.withAbletonDrumChoke(
    chokeGroup: Int,
    chokeScopeId: String,
    chokeOwnerId: String,
): StateChain = copy(
    devices = devices.withAbletonDrumChoke(chokeGroup, chokeScopeId, chokeOwnerId),
)

private fun <T : DeviceState> DeviceState.preserveDisplayState(copy: T): T = copy.also {
    it.isMuted = isMuted
    it.isCollapsed = isCollapsed
}
