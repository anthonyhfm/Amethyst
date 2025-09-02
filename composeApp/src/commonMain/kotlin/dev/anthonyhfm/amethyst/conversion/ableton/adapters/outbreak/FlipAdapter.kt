package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import androidx.compose.ui.graphics.TileMode
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FlipAdapter(
    private val data: ByteArray
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
         val dataObj: FlipData = Json {
            ignoreUnknownKeys = true
        }.decodeFromString(data.decodeToString())

        when (dataObj.flipMode.first().toInt()) {
            1 -> { // Mirror
                return listOf(
                    FlipChainDeviceState(
                        mode = when (dataObj.mirrorMode.first().toInt()) {
                            0 -> FlipChainDeviceState.FlipMode.HORIZONTAL
                            1 -> FlipChainDeviceState.FlipMode.VERTICAL
                            else -> FlipChainDeviceState.FlipMode.HORIZONTAL
                        },
                        bypass = dataObj.bypassEnabled.first().toInt() == 1
                    )
                )
            }

            2 -> { // Rotate
                return listOf(
                    RotateChainDeviceState(
                        mode = when (dataObj.rotateMode.first().toInt()) {
                            0 -> RotateChainDeviceState.RotateMode.DEGREES_90
                            1 -> RotateChainDeviceState.RotateMode.DEGREES_180
                            2 -> RotateChainDeviceState.RotateMode.DEGREES_270
                            else -> RotateChainDeviceState.RotateMode.DEGREES_90
                        },
                        bypass = dataObj.bypassEnabled.first().toInt() == 1
                    )
                )
            }

            else -> {
                return emptyList()
            }
        }
    }

    @Serializable
    data class FlipData(
        @SerialName("number")
        val flipMode: List<Double>,

        @SerialName("pictctrl")
        val bypassEnabled: List<Double>,

        @SerialName("umenu")
        val mirrorMode: List<Double>,

        @SerialName("umenu[1]")
        val rotateMode: List<Double>,
    )
}