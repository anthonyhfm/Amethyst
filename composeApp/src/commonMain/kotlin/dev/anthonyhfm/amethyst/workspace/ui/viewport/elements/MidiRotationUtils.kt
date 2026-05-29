package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import kotlin.math.roundToInt

internal fun rotatePadCoordinates(
    x: Int,
    y: Int,
    layout: LaunchpadLayout,
    rotationDegrees: Float,
): Pair<Int, Int> {
    val normalized = ((rotationDegrees % 360f + 360f) % 360f).roundToInt()
    if (normalized == 0) return Pair(x, y)

    val m = layout.cols - 1

    return when (normalized) {
        90  -> Pair(y, m - x)
        180 -> Pair(m - x, m - y)
        270 -> Pair(m - y, x)
        else -> Pair(x, y)
    }
}

internal fun rotateMidiCoordinate(
    x: Int,
    y: Int,
    layout: LaunchpadLayout,
    rotationDegrees: Float,
): Pair<Int, Int> {
    val normalized = ((rotationDegrees % 360f + 360f) % 360f).roundToInt()
    if (normalized == 0) return Pair(x, y)

    val m = layout.cols + 2 * layout.offsetX - 1

    return when (normalized) {
        90  -> Pair(y, m - x)
        180 -> Pair(m - x, m - y)
        270 -> Pair(m - y, x)
        else -> Pair(x, y)
    }
}

internal fun rotateMidiUpdates(
    updates: List<RawLEDUpdate>,
    layout: LaunchpadLayout,
    rotationDegrees: Float,
): List<RawLEDUpdate> {
    val normalized = ((rotationDegrees % 360f + 360f) % 360f).roundToInt()
    if (normalized == 0) return updates

    return updates.map { update ->
        val idx = update.index.toInt() and 0xFF
        if (idx >= 100) return@map update

        val posX = idx % 10
        val posY = idx / 10

        val (newPosX, newPosY) = rotateMidiCoordinate(posX, posY, layout, rotationDegrees)

        RawLEDUpdate(newPosX + newPosY * 10, update.color)
    }
}
