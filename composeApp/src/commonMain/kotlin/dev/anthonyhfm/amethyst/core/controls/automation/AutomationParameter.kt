package dev.anthonyhfm.amethyst.core.controls.automation

import kotlin.reflect.KClass

interface AutomationParameter {
    val id: String
    val label: String
    val curveMode: CurveMode get() = CurveMode.Unipolar
    val snapPoints: List<SnapPoint>? get() = null
    val snapThreshold: Float get() = 0.04f
    val unit: String? get() = null
    val displayDecimals: Int get() = 1
    val displayRange: ClosedFloatingPointRange<Float> get() = 0f..1f
}

enum class CurveMode {
    Unipolar,
    Bipolar,
}

data class SnapPoint(
    val normalizedValue: Float,
    val label: String? = null,
)

inline fun <reified E : Enum<E>> enumSnapPoints(
    noinline label: (E) -> String = { it.name },
): List<SnapPoint> {
    val values = enumValues<E>()
    if (values.isEmpty()) return emptyList()
    return values.mapIndexed { i, v ->
        val normalized = if (values.size == 1) 0f
        else -1f + (2f * i / (values.size - 1))
        SnapPoint(normalized, label(v))
    }
}

fun booleanSnapPoints(onLabel: String = "On", offLabel: String = "Off") = listOf(
    SnapPoint(-1f, offLabel),
    SnapPoint(1f, onLabel),
)
