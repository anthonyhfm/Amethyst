package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MidiRandom(
    @SerialName("Id")
    val id: Int,

    val chance: Chance,
    val choices: Choices,
    val alternate: Alternate
) : AbletonDevice {
    @Serializable
    data class Chance(
        val manual: AbletonManual<Double>
    )

    @Serializable
    data class Choices(
        val manual: AbletonManual<Double>
    )

    @Serializable
    data class Alternate(
        val manual: AbletonManual<Boolean>
    )
}