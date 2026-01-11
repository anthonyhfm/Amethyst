package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MidiVelocity(
    @SerialName("Id")
    val id: Int,
    val maxOut: MaxOut
) : AbletonDevice {
    @Serializable
    data class MaxOut(
        val manual: AbletonManual<Int>
    )
}