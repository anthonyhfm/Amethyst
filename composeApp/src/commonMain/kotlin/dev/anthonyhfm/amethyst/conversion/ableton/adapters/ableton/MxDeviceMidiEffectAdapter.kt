package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.LPXPagesAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.GenericMidiExtAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.Resonator2Adapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DelayAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DepthsSelectorAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.InfinityAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.IrisAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.TwistAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.devices.DeviceState

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

        when (AbletonConverter.liveVersion) {
            AbletonConverter.LiveVersion.LIVE_11 -> {
                val fileRef = patchSlot.querySelector("FileRef")[0]

                fileSize = fileRef.querySelector("OriginalFileSize")[0].attributes["Value"]?.toInt() ?: 0
                crc = fileRef.querySelector("OriginalCrc")[0].attributes["Value"]?.toInt() ?: 0
            }

            else -> {
                val fileRef = patchSlot.localQuerySelector("FileRef")[0]
                val searchHint = fileRef.localQuerySelector("SearchHint")[0]

                fileSize = searchHint.localQuerySelector("FileSize")[0].attributes["Value"]?.toInt() ?: 0
                crc = searchHint.localQuerySelector("Crc")[0].attributes["Value"]?.toInt() ?: 0
            }
        }

        when (MaxDeviceMatcher(fileSize, crc)) {
            MaxDeviceMatcher(55316, 55855) -> { // Depths Selector
                return DepthsSelectorAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            MaxDeviceMatcher(53085, 10065) -> { // Infinity
                return InfinityAdapter().toDeviceStates()
            }

            MaxDeviceMatcher(157993, 26896) -> {
                return IrisAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            MaxDeviceMatcher(453469, 2928),
            MaxDeviceMatcher(380840, 40553) -> {
                return TwistAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            MaxDeviceMatcher(80022, 36805) -> {
                return DelayAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            MaxDeviceMatcher(91230, 33545),
            MaxDeviceMatcher(134927, 42016),
            MaxDeviceMatcher(23292, 61071),
            MaxDeviceMatcher(134924, 38265),
            MaxDeviceMatcher(159503, 62613),-> {
                return GenericMidiExtAdapter(xml).toDeviceStates()
            }

            MaxDeviceMatcher(758577, 2479),
            MaxDeviceMatcher(123814, 44049) -> {
                return LPXPagesAdapter().toDeviceStates()
            }

            MaxDeviceMatcher(1105205, 43348) -> { // Resonator v2
                return Resonator2Adapter(readDataBlob(blob.text!!), xml).toDeviceStates()
            }

            else -> {
                when (AbletonConverter.liveVersion) {
                    AbletonConverter.LiveVersion.LIVE_11 -> {
                        val name = patchSlot.localQuerySelector("FileRef")[0].querySelector("Path")[0].attributes["Value"]?.split("/")?.last()
                        println("Max device not supported: $name. $fileSize, CRC: $crc")
                    }

                    else -> {
                        val name = xml.querySelector("Name")[0].attributes["Value"]
                        println("Max device not supported: $name. File size: $fileSize, CRC: $crc")
                    }
                }

                return emptyList()
            }
        }
    }

    data class MaxDeviceMatcher(
        val fileSize: Int,
        val crc: Int
    )

    companion object {
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