package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.devices.DeviceState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GridFilterAdapter(
    private val blob: String,
    val offset: IntOffset = IntOffset.Zero,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data = jsonDecoder.decodeFromString<GridFilterData>(blob)

        return listOf(
            AbletonConverter.coordinateFilter(
                launchpad = AbletonConverter.launchpadTarget(offset),
                localCoordinates = data.matrixctrl.chunked(3).map { list ->
                    // For some reason, the third data block is always "1.0". Maybe it has something to do with the color in max or some shit
                    val x = list[0].toInt()
                    val y = list[1].toInt()

                    Pair(x, y)
                },
            )
        )
    }

    @Serializable
    data class GridFilterData(
        /**
         * Max saves values both as int and double in the same json array. Using Float to cover both cases
         */
        val matrixctrl: List<Float>,
    )
}
