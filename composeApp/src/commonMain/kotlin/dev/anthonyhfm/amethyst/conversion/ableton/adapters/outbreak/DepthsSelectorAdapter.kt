package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayout
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class DepthsSelectorAdapter(
    private val blob: String,
    private val offset: IntOffset,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val dataObj: DepthsSelectorData = jsonDecoder.decodeFromString(blob)

        if (AbletonConverter.projectLayout is AbletonLayout.Dual2Light) {
            val channel = dataObj.channelField.firstOrNull()
            val receiverOffset = channel?.let(DepthsMixerAdapter.mixerReceivers::get)

            if (channel != null && receiverOffset != null) {
                val relativeOffset = relativeReceiverOffset(
                    selectorOffset = offset,
                    receiverOffset = receiverOffset,
                )

                println("Found selector with channel $channel at relative offset $relativeOffset")

                return listOf(
                    OffsetChainDeviceState(
                        offsetX = relativeOffset.x,
                        offsetY = relativeOffset.y,
                    ),
                    LayerChainDeviceState(layer = dataObj.layerField.first()),
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

    companion object {
        internal fun relativeReceiverOffset(
            selectorOffset: IntOffset,
            receiverOffset: IntOffset,
        ): IntOffset = receiverOffset - selectorOffset
    }
}
