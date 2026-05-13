package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.utils.rythmIndexToDuration
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class AutoPageAdapter(
    val blob: String,
    val xml: MxDevice
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data = jsonDecoder.decodeFromString<AutoPageData>(blob)

        return mutableListOf<DeviceState>().apply {
            val time = timeSplits[data.delay.first().toInt()]

            if (data.delay.first() != 0f) {
                add(
                    DelayChainDeviceState(
                        timing = Timing.Duration(
                            rythmIndexToDuration(
                                timing = "${time.first}/${time.second}",
                                bpm = AbletonConverter.bpm,
                                steps = 1
                            )
                        )
                    )
                )
            }

            add(
                MacroControlChainDeviceState(
                    macro = 0,
                    value = data.targetPage.first() - 1
                )
            )
        }
    }

    val timeSplits = listOf<Pair<Int, Int>>(
        Pair(0, 0),
        Pair(1, 1024),
        Pair(1, 512),
        Pair(1, 256),
        Pair(1, 128),
        Pair(1, 64),
        Pair(1, 32),
        Pair(1, 16),
        Pair(1, 8),
        Pair(1, 4),
        Pair(1, 2),
        Pair(1, 1),
        Pair(2, 1),
        Pair(4, 1),
    )

    @Serializable
    data class AutoPageData(
        @SerialName("live.numbox")
        val targetPage: List<Int>,

        @SerialName("live.numbox[43]")
        val delay: List<Float>
    )
}