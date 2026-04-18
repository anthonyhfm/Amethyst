package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private const val VolumeAutomationMinimumDisplayDb = -24f
private const val VolumeAutomationMaximumDisplayDb = 24f
private const val VolumeAutomationSilenceThreshold = 0.0005f
private const val VolumeAutomationUnitySnapThresholdDb = 0.35f
internal const val TimelineAutomationCurveMaxMagnitude = 3f
internal const val TimelineAutomationDefaultHandleTimeProgress = 0.5f
private const val TimelineAutomationMinimumHandleTimeProgress = 0.05f
private const val TimelineAutomationMaximumHandleTimeProgress = 0.95f
private const val TimelineAutomationLinearHandleTolerance = 0.05f
private val VolumeAutomationMaximumGain = decibelsToLinearGain(VolumeAutomationMaximumDisplayDb)

@Serializable
enum class TimelineTrackAutomationTarget(
    val defaultValue: Float,
    val minimumValue: Float,
    val maximumValue: Float,
) {
    VOLUME(defaultValue = 1f, minimumValue = 0f, maximumValue = VolumeAutomationMaximumGain);

    fun normalizeValue(value: Float): Float {
        if (!value.isFinite()) return defaultValue
        return value.coerceIn(minimumValue, maximumValue)
    }

    fun normalizeBindingId(bindingId: String?): String? = null

    fun displayMinimumValue(): Float = when (this) {
        VOLUME -> VolumeAutomationMinimumDisplayDb
    }

    fun displayMaximumValue(): Float = when (this) {
        VOLUME -> VolumeAutomationMaximumDisplayDb
    }

    fun displayDefaultValue(): Float = when (this) {
        VOLUME -> 0f
    }

    fun valueToDisplayValue(value: Float): Float = when (this) {
        VOLUME -> linearGainToDecibels(value)
    }

    fun displayValueToValue(displayValue: Float): Float = when (this) {
        VOLUME -> if (displayValue <= VolumeAutomationMinimumDisplayDb) {
            0f
        } else {
            decibelsToLinearGain(
                displayValue.coerceIn(
                    minimumValue = VolumeAutomationMinimumDisplayDb,
                    maximumValue = VolumeAutomationMaximumDisplayDb
                )
            )
        }
    }

    fun valueToDisplayProgress(value: Float): Float {
        val minimumDisplayValue = displayMinimumValue()
        val maximumDisplayValue = displayMaximumValue()
        val displayRange = maximumDisplayValue - minimumDisplayValue
        if (displayRange == 0f) return 0.5f

        return ((valueToDisplayValue(value) - minimumDisplayValue) / displayRange)
            .coerceIn(0f, 1f)
    }

    fun displayProgressToValue(progress: Float): Float {
        return displayValueToValue(displayProgressToDisplayValue(progress))
    }

    fun displayProgressToDisplayValue(progress: Float): Float {
        val minimumDisplayValue = displayMinimumValue()
        val maximumDisplayValue = displayMaximumValue()
        return minimumDisplayValue + ((maximumDisplayValue - minimumDisplayValue) * progress.coerceIn(0f, 1f))
    }

    fun snapDisplayValue(displayValue: Float): Float = when (this) {
        VOLUME -> {
            val clampedValue = displayValue.coerceIn(
                minimumValue = VolumeAutomationMinimumDisplayDb,
                maximumValue = VolumeAutomationMaximumDisplayDb
            )
            if (abs(clampedValue) <= VolumeAutomationUnitySnapThresholdDb) {
                0f
            } else {
                clampedValue
            }
        }
    }

    fun snapValue(value: Float): Float {
        return displayValueToValue(
            snapDisplayValue(valueToDisplayValue(value))
        )
    }

    fun formatValue(value: Float): String = when (this) {
        VOLUME -> if (value <= VolumeAutomationSilenceThreshold) {
            "-inf dB"
        } else {
            val displayValue = valueToDisplayValue(value)
            val roundedValue = ((displayValue * 10f).roundToInt()) / 10f
            val normalizedDisplayValue = if (abs(roundedValue) < 0.05f) 0f else roundedValue
            val prefix = if (normalizedDisplayValue > 0f) "+" else ""
            if (normalizedDisplayValue == normalizedDisplayValue.roundToInt().toFloat()) {
                "$prefix${normalizedDisplayValue.roundToInt()} dB"
            } else {
                "$prefix$normalizedDisplayValue dB"
            }
        }
    }

    companion object {
        val globalEntries: List<TimelineTrackAutomationTarget> = entries
    }
}

