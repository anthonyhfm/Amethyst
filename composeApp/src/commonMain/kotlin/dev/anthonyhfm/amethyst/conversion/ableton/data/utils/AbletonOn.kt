package dev.anthonyhfm.amethyst.conversion.ableton.data.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement

@Serializable
@SerialName("On")
data class AbletonOn(
    @XmlElement
    val manual: AbletonManual<Boolean> = AbletonManual(true)
)
