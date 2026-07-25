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
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import io.github.vinceglb.filekit.PlatformFile

class DrumGroupDeviceAdapter(
    private val device: DrumGroupDevice,
    val offset: IntOffset = IntOffset.Zero,
    val outputOffset: IntOffset = IntOffset.Zero,
    private val chainDepth: Int = 0
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val branches: List<DrumGroupDevice.Branches.DrumBranch> = device.branches.branches

        return listOf(
            GroupChainDeviceState(
                groups = branches.mapIndexed { index, branch ->
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
                                    CoordinateFilterChainDeviceState(
                                        filters = listOf(
                                            Pair(x + offset.x,  y + offset.y)
                                        )
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
                                                        drumContainer = drumContainer
                                                    ).toDeviceStates()
                                                } else if (kaskobiMultiHashMatches) {
                                                    MultiEffectAdapter(
                                                        device = potentialMultiDevice,
                                                        midiContainer = null,
                                                        instrumentContainer = instrumentContainer,
                                                        drumContainer = drumContainer
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
                                            chainDepth = chainDepth + 1
                                        )?.toDeviceStates() ?: emptyList()
                                    }
                                )
                            }
                        )
                    )
                }
            )
        ).withMuteState(device.on.manual.value)
    }
}