package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter.Companion.readDataBlob
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.utils.MultiPluginHashes.MULTI_HASHES
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.MultiAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.FileRef
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.conversion.ableton.utils.getFileHash
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import io.github.vinceglb.filekit.PlatformFile

class DrumGroupDeviceAdapter(
    private val xml: XmlElement,
    val offset: IntOffset = IntOffset.Zero,
    val outputOffset: IntOffset = IntOffset.Zero
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
                                    val potentialMultiDevice = branchElements.find {
                                        it.name == "MxDeviceMidiEffect"
                                    }
                                    val patchSlot = potentialMultiDevice?.localQuerySelector("PatchSlot")[0]
                                        ?.localQuerySelector("Value")[0]
                                        ?.localQuerySelector("MxDPatchRef")[0]
                                    val potentialMultiDeviceHash = potentialMultiDevice.let {
                                        if (patchSlot?.localQuerySelector("FileRef")?.isEmpty() == true) return@let null

                                        val path = FileRef.resolveFileReference(patchSlot?.localQuerySelector("FileRef")?.first() ?: return@let null)
                                        val file = PlatformFile(path)
                                        val hash = file.getFileHash()
                                        hash
                                    }
                                    val multiHashMatches = MULTI_HASHES.contains(potentialMultiDeviceHash)

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
                                            resolveAdapter(
                                                xml = child,
                                                offset = offset,
                                                outputOffset = outputOffset
                                            )?.toDeviceStates()?.firstOrNull()
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