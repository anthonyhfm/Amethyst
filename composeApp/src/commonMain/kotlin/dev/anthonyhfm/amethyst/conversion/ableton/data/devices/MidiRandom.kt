package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MidiRandom(
    @SerialName("Id")
    val id: Int
) : AbletonDevice