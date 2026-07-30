package dev.anthonyhfm.amethyst.core.controls.automation

import dev.anthonyhfm.amethyst.devices.effects.composition.automation.CompositionAutomationPoint
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.segmentValueAt
import kotlinx.serialization.Serializable

@Serializable
enum class DialAutomationTimingUnit {
    Milliseconds,
    Beats
}

@Serializable
enum class DialAutomationRetriggerMode {
    GatedOneShot,
    AlwaysRetrigger,
}

@Serializable
data class DialAutomationSettings(
    val durationValue: Float = 1.0f,
    val timingUnit: DialAutomationTimingUnit = DialAutomationTimingUnit.Beats,
    val retriggerMode: DialAutomationRetriggerMode = DialAutomationRetriggerMode.GatedOneShot,
    val isAdditive: Boolean = false,
    val gate: Float = 0.5f,
)

@Serializable
data class DialAutomationLane(
    val parameterId: String,
    val points: List<CompositionAutomationPoint> = listOf(
        CompositionAutomationPoint(0f, -1f),
        CompositionAutomationPoint(1f, 1f)
    ),
    val settings: DialAutomationSettings = DialAutomationSettings(),
) {
    companion object {
        fun createInitial(parameterId: String, initialNormalizedValue: Float = 0.5f): DialAutomationLane {
            val initVal = (initialNormalizedValue * 2f - 1f).coerceIn(-1f, 1f)
            return DialAutomationLane(
                parameterId = parameterId,
                points = listOf(
                    CompositionAutomationPoint(0f, initVal),
                    CompositionAutomationPoint(1f, initVal)
                )
            )
        }
    }

    fun valueAt(progress: Float, fallback: Float): Float {
        val ordered = points.sortedBy(CompositionAutomationPoint::progress)
        if (ordered.isEmpty()) return fallback
        val p = progress.coerceIn(0f, 1f)
        if (p <= ordered.first().progress) return ordered.first().value
        if (p >= ordered.last().progress) return ordered.last().value
        val endIndex = ordered.indexOfFirst { it.progress >= p }.coerceAtLeast(1)
        val start = ordered[endIndex - 1]
        val end = ordered[endIndex]
        val span = (end.progress - start.progress).coerceAtLeast(0.0001f)
        val t = ((p - start.progress) / span).coerceIn(0f, 1f)
        return start.segmentValueAt(end, t).coerceIn(-1f, 1f)
    }
}

class DialAutomationRuntime(
    var lane: DialAutomationLane,
) {
    var isRunning: Boolean = false
        private set
    var startTimeMs: Long = 0L
        private set

    fun trigger(nowMs: Long) {
        if (!isRunning || lane.settings.retriggerMode == DialAutomationRetriggerMode.AlwaysRetrigger) {
            isRunning = true
            startTimeMs = nowMs
        }
    }

    fun currentProgress(nowMs: Long, bpm: Float = 120f): Float {
        if (!isRunning) return 0f
        val elapsedMs = (nowMs - startTimeMs).coerceAtLeast(0L)
        val baseMs = when (lane.settings.timingUnit) {
            DialAutomationTimingUnit.Milliseconds -> lane.settings.durationValue
            DialAutomationTimingUnit.Beats -> (lane.settings.durationValue * (60_000f / bpm.coerceAtLeast(1f)))
        }.coerceAtLeast(1f)
        val totalMs = (baseMs * (lane.settings.gate * 2.0f)).coerceAtLeast(1f)

        val progress = elapsedMs / totalMs
        if (progress >= 1f) {
            isRunning = false
            return 1f
        }
        return progress
    }

    fun stop() {
        isRunning = false
    }
}