data class TimelineAutomationLaneKey(
    val target: TimelineTrackAutomationTarget,
    val bindingId: String? = null
) {
    fun normalized(): TimelineAutomationLaneKey {
        return copy(bindingId = target.normalizeBindingId(bindingId))
    }
}

@Serializable
data class TimelineAutomationPoint(
    val timeMs: Long,
    val value: Float,
    val curve: Float = 0f,
    val curveHandleTime: Float? = null,
    val curveHandleValue: Float? = null,
    val pointId: String = UUID.randomUUID()
) {
    fun normalized(target: TimelineTrackAutomationTarget): TimelineAutomationPoint {
        val normalizedHandleTime = curveHandleTime?.coerceIn(
            minimumValue = TimelineAutomationMinimumHandleTimeProgress,
            maximumValue = TimelineAutomationMaximumHandleTimeProgress
        )
        val normalizedHandleValue = curveHandleValue?.let(target::normalizeValue)

        return copy(
            timeMs = timeMs.coerceAtLeast(0L),
            value = target.normalizeValue(value),
            curve = curve.coerceIn(
                minimumValue = -TimelineAutomationCurveMaxMagnitude,
                maximumValue = TimelineAutomationCurveMaxMagnitude
            ),
            curveHandleTime = if (normalizedHandleTime != null && normalizedHandleValue != null) {
                normalizedHandleTime
            } else {
                null
            },
            curveHandleValue = if (normalizedHandleTime != null && normalizedHandleValue != null) {
                normalizedHandleValue
            } else {
                null
            },
            pointId = pointId.ifBlank { UUID.randomUUID() }
        )
    }
}

internal data class TimelineAutomationSegmentHandle(
    val timeProgress: Float,
    val value: Float
)

@Serializable
data class TimelineAutomationLane(
    val target: TimelineTrackAutomationTarget,
    val points: List<TimelineAutomationPoint> = emptyList(),
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val bindingId: String? = null
) {
    val key: TimelineAutomationLaneKey
        get() = TimelineAutomationLaneKey(
            target = target,
            bindingId = bindingId
        ).normalized()

    fun normalized(): TimelineAutomationLane {
        val normalizedPoints = points
            .mapIndexed { index, point -> index to point.normalized(target) }
            .sortedWith(compareBy<Pair<Int, TimelineAutomationPoint>>({ it.second.timeMs }, { it.first }))
            .fold(mutableListOf<TimelineAutomationPoint>()) { acc, (_, point) ->
                if (acc.lastOrNull()?.timeMs == point.timeMs) {
                    acc[acc.lastIndex] = point
                } else {
                    acc += point
                }
                acc
            }

        return copy(
            points = normalizedPoints,
            bindingId = key.bindingId
        )
    }

    fun valueAt(timeMs: Long, defaultValue: Float = target.defaultValue): Float {
        val fallbackValue = target.normalizeValue(defaultValue)
        if (!enabled) return fallbackValue

        if (points.isEmpty()) return fallbackValue

        val firstPoint = points.first()
        if (timeMs < firstPoint.timeMs) return fallbackValue
        if (timeMs == firstPoint.timeMs) return firstPoint.value

        var previousPoint = firstPoint
        for (index in 1 until points.size) {
            val nextPoint = points[index]
            if (timeMs <= nextPoint.timeMs) {
                val durationMs = (nextPoint.timeMs - previousPoint.timeMs).coerceAtLeast(1L)
                val progress = ((timeMs - previousPoint.timeMs).toFloat() / durationMs.toFloat())
                    .coerceIn(0f, 1f)
                return previousPoint.segmentValueAtProgress(
                    target = target,
                    endPoint = nextPoint,
                    progress = progress
                )
            }
            previousPoint = nextPoint
        }

        return points.last().value
    }

    fun point(pointId: String): TimelineAutomationPoint? {
        return points.firstOrNull { it.pointId == pointId }
    }

    fun withPointUpdates(updatedPoints: Iterable<TimelineAutomationPoint>): TimelineAutomationLane {
        val replacements = updatedPoints
            .map { it.normalized(target) }
            .associateByTo(mutableMapOf(), TimelineAutomationPoint::pointId)

        if (replacements.isEmpty()) return this

        val mergedPoints = points
            .filterNot { point -> point.pointId in replacements }
            .toMutableList()
            .apply {
                addAll(replacements.values)
            }

        return copy(points = mergedPoints).normalized()
    }

    fun withoutPoints(pointIds: Collection<String>): TimelineAutomationLane {
        if (pointIds.isEmpty()) return this
        val pointIdsToRemove = pointIds.toSet()
        return copy(
            points = points.filterNot { it.pointId in pointIdsToRemove }
        ).normalized()
    }

    fun deepCopy(): TimelineAutomationLane = copy(
        points = points.map(TimelineAutomationPoint::copy)
    )
}

