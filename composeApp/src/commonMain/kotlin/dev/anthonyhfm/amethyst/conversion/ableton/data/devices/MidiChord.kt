package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class MidiChord(
    @SerialName("Id")
    val id: Int,

    @XmlElement
    val on: AbletonOn = AbletonOn(),

    @XmlElement
    @XmlSerialName("Shift1")
    val shift1: Shift,

    @XmlElement
    @XmlSerialName("Shift2")
    val shift2: Shift,

    @XmlElement
    @XmlSerialName("Shift3")
    val shift3: Shift,

    @XmlElement
    @XmlSerialName("Shift4")
    val shift4: Shift,

    @XmlElement
    @XmlSerialName("Shift5")
    val shift5: Shift,

    @XmlElement
    @XmlSerialName("Shift6")
    val shift6: Shift,
) : AbletonDevice {
    @Serializable
    data class Shift(
        val manual: AbletonManual<Int>,
    )
}