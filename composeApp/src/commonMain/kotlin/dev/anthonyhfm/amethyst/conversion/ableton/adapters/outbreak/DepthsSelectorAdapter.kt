package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayout
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DepthsSelectorAdapter(
    private val blob: String,
    private val offset: IntOffset
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val dataObj: DepthsSelectorData = jsonDecoder.decodeFromString(blob)

        if (AbletonConverter.projectLayout is AbletonLayout.Dual2Light) {
            if (dataObj.channelField.isNotEmpty()) {
                val offset = DepthsMixerAdapter.mixerReceivers[dataObj.channelField.first()]?.let {
                    if (it == null) {
                        return@let IntOffset.Zero
                    } else {
                        if (it == IntOffset.Zero && offset != IntOffset.Zero) {
                            return@let -offset
                        } else {
                            return@let it
                        }
                    }
                }

                println("Found selector with channel ${dataObj.channelField.first()} at offset $offset")

                return listOf(
                    OffsetChainDeviceState(offsetX = offset!!.x, offsetY = offset!!.y),
                    LayerChainDeviceState(layer = dataObj.layerField.first())
                )
            }
        }

        return listOf(LayerChainDeviceState(layer = dataObj.layerField.first()))
    }

    @Serializable
    data class DepthsSelectorData(
        @SerialName("live.numbox")
        val channelField: List<Int> = listOf(0),

        @SerialName("live.numbox[1]")
        val layerField: List<Int> = listOf(0),
    )
}