package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class FlipAdapter(
    private val blob: String
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
         val dataObj: FlipData = jsonDecoder.decodeFromString(blob)

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
                val (mode, angle) = when (dataObj.rotateMode.first().toInt()) {
                    0 -> Pair(RotateChainDeviceState.RotateMode.DEGREES_90, 90f)
                    1 -> Pair(RotateChainDeviceState.RotateMode.DEGREES_180, 180f)
                    2 -> Pair(RotateChainDeviceState.RotateMode.DEGREES_270, 270f)
                    else -> Pair(RotateChainDeviceState.RotateMode.DEGREES_90, 90f)
                }
                return listOf(
                    RotateChainDeviceState(
                        mode = mode,
                        angleDegrees = angle,
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