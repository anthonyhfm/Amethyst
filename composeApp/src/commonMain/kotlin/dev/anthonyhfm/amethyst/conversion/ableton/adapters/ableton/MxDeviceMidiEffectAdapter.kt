package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.GenericMidiExtAdapter
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

        var fileSize: Int = 0
        var crc: Int = 0

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
            /*MaxDeviceMatcher(55316, 55855),
            MaxDeviceMatcher(35337, 2349), -> { // Depths Selector
                return DepthsSelectorAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            MaxDeviceMatcher(53085, 10065) -> { // Infinity
                return InfinityAdapter().toDeviceStates()
            }

            MaxDeviceMatcher(157993, 26896) -> {
                return IrisAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            MaxDeviceMatcher(453469, 2928),
            MaxDeviceMatcher(387865, 29354),
            MaxDeviceMatcher(380840, 40553) -> {
                return TwistAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            MaxDeviceMatcher(47076, 64779),
            MaxDeviceMatcher(59062, 30081),
            MaxDeviceMatcher(79581, 51271),
            MaxDeviceMatcher(80022, 36805) -> {
                return DelayAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            MaxDeviceMatcher(148081, 63576),
            MaxDeviceMatcher(147568, 4278),
            MaxDeviceMatcher(138711, 3885),
            MaxDeviceMatcher(135201, 64959),
            MaxDeviceMatcher(134829, 39407),
            MaxDeviceMatcher(134637, 11782),
            MaxDeviceMatcher(111310, 39693) -> {
                return FlipAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            MaxDeviceMatcher(91230, 33545),
            MaxDeviceMatcher(134927, 42016),
            MaxDeviceMatcher(23292, 61071),
            MaxDeviceMatcher(134924, 38265),
            MaxDeviceMatcher(47971, 51197),
            MaxDeviceMatcher(54578, 48303),
            MaxDeviceMatcher(159503, 62613),-> {
                return GenericMidiExtAdapter(xml).toDeviceStates()
            }

            MaxDeviceMatcher(1105205, 43348) -> { // Resonator v2
                return Resonator2Adapter(readDataBlob(blob.text!!), xml).toDeviceStates()
            }*/

            "25a0f03868c45af4d06bcead0a1bc6ce" -> {
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

    data class MaxDeviceMatcher(
        val fileSize: Int,
        val crc: Int
    )

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