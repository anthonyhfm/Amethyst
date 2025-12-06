package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.DrumGroupDevice
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

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

                                /*// Multisampling logic
                                val branchElements = branch.querySelector("DeviceChain")[0]
                                    .querySelector("Devices")[0]
                                    .children

                                if (branchElements.size >= 2) {
                                    val potentialMultiDevice = branchElements.find {
                                        it.name == "MxDeviceMidiEffect"
                                    }
                                    val patchSlot = potentialMultiDevice?.localQuerySelector("PatchSlot")[0]
                                        ?.localQuerySelector("Value")[0]
                                        ?.localQuerySelector("MxDPatchRef")[0]
                                    val potentialMultiDeviceHash = potentialMultiDevice.let {
                                        if (patchSlot?.localQuerySelector("FileRef")?.isEmpty() == true) return@let null

                                        val path = FileRef.resolveFileReference(patchSlot?.localQuerySelector("FileRef")?.first() ?: return@let null)
                                        val hash = if (AbletonConverter.isZip) {
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
                                    val samplesContainer = branchElements.find {
                                        it.name == "InstrumentGroupDevice"
                                                || it.name == "DrumGroupDevice"
                                    }

                                    if (potentialMultiDevice != null && multiHashMatches && samplesContainer != null) {
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
                                                        containerXml = samplesContainer
                                                    ).toDeviceStates()
                                                } else if (kaskobiMultiHashMatches) {
                                                    MultiEffectAdapter(
                                                        deviceXml = potentialMultiDevice,
                                                        containerXml = samplesContainer
                                                    ).toDeviceStates()
                                                } else {
                                                    listOf()
                                                }
                                            } catch (e: Exception) {
                                                println("Error parsing Multi plugin with hash $potentialMultiDeviceHash")
                                                println("Error: ${e.message}")
                                                listOf()
                                            }
                                        )

                                        return@apply
                                    } else if (randomDevice != null && samplesContainer != null) {
                                        println("Found random and container, using RandomDeviceMultisamplingAdapter")
                                        addAll(
                                            try {
                                                RandomDeviceMultisamplingAdapter(
                                                    randomDeviceXml = randomDevice,
                                                    containerXml = samplesContainer
                                                ).toDeviceStates()
                                            } catch (e: Exception) {
                                                println("Error parsing Random multisampling")
                                                println("Error: ${e.message}")
                                                listOf()
                                            }
                                        )

                                        return@apply
                                    }
                                }*/

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
        )
    }
}