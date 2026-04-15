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

        return listOf(
            AbletonConverter.audioMap[data]?.copy(
                fadeInMs = convertWeirdFuckingFloatValues(device.volumeAndPan.oneShotEnvelope.fadeInTime.manual.value).toFloat(),
                fadeOutMs = convertWeirdFuckingFloatValues(device.volumeAndPan.oneShotEnvelope.fadeOutTime.manual.value).toFloat()
            ) ?: SampleChainDeviceState()
        )
    }

    data class OriginalSimplerData(
        val filePath: String,
        val sampleStart: Long,
        val sampleEnd: Long,
    )

    companion object {
        private val RAW_REF_100 = 719.005432
        private val LN2 = kotlin.math.ln(2.0)
        private val B = 200.0 / LN2
        private val A = RAW_REF_100 - B * kotlin.math.ln(100.0)

        private val IDENTITY_ANCHORS = doubleArrayOf(10.0, 2000.0)
        private val EPS = 1e-6

        private fun convertWeirdFuckingFloatValues(raw: Float): Double {
            if (IDENTITY_ANCHORS.any { kotlin.math.abs(raw - it) <= EPS }) return raw.toDouble()
            return kotlin.math.exp((raw - A) / B)
        }

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