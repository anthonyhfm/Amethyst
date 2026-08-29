package dev.anthonyhfm.amethyst.core.parameter

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.math.ln

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ParameterAddress(
    @ProtoNumber(1)
    val deviceId: String,
    @ProtoNumber(2)
    val parameterId: String,
) {
    init {
        require(deviceId.isNotBlank())
        require(parameterId.isNotBlank())
    }
}

enum class ParameterScale {
    Linear,
    Logarithmic,
    Discrete,
}

data class ParameterSmoothing(
    val durationMs: Float,
) {
    init {
        require(durationMs.isFinite() && durationMs >= 0f)
    }

    companion object {
        val None = ParameterSmoothing(0f)
        val Default = ParameterSmoothing(12f)
    }
}

/** Shared metadata consumed by controls, mappings, automation and DSP snapshots. */
data class ParameterDescriptor(
    val id: String,
    val label: String,
    val unit: String = "",
    val minimum: Float = 0f,
    val maximum: Float = 1f,
    val defaultValue: Float = minimum,
    val scale: ParameterScale = ParameterScale.Linear,
    val snapPoints: List<Float> = emptyList(),
    val automatable: Boolean = true,
    val macroMappable: Boolean = true,
    val smoothing: ParameterSmoothing = ParameterSmoothing.Default,
    val formatter: (Float) -> String = { value ->
        if (unit.isBlank()) value.toString() else "$value $unit"
    },
    val parser: (String) -> Float? = { text ->
        text.removeSuffix(unit).trim().toFloatOrNull()
    },
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(minimum.isFinite() && maximum.isFinite() && minimum < maximum)
        require(defaultValue in minimum..maximum)
        require(snapPoints.all { it.isFinite() && it in minimum..maximum })
        if (scale == ParameterScale.Logarithmic) require(minimum > 0f)
    }

    fun clamp(value: Float): Float {
        val finite = value.takeIf(Float::isFinite) ?: defaultValue
        if (snapPoints.isEmpty()) return finite.coerceIn(minimum, maximum)
        return snapPoints.minBy { kotlin.math.abs(it - finite) }
    }

    fun normalize(value: Float): Float {
        val clamped = clamp(value)
        return when (scale) {
            ParameterScale.Logarithmic ->
                ((ln(clamped) - ln(minimum)) / (ln(maximum) - ln(minimum))).coerceIn(0f, 1f)
            else -> ((clamped - minimum) / (maximum - minimum)).coerceIn(0f, 1f)
        }
    }

    fun denormalize(value: Float): Float {
        val normalized = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: normalize(defaultValue)
        val native = when (scale) {
            ParameterScale.Logarithmic ->
                kotlin.math.exp(ln(minimum) + normalized * (ln(maximum) - ln(minimum)))
            else -> minimum + normalized * (maximum - minimum)
        }
        return clamp(native)
    }
}

interface ParameterOwner {
    val parameterDescriptors: List<ParameterDescriptor>
}
