package dev.anthonyhfm.amethyst.core.util

import kotlin.time.TimeSource

class StopWatch {
    private var startMark = TimeSource.Monotonic.markNow()

    fun reset() {
        startMark = TimeSource.Monotonic.markNow()
    }

    fun elapsedMillis(): Double {
        return elapsedNanos() / NANOS_PER_MILLISECOND.toDouble()
    }

    fun elapsedNanos(): Long {
        return startMark.elapsedNow().inWholeNanoseconds.coerceAtLeast(0)
    }

    companion object {
        const val NANOS_PER_MILLISECOND: Long = 1_000_000L
    }
}
