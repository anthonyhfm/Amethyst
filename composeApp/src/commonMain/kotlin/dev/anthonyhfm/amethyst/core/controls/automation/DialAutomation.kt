package dev.anthonyhfm.amethyst.core.controls.automation

import dev.anthonyhfm.amethyst.devices.effects.composition.automation.CompositionAutomationPoint
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.segmentValueAt
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.core.parameter.ParameterAddress
import kotlin.math.exp
import kotlin.math.ln

@Serializable
enum class LiveAutomationTimingUnit { Milliseconds, Beats }

@Serializable
enum class LiveAutomationRetriggerMode { IgnoreWhileRunning, Restart, ContinueFromCurrent, Blend }

@Serializable
enum class LiveAutomationCurve { Linear, Exponential, Logarithmic, SCurve, Bezier }

@Serializable
sealed interface LiveAutomationTarget {
    @Serializable
    data class Macro(val macroId: String) : LiveAutomationTarget

    @Serializable
    data class Parameter(val address: ParameterAddress) : LiveAutomationTarget
}

@Serializable
data class LiveAutomationSettings(
    val durationValue: Float = 1f,
    val timingUnit: LiveAutomationTimingUnit = LiveAutomationTimingUnit.Beats,
    val retriggerMode: LiveAutomationRetriggerMode = LiveAutomationRetriggerMode.IgnoreWhileRunning,
    val isAdditive: Boolean = false,
    val gate: Float = 0.5f,
    val curve: LiveAutomationCurve = LiveAutomationCurve.Linear,
    val blendDurationMs: Float = 30f,
    val stopOnPadUp: Boolean = false,
)

/** Parameter-independent automation shared by direct controls and automation devices. */
@Serializable
data class LiveAutomation(
    val parameterId: String = "",
    val points: List<CompositionAutomationPoint> = listOf(
        CompositionAutomationPoint(0f, -1f),
        CompositionAutomationPoint(1f, 1f),
    ),
    val settings: LiveAutomationSettings = LiveAutomationSettings(),
) {
    val startValue: Float get() = points.minByOrNull { it.progress }?.value ?: -1f
    val endValue: Float get() = points.maxByOrNull { it.progress }?.value ?: 1f

    fun withEndpoints(start: Float, end: Float): LiveAutomation = copy(
        points = listOf(
            CompositionAutomationPoint(0f, start.coerceIn(-1f, 1f)),
            CompositionAutomationPoint(1f, end.coerceIn(-1f, 1f)),
        ),
    )

    fun valueAt(progress: Float, fallback: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        if (settings.curve != LiveAutomationCurve.Bezier) {
            val shaped = when (settings.curve) {
                LiveAutomationCurve.Linear -> p
                LiveAutomationCurve.Exponential ->
                    if (p == 0f) 0f else ((exp(p.toDouble()) - 1.0) / (exp(1.0) - 1.0)).toFloat()
                LiveAutomationCurve.Logarithmic -> ln(1.0 + p * (exp(1.0) - 1.0)).toFloat()
                LiveAutomationCurve.SCurve -> p * p * (3f - 2f * p)
                LiveAutomationCurve.Bezier -> p
            }
            return (startValue + (endValue - startValue) * shaped).coerceIn(-1f, 1f)
        }
        val ordered = points.sortedBy(CompositionAutomationPoint::progress)
        if (ordered.isEmpty()) return fallback
        if (p <= ordered.first().progress) return ordered.first().value
        if (p >= ordered.last().progress) return ordered.last().value
        val endIndex = ordered.indexOfFirst { it.progress >= p }.coerceAtLeast(1)
        val start = ordered[endIndex - 1]
        val end = ordered[endIndex]
        val span = (end.progress - start.progress).coerceAtLeast(0.0001f)
        return start.segmentValueAt(end, ((p - start.progress) / span).coerceIn(0f, 1f))
            .coerceIn(-1f, 1f)
    }

    companion object {
        fun createInitial(parameterId: String, initialNormalizedValue: Float = 0.5f): LiveAutomation {
            val initial = (initialNormalizedValue * 2f - 1f).coerceIn(-1f, 1f)
            return LiveAutomation(parameterId = parameterId).withEndpoints(initial, initial)
        }
    }
}

