package dev.anthonyhfm.amethyst.timeline.utils

import kotlin.math.ceil
import kotlin.math.round

object GridUtils {
    private val candidates = longArrayOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 60000)
    private const val MIN_SPACING_PX = 48f

    data class GridIntervals(
        val intervalMs: Long,
        val majorEvery: Int,
        val majorIntervalMs: Long,
        val type: GridType
    )

    // --- Bestehende dynamische Berechnung (Fallback) ---
    fun compute(zoomLevel: Float): GridIntervals {
        val intervalMs = candidates.firstOrNull { it * zoomLevel >= MIN_SPACING_PX } ?: candidates.last()
        val majorEvery = when (intervalMs) {
            1L, 2L, 5L -> 10
            10L, 20L -> 5
            50L -> 4
            100L, 200L, 500L -> 5
            1000L -> 5
            2000L, 5000L -> 6
            else -> 2
        }
        val majorIntervalMs = intervalMs * majorEvery
        return GridIntervals(intervalMs, majorEvery, majorIntervalMs, GridType.None)
    }

    fun snapToGrid(timeMs: Long, zoomLevel: Float, bpm: Double? = null, gridType: GridType? = null): Long {
        val intervals = if (gridType == null || gridType is GridType.None) compute(zoomLevel) else computeWithGridType(zoomLevel, bpm ?: 120.0, gridType)
        val interval = intervals.intervalMs
        if (interval <= 0) return timeMs
        val quotient = timeMs.toDouble() / interval.toDouble()
        return round(quotient).toLong() * interval
    }

    // --- Ableton-ähnliche Grid Berechnung ---
    fun computeWithGridType(zoomLevel: Float, bpm: Double, gridType: GridType): GridIntervals {
        if (gridType is GridType.None) return compute(zoomLevel)
        val safeBpm = bpm.takeIf { it > 0.0 } ?: 120.0
        val beatMs = 60000.0 / safeBpm // Länge eines Beats
        val barMs = beatMs * 4 // 4/4 Takt angenommen

        // Liste musikalischer Subdivisionen (Bruchteile eines Bars) für flexible Auswahl
        val subdivisions = listOf(
            1.0 / 32.0,
            1.0 / 16.0,
            1.0 / 8.0,
            1.0 / 4.0,
            1.0 / 2.0,
            1.0
        )

        fun fractionToMs(frac: Double) = (barMs * frac).roundToLongSafe()

        return when (gridType) {
            GridType.None -> compute(zoomLevel)
            is GridType.Flexible.Smallest -> {
                val ms = fractionToMs(1.0 / 32.0)
                GridIntervals(ms, (barMs / ms).ceilInt(), barMs.roundToLongSafe(), gridType)
            }
            is GridType.Flexible.Small -> {
                val ms = fractionToMs(1.0 / 16.0)
                GridIntervals(ms, (barMs / ms).ceilInt(), barMs.roundToLongSafe(), gridType)
            }
            is GridType.Flexible.Medium -> {
                // Wähle kleinstes Subdivision mit ausreichender Pixelbreite
                val chosenFrac = subdivisions.firstOrNull { (barMs * it * zoomLevel) >= MIN_SPACING_PX } ?: subdivisions.last()
                val ms = fractionToMs(chosenFrac)
                GridIntervals(ms, (barMs / ms).ceilInt(), barMs.roundToLongSafe(), gridType)
            }
            is GridType.Flexible.Large -> {
                val ms = fractionToMs(1.0 / 4.0)
                GridIntervals(ms, (barMs / ms).ceilInt(), barMs.roundToLongSafe(), gridType)
            }
            is GridType.Flexible.Largest -> {
                val ms = fractionToMs(1.0)
                GridIntervals(ms, 1, ms, gridType)
            }
            is GridType.Fixed.Bar_1 -> {
                val ms = barMs.roundToLongSafe(); GridIntervals(ms, 1, ms, gridType)
            }
            is GridType.Fixed.Bar_2 -> {
                val ms = (barMs * 2).roundToLongSafe(); GridIntervals(barMs.roundToLongSafe(), 2, ms, gridType)
            }
            is GridType.Fixed.Bar_4 -> {
                val ms = (barMs * 4).roundToLongSafe(); GridIntervals(barMs.roundToLongSafe(), 4, ms, gridType)
            }
            is GridType.Fixed.Bar_8 -> {
                val ms = (barMs * 8).roundToLongSafe(); GridIntervals(barMs.roundToLongSafe(), 8, ms, gridType)
            }
            is GridType.Fixed._1_2 -> {
                val ms = fractionToMs(1.0 / 2.0); GridIntervals(ms, 2, barMs.roundToLongSafe(), gridType)
            }
            is GridType.Fixed._1_4 -> {
                val ms = fractionToMs(1.0 / 4.0); GridIntervals(ms, 4, barMs.roundToLongSafe(), gridType)
            }
            is GridType.Fixed._1_8 -> {
                val ms = fractionToMs(1.0 / 8.0); GridIntervals(ms, 8, barMs.roundToLongSafe(), gridType)
            }
            is GridType.Fixed._1_16 -> {
                val ms = fractionToMs(1.0 / 16.0); GridIntervals(ms, 16, barMs.roundToLongSafe(), gridType)
            }
            is GridType.Fixed._1_32 -> {
                val ms = fractionToMs(1.0 / 32.0); GridIntervals(ms, 32, barMs.roundToLongSafe(), gridType)
            }
        }
    }

    private fun Double.roundToLongSafe(): Long = round(this).toLong().coerceAtLeast(1L)
    private fun Double.ceilInt(): Int = ceil(this).toInt().coerceAtLeast(1)

    sealed interface GridType {
        data object None : GridType
        sealed interface Flexible : GridType {
            data object Smallest : Flexible
            data object Small : Flexible
            data object Medium : Flexible
            data object Large : Flexible
            data object Largest : Flexible
        }

        sealed interface Fixed : GridType {
            data object Bar_1 : Fixed
            data object Bar_2 : Fixed
            data object Bar_4 : Fixed
            data object Bar_8 : Fixed

            data object _1_2: Fixed
            data object _1_4: Fixed
            data object _1_8: Fixed
            data object _1_16: Fixed
            data object _1_32: Fixed
        }
    }
}