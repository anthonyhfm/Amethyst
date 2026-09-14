package dev.anthonyhfm.amethyst.conversion.ableton.utils

import kotlin.math.roundToInt

internal object AbletonPageIndexing {
    fun controlsPages(
        hasKeyMidiMapping: Boolean,
        selectorRanges: Iterable<Pair<Int, Int>>,
    ): Boolean = hasKeyMidiMapping || selectorRanges.any { (minimum, maximum) ->
        minimum != 0 || (maximum != 0 && maximum != 127)
    }

    fun sourceOffset(
        selectorMinimum: Int?,
        hasOneBasedPageController: Boolean = false,
    ): Int = if (hasOneBasedPageController || selectorMinimum == 1) 1 else 0

    fun normalizeSelectorValue(value: Int, sourceOffset: Int): Int =
        value - sourceOffset

    fun normalizeMacroValue(
        value: Double,
        sourceMinimum: Int?,
        sourceMaximum: Int?,
        targetMaximum: Int?,
    ): Int {
        if (
            sourceMinimum == null || sourceMaximum == null || targetMaximum == null ||
            sourceMaximum <= sourceMinimum || targetMaximum !in 1..15
        ) {
            return value.roundToInt()
        }

        return (
            (value - sourceMinimum) * targetMaximum.toDouble() /
                (sourceMaximum - sourceMinimum).toDouble()
            ).roundToInt()
    }

    fun pageTargetMaximum(
        keyMinimum: Int?,
        keyMaximum: Int?,
        controllerMinimum: Int?,
        controllerMaximum: Int?,
    ): Int? {
        val keyRangeSize = if (keyMinimum != null && keyMaximum != null) {
            (keyMaximum - keyMinimum).takeIf { it in 1..15 }
        } else {
            null
        }
        if (keyRangeSize != null) return keyRangeSize

        return if (
            controllerMinimum != null && controllerMaximum != null &&
            controllerMinimum in 0..1 && controllerMaximum in 1..16
        ) {
            (controllerMaximum - controllerMinimum).takeIf { it in 1..15 }
        } else {
            null
        }
    }
}
