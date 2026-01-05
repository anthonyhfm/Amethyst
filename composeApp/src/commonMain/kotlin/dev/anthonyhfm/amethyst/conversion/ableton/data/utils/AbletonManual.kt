package dev.anthonyhfm.amethyst.conversion.ableton.data.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Manual")
data class AbletonManual<T>(
    @SerialName("Value")
    val value: T
)