/** Audio-frame-clock runtime. Beat duration and BPM are snapshotted at trigger time. */
class LiveAutomationRuntime(var automation: LiveAutomation) {
    var isRunning: Boolean = false
        private set
    var startFrame: Long = 0L
        private set
    var durationFrames: Long = 1L
        private set
    var startBpm: Float = 120f
        private set
    private var runtimeStartValue: Float? = null
    private var blendFromValue = 0f
    private var blendFrames = 0L
    private var legacyStartMs = 0L

    var lane: LiveAutomation
        get() = automation
        set(value) { automation = value }

    fun trigger(frame: Long, sampleRate: Int, bpm: Float, currentValue: Float = automation.startValue): Boolean {
        val mode = automation.settings.retriggerMode
        if (isRunning && mode == LiveAutomationRetriggerMode.IgnoreWhileRunning) return false
        val previousValue = if (isRunning) valueAtFrame(frame) else currentValue
        runtimeStartValue = if (mode == LiveAutomationRetriggerMode.ContinueFromCurrent) previousValue else null
        blendFromValue = previousValue
        blendFrames = if (mode == LiveAutomationRetriggerMode.Blend) {
            (sampleRate * automation.settings.blendDurationMs / 1_000f).toLong().coerceAtLeast(1L)
        } else 0L
        startFrame = frame.coerceAtLeast(0L)
        startBpm = bpm.coerceAtLeast(1f)
        val seconds = when (automation.settings.timingUnit) {
            LiveAutomationTimingUnit.Milliseconds -> automation.settings.durationValue / 1_000f
            LiveAutomationTimingUnit.Beats -> automation.settings.durationValue * 60f / startBpm
        } * (automation.settings.gate * 2f)
        durationFrames = (seconds.coerceAtLeast(1f / sampleRate) * sampleRate).toLong().coerceAtLeast(1L)
        isRunning = true
        return true
    }

    fun progressAt(frame: Long): Float {
        if (!isRunning) return 0f
        return ((frame - startFrame).coerceAtLeast(0L).toFloat() / durationFrames).coerceIn(0f, 1f)
    }

    fun valueAtFrame(frame: Long, fallback: Float = automation.startValue): Float {
        if (!isRunning) return fallback.coerceIn(-1f, 1f)
        val progress = progressAt(frame)
        var value = automation.valueAt(progress, fallback)
        runtimeStartValue?.let { start -> value = start + (automation.endValue - start) * progress }
        if (blendFrames > 0L) {
            val blend = ((frame - startFrame).coerceAtLeast(0L).toFloat() / blendFrames).coerceIn(0f, 1f)
            value = blendFromValue + (value - blendFromValue) * blend
        }
        if (progress >= 1f) isRunning = false
        return value.coerceIn(-1f, 1f)
    }

    fun stop() { isRunning = false }

    /** Compatibility clock for LED-only devices; audio consumers use frame APIs. */
    fun trigger(nowMs: Long) {
        if (isRunning && automation.settings.retriggerMode == LiveAutomationRetriggerMode.IgnoreWhileRunning) return
        legacyStartMs = nowMs
        trigger(0L, 1_000, 120f)
    }

    fun currentProgress(nowMs: Long, bpm: Float = 120f): Float {
        if (!isRunning) return 0f
        val durationMs = when (automation.settings.timingUnit) {
            LiveAutomationTimingUnit.Milliseconds -> automation.settings.durationValue
            LiveAutomationTimingUnit.Beats -> automation.settings.durationValue * 60_000f / bpm.coerceAtLeast(1f)
        } * (automation.settings.gate * 2f)
        val progress = ((nowMs - legacyStartMs).coerceAtLeast(0L) / durationMs.coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        if (progress >= 1f) isRunning = false
        return progress
    }
}

typealias DialAutomationLane = LiveAutomation
typealias DialAutomationSettings = LiveAutomationSettings
typealias DialAutomationTimingUnit = LiveAutomationTimingUnit
typealias DialAutomationRetriggerMode = LiveAutomationRetriggerMode
typealias DialAutomationRuntime = LiveAutomationRuntime
