package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState

class OriginalSimplerAdapter(
    private val device: OriginalSimpler,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data = getSimplerData(device)

        return listOf(
            AbletonConverter.audioMap[data] ?: ClipChainDeviceState()
        )
    }

    data class OriginalSimplerData(
        val filePath: String,
        val sampleStart: Long,
        val sampleEnd: Long
    )

    companion object {
        fun getSimplerData(data: OriginalSimpler): OriginalSimplerData {
            val samplePart = data
                .player
                .multiSampleMap
                .sampleParts
                .multiSamplePart ?: return OriginalSimplerData("", 0, 0)

            return OriginalSimplerData(
                filePath = samplePart.sampleRef.fileRef.resolvePath(),
                sampleStart = samplePart.sampleStart.value,
                sampleEnd = samplePart.sampleEnd.value
            )
        }
    }
}