internal fun applyAutomationCurve(progress: Float, curve: Float): Float {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    if (abs(curve) < 0.001f) {
        return normalizedProgress
    }

    val exponent = 1f + (abs(curve) * 3f)
    return if (curve > 0f) {
        1f - (1f - normalizedProgress).pow(exponent)
    } else {
        normalizedProgress.pow(exponent)
    }.coerceIn(0f, 1f)
}

internal fun TimelineAutomationPoint.clearCurveHandle(): TimelineAutomationPoint {
    return copy(
        curve = 0f,
        curveHandleTime = null,
        curveHandleValue = null
    )
}

internal fun TimelineAutomationPoint.withCurveHandle(
    target: TimelineTrackAutomationTarget,
    endPoint: TimelineAutomationPoint,
    timeProgress: Float,
    value: Float
): TimelineAutomationPoint {
    val normalizedTimeProgress = timeProgress.coerceIn(
        minimumValue = TimelineAutomationMinimumHandleTimeProgress,
        maximumValue = TimelineAutomationMaximumHandleTimeProgress
    )
    val normalizedValue = target.normalizeValue(value)
    val linearValue = valueAtLinearProgress(
        start = this.value,
        end = endPoint.value,
        progress = normalizedTimeProgress
    )
    val linearDisplayDelta = abs(
        target.valueToDisplayValue(normalizedValue) -
            target.valueToDisplayValue(linearValue)
    )

    return if (linearDisplayDelta <= TimelineAutomationLinearHandleTolerance) {
        clearCurveHandle()
    } else {
        copy(
            curve = 0f,
            curveHandleTime = normalizedTimeProgress,
            curveHandleValue = normalizedValue
        )
    }
}

internal fun TimelineAutomationPoint.curveHandle(
    target: TimelineTrackAutomationTarget,
    endPoint: TimelineAutomationPoint
): TimelineAutomationSegmentHandle? {
    val normalizedPoint = normalized(target)
    val normalizedEndPoint = endPoint.normalized(target)
    if (normalizedPoint.curveHandleTime != null &&
        normalizedPoint.curveHandleValue != null
    ) {
        return TimelineAutomationSegmentHandle(
            timeProgress = normalizedPoint.curveHandleTime,
            value = normalizedPoint.curveHandleValue
        )
    }

    if (abs(normalizedPoint.curve) < 0.001f) {
        return null
    }

    return TimelineAutomationSegmentHandle(
        timeProgress = TimelineAutomationDefaultHandleTimeProgress,
        value = legacyAutomationSegmentValueAtProgress(
            startValue = normalizedPoint.value,
            endValue = normalizedEndPoint.value,
            curve = normalizedPoint.curve,
            progress = TimelineAutomationDefaultHandleTimeProgress
        )
    )
}

internal fun TimelineAutomationPoint.displayCurveHandle(
    target: TimelineTrackAutomationTarget,
    endPoint: TimelineAutomationPoint
): TimelineAutomationSegmentHandle {
    return curveHandle(
        target = target,
        endPoint = endPoint
    ) ?: TimelineAutomationSegmentHandle(
        timeProgress = TimelineAutomationDefaultHandleTimeProgress,
        value = valueAtLinearProgress(
            start = value,
            end = endPoint.value,
            progress = TimelineAutomationDefaultHandleTimeProgress
        )
    )
}

