package dev.anthonyhfm.amethyst.devices

import dev.anthonyhfm.amethyst.core.engine.elements.Chain

/**
 * The amount of timeline time consumed by a chain device.
 *
 * [None] deliberately differs from a zero millisecond delay: it means that the
 * device transforms or forwards a signal without extending the chain lifetime.
 */
sealed interface TimelineDuration {
    data object None : TimelineDuration
    data class Finite(val milliseconds: Long) : TimelineDuration {
        init {
            require(milliseconds >= 0L) { "A timeline duration cannot be negative" }
        }
    }
    data object Unbounded : TimelineDuration
}

data class TimelineDurationContext(
    val bpm: Double,
    /** Conservative canvas bounds used by signal-dependent devices. */
    val canvasWidth: Int = 10,
    val canvasHeight: Int = 10,
)

/** A shape/source device that can begin a private timeline chain without an input signal. */
interface TimelineTriggerable {
    fun startTimelineTrigger()
    fun stopTimelineTrigger()
}

fun Iterable<TimelineDuration>.serialDuration(): TimelineDuration {
    var total = 0L
    for (duration in this) {
        when (duration) {
            TimelineDuration.None -> Unit
            TimelineDuration.Unbounded -> return TimelineDuration.Unbounded
            is TimelineDuration.Finite -> total = saturatingAdd(total, duration.milliseconds)
        }
    }
    return if (total == 0L) TimelineDuration.None else TimelineDuration.Finite(total)
}

fun Iterable<TimelineDuration>.parallelDuration(): TimelineDuration {
    var maximum = 0L
    for (duration in this) {
        when (duration) {
            TimelineDuration.None -> Unit
            TimelineDuration.Unbounded -> return TimelineDuration.Unbounded
            is TimelineDuration.Finite -> maximum = maxOf(maximum, duration.milliseconds)
        }
    }
    return if (maximum == 0L) TimelineDuration.None else TimelineDuration.Finite(maximum)
}

private fun saturatingAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

fun Chain.timelineDuration(context: TimelineDurationContext): TimelineDuration =
    devices.value
        .asSequence()
        .filterNot(GenericChainDevice<*>::isMuted)
        .map { it.timelineDuration(context) }
        .asIterable()
        .serialDuration()
