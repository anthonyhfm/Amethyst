package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("VelocityRange")
data class AbletonRackVelocityRange(
    @XmlElement
    @XmlSerialName("Min")
    val min: Boundary = Boundary(value = 1),
    @XmlElement
    @XmlSerialName("Max")
    val max: Boundary = Boundary(value = 127),
) {
    @Serializable
    data class Boundary(
        @SerialName("Value")
        val value: Int,
    )
}
