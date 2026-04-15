package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import kotlin.math.roundToInt

internal fun rotateMidiUpdates(
    updates: List<RawLEDUpdate>,
    layout: LaunchpadLayout,
    rotationDegrees: Float,
): List<RawLEDUpdate> {
    val normalized = ((rotationDegrees % 360f + 360f) % 360f).roundToInt()
    if (normalized == 0) return updates

    val m = layout.cols + 2 * layout.offsetX - 1

    return updates.map { update ->
        val idx = update.index.toInt() and 0xFF
        if (idx >= 100) return@map update

        val posX = idx % 10
        val posY = idx / 10

        val (newPosX, newPosY) = when (normalized) {
            90  -> Pair(posY, m - posX)
            180 -> Pair(m - posX, m - posY)
            270 -> Pair(m - posY, posX)
            else -> return@map update
        }

        RawLEDUpdate(newPosX + newPosY * 10, update.color)
    }
}
