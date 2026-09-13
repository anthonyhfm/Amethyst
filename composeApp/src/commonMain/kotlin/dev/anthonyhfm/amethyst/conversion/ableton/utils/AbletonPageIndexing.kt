package dev.anthonyhfm.amethyst.conversion.ableton.utils

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
}
