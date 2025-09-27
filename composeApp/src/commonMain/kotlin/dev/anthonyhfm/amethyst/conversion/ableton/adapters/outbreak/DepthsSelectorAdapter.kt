package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DepthsSelectorAdapter(
    private val data: ByteArray
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val dataObj: DepthsSelectorData = Json {
            ignoreUnknownKeys = true
        }.decodeFromString(data.decodeToString())

        return listOf(LayerChainDeviceState(layer = dataObj.layerField.first()))
    }

    @Serializable
    data class DepthsSelectorData(
        @SerialName("live.numbox")
        val layerField: List<Int> = listOf(0),
    )
}