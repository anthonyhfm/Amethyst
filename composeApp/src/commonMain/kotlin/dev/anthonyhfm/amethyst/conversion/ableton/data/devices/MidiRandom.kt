package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement

@Serializable
data class MidiRandom(
    @SerialName("Id")
    val id: Int,

    @XmlElement
    val on: AbletonOn = AbletonOn(),

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