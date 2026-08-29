package dev.anthonyhfm.amethyst.core.parameter

import kotlinx.atomicfu.atomic
import kotlin.math.max

/** Immutable value array atomically published from the control thread. */
class ParameterSnapshot private constructor(
    private val values: FloatArray,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): Float = values[index]

    companion object {
        fun of(values: FloatArray): ParameterSnapshot = ParameterSnapshot(values.copyOf())
        fun zeros(size: Int): ParameterSnapshot = ParameterSnapshot(FloatArray(size))
    }
}

class ParameterSnapshotStore(initial: ParameterSnapshot) {
    private val published = atomic(initial)

    fun snapshot(): ParameterSnapshot = published.value

    fun publish(snapshot: ParameterSnapshot) {
        published.value = snapshot
    }
}

/** Allocation-free linear smoother. Target publication is lock-free. */
class SmoothedParameter(initialValue: Float) {
    private val publishedTarget = atomic(initialValue)
    private var current = initialValue
    private var activeTarget = initialValue
    private var step = 0f
    private var framesRemaining = 0

    val currentValue: Float get() = current

    fun setTarget(value: Float) {
        if (value.isFinite()) publishedTarget.value = value
    }

    fun reset(value: Float = publishedTarget.value) {
        val finite = value.takeIf(Float::isFinite) ?: 0f
        publishedTarget.value = finite
        activeTarget = finite
        current = finite
        step = 0f
        framesRemaining = 0
    }

    fun next(sampleRate: Int, smoothing: ParameterSmoothing): Float {
        val requested = publishedTarget.value
        if (requested != activeTarget) {
            activeTarget = requested
            framesRemaining = max(0, (sampleRate * smoothing.durationMs / 1_000f).toInt())
            if (framesRemaining == 0) {
                current = activeTarget
                step = 0f
            } else {
                step = (activeTarget - current) / framesRemaining
            }
        }
        if (framesRemaining > 0) {
            current += step
            framesRemaining--
            if (framesRemaining == 0) current = activeTarget
        }
        return current
    }
}
