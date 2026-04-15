package dev.anthonyhfm.amethyst.gem

import dev.anthonyhfm.amethyst.core.util.Timing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface GemValueType {
    @Serializable
    @SerialName("number")
    data object Number : GemValueType

    @Serializable
    @SerialName("boolean")
    data object Boolean : GemValueType

    @Serializable
    @SerialName("enum")
    data class Enum(
        val definition: GemEnumDefinition
    ) : GemValueType

    @Serializable
    @SerialName("color")
    data object Color : GemValueType

    @Serializable
    @SerialName("timing")
    data object Timing : GemValueType
}

@Serializable
data class GemEnumDefinition(
    val id: String,
    val label: String = id,
    val options: List<GemEnumOption> = emptyList()
)

@Serializable
data class GemEnumOption(
    val id: String,
    val label: String = id
)

@Serializable
sealed interface GemValue {
    @Serializable
    @SerialName("number")
    data class Number(
        val value: Double
    ) : GemValue

    @Serializable
    @SerialName("boolean")
    data class Boolean(
        val value: kotlin.Boolean
    ) : GemValue

    @Serializable
    @SerialName("enum")
    data class Enum(
        val enumId: String,
        val optionId: String
    ) : GemValue

    @Serializable
    @SerialName("color")
    data class Color(
        val value: GemColor
    ) : GemValue

    @Serializable
    @SerialName("timing")
    data class TimingValue(
        val value: Timing
    ) : GemValue
}

@Serializable
data class GemColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f
)
