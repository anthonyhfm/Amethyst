package dev.anthonyhfm.amethyst.conversion.ableton.utils

internal object AbletonPageIndexing {
    fun sourceOffset(chainDepth: Int, selectorMinimum: Int?): Int =
        if (chainDepth == 0 && selectorMinimum == 1) 1 else 0

    fun normalizeSelectorValue(value: Int, sourceOffset: Int): Int =
        value - sourceOffset
}
