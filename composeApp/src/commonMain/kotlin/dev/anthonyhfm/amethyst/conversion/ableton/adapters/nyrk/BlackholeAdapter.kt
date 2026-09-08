package dev.anthonyhfm.amethyst.conversion.ableton.adapters.nyrk

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class BlackholeAdapter(
    private val device: MxDeviceMidiEffect
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data = jsonDecoder.decodeFromString<BlackholeData>(device.decodeBlob())

        return listOf(
            MacroControlChainDeviceState(
                value = data.macroValue.first().toInt()
            )
        )
    }

    @Serializable
    private data class BlackholeData(
        @SerialName("number[15]")
        val macroValue: List<Float>
    )
}