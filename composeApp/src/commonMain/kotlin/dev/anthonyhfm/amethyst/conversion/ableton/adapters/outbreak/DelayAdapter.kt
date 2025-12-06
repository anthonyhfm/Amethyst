package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.utils.rythmIndexToDuration
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

class DelayAdapter(
    private val blob: String
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val dataObj: DelayData = jsonDecoder.decodeFromString(blob)

        val bpm = AbletonConverter.bpm

        val delayMs = if (dataObj.freeModeEnabled.first().toInt() == 1) {
            dataObj.rateMs.first().toLong()
        } else {
            val timingString = delayRateIndexToTiming(dataObj.rateIndex.first().toInt()) ?: "1/8"
            rythmIndexToDuration(timingString, bpm, 1).inWholeMilliseconds
        }

        return listOf(
            DelayChainDeviceState(
                timing = Timing.Duration(delayMs.milliseconds),
                delayMs = delayMs,
                gate = (dataObj.gatePercentage.first().toFloat() / 100.0f) * 0.5f,
            )
        )
    }

    private fun delayRateIndexToTiming(index: Int): String? {
        return when (index) {
            0 -> "1/128"
            1 -> "1/64"
            2 -> "1/32"
            3 -> "1/16"
            4 -> "1/8"
            5 -> "1/4"
            6 -> "1/2"
            7 -> "1/1"
            else -> "1/8" // Default to 1/8 if unknown
        }
    }

    @Serializable
    data class DelayData(
        @SerialName("Rate")
        val rateIndex: List<Double>,

        @SerialName("Rate[1]")
        val gatePercentage: List<Double>,

        @SerialName("Rate[2]")
        val rateMs: List<Double>,

        @SerialName("live.text")
        val freeModeEnabled: List<Double>,
    )
}