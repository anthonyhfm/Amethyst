package dev.anthonyhfm.amethyst.conversion.apollo.utils

internal const val APOLLO_MODE_LIGHT = "amethyst.apolloModeLight"
internal const val APOLLO_FRAME_INDEX = "amethyst.apolloFrameIndex"

internal fun apolloPadCoordinates(index: Int): Pair<Int, Int>? = when {
    index == 100 -> Pair(first = 9, second = 0)
    index in 0..98 -> Pair(first = index % 10, second = 9 - index / 10)
    else -> null
}
