package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.GenericMidiExtAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.PageSwitcherAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.Resonator2Adapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.nev.WormholeAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DelayAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DepthsSelectorAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.FlipAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.InfinityAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.IrisAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.TwistAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.FileRef
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.conversion.ableton.utils.getFileHash
import dev.anthonyhfm.amethyst.devices.DeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.nameWithoutExtension

class MxDeviceMidiEffectAdapter(
    private val xml: XmlElement
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
                val maxFile = PlatformFile(path)
                val hash = maxFile.getFileHash()

                fileHashMap[path] = hash

                return@let hash
            }
        }

        when (hash) {
            "e8726f6b3088125c4c6aaff083b1730b",
            "9a7f0ac3bc4d354c2a560427b6093f87"-> {
                return TwistAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            "14783922241a74cd4da95beed0f57b95",
            "349064d3d33e7ed2c39d766f308aa023" -> {
                return DelayAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            "811c3c410fce75959c8ab17220186701" -> {
                return FlipAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            "bcfb325a212a70bdd0acdbf740114389",
            "a591e409a908e4bd1898152222cc8336"-> {
                return IrisAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            "7bd5bf9ea8431c5697b226aa906d87ac",
            "af7c8717c232587ecea9ee2105eca17c" -> {
                return DepthsSelectorAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            "4daa43e6e4704693794cb14a33cc00fa" -> {
                return InfinityAdapter().toDeviceStates()
            }

            "feecaed62c2637a73325446a1ed1e25e",
            "32b6bec96552a6e40f6743787a20b9df" -> {
                return PageSwitcherAdapter(blob.text!!).toDeviceStates()
            }

            "d8c48c67824319295bb5bf7abda47f27" -> {
                return Resonator2Adapter(readDataBlob(blob.text!!), xml).toDeviceStates()
            }

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
            "d53dcb292a173ab7853183f3cab7620c"-> {
                return GenericMidiExtAdapter(xml).toDeviceStates()
            }

            "3d3de9b05506f279ad6cfe14d26e0084" -> {
                return WormholeAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            else -> {
                val maxFile = PlatformFile(path)

                println("Max device not supported: ${maxFile.nameWithoutExtension} - Hash: $hash")

                return emptyList()
            }
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