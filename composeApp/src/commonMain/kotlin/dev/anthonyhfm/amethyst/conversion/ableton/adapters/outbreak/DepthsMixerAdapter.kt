package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayout
import dev.anthonyhfm.amethyst.devices.DeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DepthsMixerAdapter(
    private val blob: ByteArray,
    val offset: IntOffset = IntOffset.Zero,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        if (AbletonConverter.projectLayout is AbletonLayout.Dual2Light) {
            val data = Json.decodeFromString<DepthsMixerData>(blob.decodeToString())

            println("Found mixer with channel ${data.channel[0]} at offset $offset")

            mixerReceivers[data.channel[0]] = offset
        }

        return emptyList()
    }

    @Serializable
    data class DepthsMixerData(
        @SerialName("live.numbox")
        val channel: Array<Int> = arrayOf(0)
    )

    companion object {
        val mixerReceivers: MutableMap<Int, IntOffset> = mutableMapOf()
    }
}