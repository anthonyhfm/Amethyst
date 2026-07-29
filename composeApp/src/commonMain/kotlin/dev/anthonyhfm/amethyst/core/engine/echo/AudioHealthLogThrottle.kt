package dev.anthonyhfm.amethyst.core.engine.echo

/**
 * Coalesces audio-health log messages without hiding the first failure.
 *
 * This class is intentionally state-only. Platform monitors call it away from
 * their real-time render threads and perform the actual logging themselves.
 */
internal class AudioHealthLogThrottle(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
) {
    init {
        require(intervalMillis >= 0L)
    }

    private var pending = false
    private var hasLogged = false
    private var lastLogAtMillis = 0L

    fun markChanged(nowMillis: Long): Boolean {
        pending = true
        return poll(nowMillis)
    }

    fun poll(nowMillis: Long): Boolean {
        if (!pending) return false
        if (
            hasLogged &&
            nowMillis - lastLogAtMillis < intervalMillis
        ) {
            return false
        }
        pending = false
        hasLogged = true
        lastLogAtMillis = nowMillis
        return true
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 5_000L
    }
}

internal object AudioOutputBufferingPolicy {
    const val TARGET_QUEUED_PERIODS = 2

    fun targetQueuedFrames(periodFrames: Int): Int {
        require(periodFrames > 0)
        require(periodFrames <= Int.MAX_VALUE / TARGET_QUEUED_PERIODS)
        return periodFrames * TARGET_QUEUED_PERIODS
    }

    /**
     * Desktop backends can invoke their callback with more frames than the
     * requested period. Filling the complete native ring prevents such a
     * callback from consuming the queue and zero-filling its remainder.
     */
    fun targetQueuedFrames(
        periodFrames: Int,
        ringCapacityFrames: Int,
    ): Int {
        val minimumFrames = targetQueuedFrames(periodFrames)
        return ringCapacityFrames.takeIf {
            it >= minimumFrames && it % periodFrames == 0
        } ?: minimumFrames
    }
}

internal class AudioRenderDeadlinePolicy(
    private val consecutiveMissesBeforeFallback: Int = 3,
) {
    init {
        require(consecutiveMissesBeforeFallback > 0)
    }

    private var consecutiveMisses = 0

    fun record(overloaded: Boolean): Boolean {
        if (!overloaded) {
            consecutiveMisses = 0
            return false
        }
        consecutiveMisses++
        if (consecutiveMisses < consecutiveMissesBeforeFallback) return false
        consecutiveMisses = 0
        return true
    }
}
