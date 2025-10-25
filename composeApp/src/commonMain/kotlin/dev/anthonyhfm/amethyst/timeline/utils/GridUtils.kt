package dev.anthonyhfm.amethyst.timeline.utils

import kotlin.math.round

object GridUtils {
    private val candidates = longArrayOf(1,2,5,10,20,50,100,200,500,1000,2000,5000,10000,20000,60000)
    private const val MIN_SPACING_PX = 48f

    data class GridIntervals(
        val intervalMs: Long,
        val majorEvery: Int,
        val majorIntervalMs: Long
    )

    fun compute(zoomLevel: Float): GridIntervals {
        val intervalMs = candidates.firstOrNull { it * zoomLevel >= MIN_SPACING_PX } ?: candidates.last()
        val majorEvery = when (intervalMs) {
            1L,2L,5L -> 10
            10L,20L -> 5
            50L -> 4
            100L,200L,500L -> 5
            1000L -> 5
            2000L,5000L -> 6
            else -> 2
        }
        val majorIntervalMs = intervalMs * majorEvery
        return GridIntervals(intervalMs, majorEvery, majorIntervalMs)
    }

    fun snapToGrid(timeMs: Long, zoomLevel: Float): Long {
        val interval = compute(zoomLevel).intervalMs
        if (interval <= 0) return timeMs
        val quotient = timeMs.toDouble() / interval.toDouble()
        return round(quotient).toLong() * interval
    }
}