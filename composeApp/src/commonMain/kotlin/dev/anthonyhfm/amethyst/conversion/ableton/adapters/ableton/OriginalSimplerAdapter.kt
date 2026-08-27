package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState

class OriginalSimplerAdapter(
    private val device: OriginalSimpler,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data = getSimplerData(device)

        val fadeInMs = device.volumeAndPan.oneShotEnvelope.fadeInTime.manual.value.coerceAtLeast(0f)
        val fadeOutMs = device.volumeAndPan.oneShotEnvelope.fadeOutTime.manual.value.coerceAtLeast(0f)

        return listOf(
            AbletonConverter.audioMap[data]?.copy(
                fadeInMs = fadeInMs,
                fadeOutMs = fadeOutMs
            ) ?: SampleChainDeviceState()
        ).withMuteState(device.on.manual.value)
    }

    data class OriginalSimplerData(
        val filePath: String,
        val sampleStart: Long,
        val sampleEnd: Long,
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