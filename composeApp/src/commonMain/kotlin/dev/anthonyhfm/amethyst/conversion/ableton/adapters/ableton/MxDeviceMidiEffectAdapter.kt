package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.LPXPagesAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.GenericMidiExtAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DepthsSelectorAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.devices.DeviceState

class MxDeviceMidiEffectAdapter(
    private val xml: XmlElement
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val name = xml.querySelector("Name")[0].attributes["Value"]
        val patchSlot = xml.localQuerySelector("PatchSlot")[0]
            .localQuerySelector("Value")[0]
            .localQuerySelector("MxDPatchRef")[0]

        val blob = xml.localQuerySelector("BlobSlot")[0]
            .localQuerySelector("Value")[0]
            .localQuerySelector("MxDBlob")[0]
            .localQuerySelector("Blob")[0]

        val fileRef = patchSlot.localQuerySelector("FileRef")[0]
        val searchHint = fileRef.localQuerySelector("SearchHint")[0]

        val fileSize: Int = searchHint.localQuerySelector("FileSize")[0].attributes["Value"]?.toInt() ?: 0
        val crc: Int = searchHint.localQuerySelector("Crc")[0].attributes["Value"]?.toInt() ?: 0

        when (MaxDeviceMatcher(fileSize, crc)) {
            MaxDeviceMatcher(55316, 55855) -> { // Depths Selector
                return DepthsSelectorAdapter(readDataBlob(blob.text!!)).toDeviceStates()
            }

            MaxDeviceMatcher(134927, 42016),
            MaxDeviceMatcher(23292, 61071),
            MaxDeviceMatcher(134924, 38265),
            MaxDeviceMatcher(159503, 62613),-> { // Generic MidiExt
                return GenericMidiExtAdapter(xml).toDeviceStates()
            }

            MaxDeviceMatcher(758577, 2479) -> {
                return LPXPagesAdapter().toDeviceStates()
            }

            else -> {
                println("Max device not supported: $name. File size: $fileSize, CRC: $crc")
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