internal fun TimelineAutomationPoint.segmentValueAtProgress(
    target: TimelineTrackAutomationTarget,
    endPoint: TimelineAutomationPoint,
    progress: Float
): Float {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val handle = curveHandle(
        target = target,
        endPoint = endPoint
    )

    return when {
        handle != null -> quadraticAutomationSegmentValueAtProgress(
            startValue = value,
            endValue = endPoint.value,
            handleTime = handle.timeProgress,
            handleValue = handle.value,
            progress = normalizedProgress
        )

        abs(curve) >= 0.001f -> legacyAutomationSegmentValueAtProgress(
            startValue = value,
            endValue = endPoint.value,
            curve = curve,
            progress = normalizedProgress
        )

        else -> valueAtLinearProgress(
            start = value,
            end = endPoint.value,
            progress = normalizedProgress
        )
    }
}

internal fun TimelineAutomationLane.rebuildSegmentHandlesFromSource(
    sourceLane: TimelineAutomationLane,
    sourceBaseValue: Float,
    sourceTimeOffset: Long = 0L
): TimelineAutomationLane {
    val normalizedLane = normalized()
    if (normalizedLane.points.isEmpty()) return normalizedLane

    val rebuiltPoints = normalizedLane.points.mapIndexed { index, point ->
        if (index == normalizedLane.points.lastIndex) {
            point.clearCurveHandle()
        } else {
            val nextPoint = normalizedLane.points[index + 1]
            val sourceStartTime = sourceTimeOffset + point.timeMs
            val sourceEndTime = sourceTimeOffset + nextPoint.timeMs
            val midpointTime = sourceStartTime + ((sourceEndTime - sourceStartTime) / 2L)
            val midpointValue = sourceLane.valueAt(
                timeMs = midpointTime,
                defaultValue = sourceBaseValue
            )

            point.withCurveHandle(
                target = normalizedLane.target,
                endPoint = nextPoint,
                timeProgress = TimelineAutomationDefaultHandleTimeProgress,
                value = midpointValue
            )
        }
    }

    return normalizedLane.copy(points = rebuiltPoints).normalized()
}

private fun legacyAutomationSegmentValueAtProgress(
    startValue: Float,
    endValue: Float,
    curve: Float,
    progress: Float
): Float {
    val curvedProgress = applyAutomationCurve(
        progress = progress,
        curve = curve
    )
    return valueAtLinearProgress(
        start = startValue,
        end = endValue,
        progress = curvedProgress
    )
}

private fun quadraticAutomationSegmentValueAtProgress(
    startValue: Float,
    endValue: Float,
    handleTime: Float,
    handleValue: Float,
    progress: Float
): Float {
    val normalizedHandleTime = handleTime.coerceIn(
        minimumValue = TimelineAutomationMinimumHandleTimeProgress,
        maximumValue = TimelineAutomationMaximumHandleTimeProgress
    )
    val denominator = (normalizedHandleTime * normalizedHandleTime) - normalizedHandleTime
    if (abs(denominator) < 0.0001f) {
        return valueAtLinearProgress(
            start = startValue,
            end = endValue,
            progress = progress
        )
    }

    val coefficientA = (
        handleValue -
            startValue -
            (normalizedHandleTime * (endValue - startValue))
        ) / denominator
    val coefficientB = endValue - startValue - coefficientA
    return (((coefficientA * progress) + coefficientB) * progress) + startValue
}

private fun valueAtLinearProgress(
    start: Float,
    end: Float,
    progress: Float
): Float {
    return start + ((end - start) * progress.coerceIn(0f, 1f))
}

private fun linearGainToDecibels(value: Float): Float {
    if (!value.isFinite() || value <= VolumeAutomationSilenceThreshold) {
        return VolumeAutomationMinimumDisplayDb
    }
    return (20f * log10(value)).coerceIn(
        minimumValue = VolumeAutomationMinimumDisplayDb,
        maximumValue = VolumeAutomationMaximumDisplayDb
    )
}

private fun decibelsToLinearGain(value: Float): Float {
    return 10f.pow(value / 20f)
}
