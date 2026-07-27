package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class WormholeLiteAdapter(
    private val device: MxDeviceMidiEffect,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data: WormholeLiteData = jsonDecoder.decodeFromString(device.decodeBlob())

        return listOf(
            MacroControlChainDeviceState(
                value = data.pageNumber.first().toInt() - 1
            )
        )
    }
}

@Serializable
private data class WormholeLiteData(
    @SerialName("live.numbox")
    val pageNumber: List<Float>
)