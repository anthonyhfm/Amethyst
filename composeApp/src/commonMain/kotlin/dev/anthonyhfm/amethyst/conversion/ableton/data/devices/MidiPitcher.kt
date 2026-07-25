package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement

@Serializable
data class MidiPitcher(
    @SerialName("Id")
    val id: Int,

    @XmlElement
    val on: AbletonOn = AbletonOn(),

    val pitch: Pitch,
) : AbletonDevice {
    @Serializable
    data class Pitch(
        val manual: AbletonManual<Int>
    )
}