package dev.anthonyhfm.amethyst.conversion.ableton.data.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@SerialName("MidiControllerRange")
data class AbletonMidiControllerRange(
    @XmlElement
    @XmlSerialName("Min")
    val min: Endpoint,

    @XmlElement
    @XmlSerialName("Max")
    val max: Endpoint,
) {
    @Serializable
    data class Endpoint(
        @SerialName("Value")
        val value: Int,
    )
}
