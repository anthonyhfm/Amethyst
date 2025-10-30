package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.utils.MultiPluginHashes.MULTI_HASHES
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.GenericMidiExtAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.PageSwitcherAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.Resonator2Adapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.nev.WormholeAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DelayAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DepthsMixerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DepthsSelectorAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.FlipAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.InfinityAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.IrisAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.TwistAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.FileRef
import dev.anthonyhfm.amethyst.conversion.ableton.utils.ProjectSpecials
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.conversion.ableton.utils.getFileHash
import dev.anthonyhfm.amethyst.conversion.ableton.utils.toFileHash
import dev.anthonyhfm.amethyst.devices.DeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.nameWithoutExtension

class MxDeviceMidiEffectAdapter(
    private val xml: XmlElement,
    val offset: IntOffset = IntOffset.Zero,
    val outputOffset: IntOffset = IntOffset.Zero
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val patchSlot = xml.localQuerySelector("PatchSlot")[0]
            .localQuerySelector("Value")[0]
            .localQuerySelector("MxDPatchRef")[0]

        val blob = xml.localQuerySelector("BlobSlot")[0]
            .localQuerySelector("Value")[0]
            .localQuerySelector("MxDBlob")[0]
            .localQuerySelector("Blob")[0]

        val path = FileRef.resolveFileReference(patchSlot.localQuerySelector("FileRef").first())

        val hash: String = fileHashMap[path].let {
            if (it != null) {
                return@let it
            } else {
                val hash: String = if (AbletonConverter.isZip) {
                    AbletonConverter.zipEntries[path]?.data?.toFileHash() ?: ""
                } else {
                    val maxFile = PlatformFile(path)
                    maxFile.getFileHash()
                }

                fileHashMap[path] = hash

                return@let hash
            }
        }

        try {
            when (hash) {
                "bd5d1649fed009d5399a9e8e84a0b036",
                "1e55e95bbcbd08f6f6739b235b5da30b",
                "cc9e206793622e0a03041c05ec7ed8ca",
                "306012649f1a5eca6e6b4e5dca3f6159",
                "a6d42dc12c6529eb4ff4be9f6a0cef71",
                "fe1b005361b8f099ca487aae25cc16d6",
                "fe575828d488675752a087c80401af63",
                "e8726f6b3088125c4c6aaff083b1730b",
                "9a7f0ac3bc4d354c2a560427b6093f87"-> {
                    return TwistAdapter(readDataBlob(blob.text!!)).toDeviceStates()
                }

                "14783922241a74cd4da95beed0f57b95",
                "349064d3d33e7ed2c39d766f308aa023" -> {
                    return DelayAdapter(readDataBlob(blob.text!!)).toDeviceStates()
                }

                "4ca220e9b84d740854eb8e1b00843265",
                "1140e1f12d76876e36441b17f9b0f189",
                "8f88406409ddaf7dd8413e1b5e01f859",
                "e15e0b9e248adfeeb57d7f9c54a978d8",
                "dfb2c1c6968c8547b26efd31c69dd555",
                "620a46951d419c62150da25467ec044b",
                "811c3c410fce75959c8ab17220186701" -> {
                    return FlipAdapter(readDataBlob(blob.text!!)).toDeviceStates()
                }

                // "1123f889d9f60562ca63945c5a823665", NEEDS EXTRA HANDLING, OLDER VERSION
                "4ab1f1c29a3ac9e447756a9d075c2a6b",
                "4feeb78db6367007b1badf8f9d2c1cae",
                "bcfb325a212a70bdd0acdbf740114389",
                "a591e409a908e4bd1898152222cc8336"-> {
                    return IrisAdapter(readDataBlob(blob.text!!)).toDeviceStates()
                }

                "7bd5bf9ea8431c5697b226aa906d87ac",
                "af7c8717c232587ecea9ee2105eca17c" -> {
                    return DepthsSelectorAdapter(readDataBlob(blob.text!!), offset).toDeviceStates()
                }

                "07b41f57975c3f6b65b37be548c23377",
                "db42168ef06e75ab599a30570c565b20",
                "5ef8dd91805de9f50000565f21d1485c",
                "4daa43e6e4704693794cb14a33cc00fa" -> {
                    return InfinityAdapter().toDeviceStates()
                }

                "feecaed62c2637a73325446a1ed1e25e" -> {
                    println("Pager hash detected: $hash")
                    AbletonConverter.special = ProjectSpecials(
                        useKaskobiPageSwitcher = true
                    )

                    return emptyList()
                }

                "d8c48c67824319295bb5bf7abda47f27" -> {
                    return Resonator2Adapter(readDataBlob(blob.text!!), xml).toDeviceStates()
                }

                "2491c3c841b70b7c9765db8e4defdfff",
                "8131f56b0fed3013999374d7e27b0ae0",
                "433a3efd3c017d799519fc80ec31a53d",
                "e10191145ac3fcc5c9de4bc5e6997764",
                "7295b9ab0878170b1080e2a89feed177",
                "7aed2a19a3776486492e9abc1307d8ba",
                "031356ce98ba9104607ea3e57c8fd37e",
                "8b7dc60359dadae0ef6755eddcbe0185",
                "25a0f03868c45af4d06bcead0a1bc6ce",
                "494a1455eac528aee9d94055dcdb4463",
                "aacfc91a06f6ce784ace184ad436da47",
                "e4a51582c69996d40a7dfb27c9c4a948",
                "247590e4be0b51ce925bba2aa1d2701d",
                "6557d723e39156749aa1b55e38c53995",
                "fa3f0f6b3af43ab4bd754010124f9dc7",
                "4b74eea1e5ee0db42171418717c02561",
                "4dd48ac60e858928fff89a28865ce735",
                "2ef098a53fe4e9a4b035588561080343",
                "d53dcb292a173ab7853183f3cab7620c"-> {
                    return GenericMidiExtAdapter(xml, offset).toDeviceStates()
                }

                "9f50358372279f946cae0fdac0cfbf56", // Wormhole Lite, unsure if this actually works!
                "3d3de9b05506f279ad6cfe14d26e0084" -> {
                    return WormholeAdapter(readDataBlob(blob.text!!)).toDeviceStates()
                }

                else -> {
                    val maxFile = PlatformFile(path)

                    if (!MULTI_HASHES.contains(hash)) { // Multi is handled in DrumGroupDeviceAdapter/MidiEffectGroupDeviceAdapter
                        println("Max device not supported: ${maxFile.nameWithoutExtension} - Hash: $hash")
                    }

                    return emptyList()
                }
            }
        } catch (e: Exception) {
            val maxFile = PlatformFile(path)

            println("Error while converting Max device ${maxFile.nameWithoutExtension} - Hash: $hash")

            return emptyList()
        }
    }

    companion object {
        val fileHashMap: MutableMap<String, String> = mutableMapOf()

        fun readDataBlob(blob: String): ByteArray {
            val cleanHex = blob.replace("\\s".toRegex(), "")
            require(cleanHex.length % 2 == 0) { "" }

            val raw = ByteArray(cleanHex.length / 2) { idx ->
                cleanHex
                    .substring(idx * 2, idx * 2 + 2)
                    .toInt(16)
                    .toByte()
            }

            val lastNonZero = raw.indexOfLast { it != 0.toByte() }
            return if (lastNonZero == -1) {
                ByteArray(0)
            } else {
                raw.copyOfRange(0, lastNonZero + 1)
            }
        }
    }
}