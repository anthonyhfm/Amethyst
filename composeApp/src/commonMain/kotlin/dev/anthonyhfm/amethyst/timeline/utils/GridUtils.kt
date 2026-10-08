package dev.anthonyhfm.amethyst.timeline.utils

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.pow
import kotlin.math.roundToLong

object GridUtils {
    private val candidates = longArrayOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 60000)
    private const val MIN_SPACING_PX = 48f
    private val musicalSubdivisions = (0..23).map { 2.0.pow(it - 13) }

    data class GridIntervals(
        val intervalMs: Long,
        val majorEvery: Int,
        val majorIntervalMs: Long,
        val type: GridType,
        val exactIntervalMs: Double = intervalMs.toDouble(),
        val exactMajorIntervalMs: Double = majorIntervalMs.toDouble(),
    ) {
        fun timeAt(index: Long): Long = (index * exactIntervalMs).roundToLong()

        fun majorTimeAt(index: Long): Long = (index * exactMajorIntervalMs).roundToLong()

        fun indexAt(timeMs: Double): Long = floor(timeMs / exactIntervalMs).toLong().coerceAtLeast(0L)

        fun majorIndexAt(timeMs: Double): Long = floor(timeMs / exactMajorIntervalMs).toLong().coerceAtLeast(0L)

        fun adjacentTime(timeMs: Long, direction: Int): Long {
            var index = indexAt(timeMs = timeMs.toDouble())
            if (direction >= 0) {
                while (timeAt(index = index) <= timeMs) {
                    index++
                }
            } else {
                while (index > 0L && timeAt(index = index) >= timeMs) {
                    index--
                }
            }
            return timeAt(index = index).coerceAtLeast(0L)
        }

        fun isMajor(index: Long): Boolean = index % majorEvery == 0L
    }

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
        if (gridType is GridType.NoGrid) return timeMs.coerceAtLeast(0L)
        val intervals = if (gridType == null || gridType is GridType.None) compute(zoomLevel) else computeWithGridType(zoomLevel, bpm ?: 120.0, gridType)
        val index = floor(timeMs.toDouble() / intervals.exactIntervalMs + 0.5).toLong()
        return intervals.timeAt(index = index).coerceAtLeast(0L)
    }

    /**
     * Erweitertes Snapping mit Pixel-Threshold: Snap nur wenn Abstand zum nächstliegenden Gridpunkt <= thresholdPx.
     * Fallback: Rohwert (kein Snap), dadurch weniger "sprunghaftes" Verhalten bei großen Intervallen und kleiner Bewegung.
     */
    fun snapToGridWithThreshold(
        timeMs: Long,
        zoomLevel: Float,
        bpm: Double? = null,
        gridType: GridType? = null,
        thresholdPx: Float = 0f
    ): Long {
        if (gridType is GridType.NoGrid) return timeMs.coerceAtLeast(0L)
        val snappedCandidate = snapToGrid(
            timeMs = timeMs,
            zoomLevel = zoomLevel,
            bpm = bpm,
            gridType = gridType,
        )
        val diffMs = snappedCandidate - timeMs
        val diffPx = kotlin.math.abs(diffMs.toFloat() * zoomLevel)
        return if (thresholdPx > 0f && diffPx > thresholdPx) timeMs.coerceAtLeast(0L) else snappedCandidate.coerceAtLeast(0L)
    }

    // --- Ableton-ähnliche Grid Berechnung ---
    fun computeWithGridType(zoomLevel: Float, bpm: Double, gridType: GridType): GridIntervals {
        if (gridType is GridType.None || gridType is GridType.NoGrid) return compute(zoomLevel)
        val safeBpm = bpm.takeIf { it.isFinite() && it > 0.0 } ?: 120.0
        val beatMs = 60000.0 / safeBpm // Länge eines Beats
        val barMs = beatMs * 4 // 4/4 Takt angenommen

        fun fractionToMs(frac: Double) = (barMs * frac).roundToLongSafe()

        return when (gridType) {
            GridType.None, GridType.NoGrid -> compute(zoomLevel)
            is GridType.Flexible -> {
                val spacingPx = when (gridType) {
                    GridType.Flexible.Smallest -> 12f
                    GridType.Flexible.Small -> 24f
                    GridType.Flexible.Medium -> MIN_SPACING_PX
                    GridType.Flexible.Large -> 96f
                    GridType.Flexible.Largest -> 192f
                }
                val safeZoom = zoomLevel.takeIf { it.isFinite() && it > 0f } ?: 0.025f
                val chosenFraction = musicalSubdivisions.firstOrNull {
                    fractionToMs(frac = it) * safeZoom >= spacingPx
                } ?: musicalSubdivisions.last()
                val ms = fractionToMs(frac = chosenFraction)
                val majorMs = maxOf(ms, barMs.roundToLongSafe())
                GridIntervals(ms, (maxOf(chosenFraction, 1.0) / chosenFraction).ceilInt(), majorMs, gridType, barMs * chosenFraction, maxOf(barMs * chosenFraction, barMs))
            }
            is GridType.Fixed.Bar_1 -> {
                val ms = barMs.roundToLongSafe(); GridIntervals(ms, 1, ms, gridType, barMs, barMs)
            }
            is GridType.Fixed.Bar_2 -> {
                val ms = (barMs * 2).roundToLongSafe(); GridIntervals(barMs.roundToLongSafe(), 2, ms, gridType, barMs, barMs * 2)
            }
            is GridType.Fixed.Bar_4 -> {
                val ms = (barMs * 4).roundToLongSafe(); GridIntervals(barMs.roundToLongSafe(), 4, ms, gridType, barMs, barMs * 4)
            }
            is GridType.Fixed.Bar_8 -> {
                val ms = (barMs * 8).roundToLongSafe(); GridIntervals(barMs.roundToLongSafe(), 8, ms, gridType, barMs, barMs * 8)
            }
            is GridType.Fixed._1_2 -> {
                val ms = fractionToMs(1.0 / 2.0); GridIntervals(ms, 2, barMs.roundToLongSafe(), gridType, barMs / 2, barMs)
            }
            is GridType.Fixed._1_4 -> {
                val ms = fractionToMs(1.0 / 4.0); GridIntervals(ms, 4, barMs.roundToLongSafe(), gridType, barMs / 4, barMs)
            }
            is GridType.Fixed._1_8 -> {
                val ms = fractionToMs(1.0 / 8.0); GridIntervals(ms, 8, barMs.roundToLongSafe(), gridType, barMs / 8, barMs)
            }
            is GridType.Fixed._1_16 -> {
                val ms = fractionToMs(1.0 / 16.0); GridIntervals(ms, 16, barMs.roundToLongSafe(), gridType, barMs / 16, barMs)
            }
            is GridType.Fixed._1_32 -> {
                val ms = fractionToMs(1.0 / 32.0); GridIntervals(ms, 32, barMs.roundToLongSafe(), gridType, barMs / 32, barMs)
            }
        }
    }

    fun beatDurationMs(bpm: Double): Double {
        val safeBpm = bpm.takeIf { it.isFinite() && it > 0.0 } ?: 120.0
        return 60_000.0 / safeBpm
    }

    fun beatTimeMs(beatIndex: Long, bpm: Double): Long =
        (beatIndex * beatDurationMs(bpm = bpm)).roundToLong()

    private fun Double.roundToLongSafe(): Long = round(this).toLong().coerceAtLeast(1L)
    private fun Double.ceilInt(): Int = ceil(this).toInt().coerceAtLeast(1)

    /** Fixed grid sizes from widest to narrowest. */
    private val fixedLadder: List<GridType.Fixed> = listOf(
        GridType.Fixed.Bar_8,
        GridType.Fixed.Bar_4,
        GridType.Fixed.Bar_2,
        GridType.Fixed.Bar_1,
        GridType.Fixed._1_2,
        GridType.Fixed._1_4,
        GridType.Fixed._1_8,
        GridType.Fixed._1_16,
        GridType.Fixed._1_32,
    )

    /** Adaptive grid sizes from widest to narrowest. */
    private val flexibleLadder: List<GridType.Flexible> = listOf(
        GridType.Flexible.Largest,
        GridType.Flexible.Large,
        GridType.Flexible.Medium,
        GridType.Flexible.Small,
        GridType.Flexible.Smallest,
    )

    /**
     * Steps the grid one notch along its ladder, like Ableton's Cmd+1 (narrow)
     * and Cmd+2 (widen). Fixed grids halve/double the division, adaptive grids
     * move between the Flexible sizes. "Auto" starts from Flexible.Medium and
     * "No Grid" stays unchanged. Returns the same instance at the end of a ladder.
     */
    fun GridType.stepped(narrower: Boolean): GridType {
        fun <T : GridType> List<T>.step(current: T): T {
            val index = indexOf(current)
            if (index < 0) return current
            val target = if (narrower) index + 1 else index - 1
            return getOrNull(target) ?: current
        }
        return when (this) {
            is GridType.NoGrid -> this
            is GridType.None -> flexibleLadder.step(GridType.Flexible.Medium)
            is GridType.Flexible -> flexibleLadder.step(this)
            is GridType.Fixed -> fixedLadder.step(this)
        }
    }

    fun GridType.narrower(): GridType = stepped(narrower = true)
    fun GridType.wider(): GridType = stepped(narrower = false)

    sealed interface GridType {
        data object None : GridType
        data object NoGrid : GridType
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

val GridUtils.GridType.displayLabel: String
    get() = when (this) {
        is GridUtils.GridType.NoGrid -> "No Grid"
        is GridUtils.GridType.None -> "Auto"
        is GridUtils.GridType.Flexible.Smallest -> "Flex: Min"
        is GridUtils.GridType.Flexible.Small -> "Flex: S"
        is GridUtils.GridType.Flexible.Medium -> "Flex: M"
        is GridUtils.GridType.Flexible.Large -> "Flex: L"
        is GridUtils.GridType.Flexible.Largest -> "Flex: Max"
        is GridUtils.GridType.Fixed.Bar_1 -> "1 Bar"
        is GridUtils.GridType.Fixed.Bar_2 -> "2 Bars"
        is GridUtils.GridType.Fixed.Bar_4 -> "4 Bars"
        is GridUtils.GridType.Fixed.Bar_8 -> "8 Bars"
        is GridUtils.GridType.Fixed._1_2 -> "1/2"
        is GridUtils.GridType.Fixed._1_4 -> "1/4"
        is GridUtils.GridType.Fixed._1_8 -> "1/8"
        is GridUtils.GridType.Fixed._1_16 -> "1/16"
        is GridUtils.GridType.Fixed._1_32 -> "1/32"
    }
