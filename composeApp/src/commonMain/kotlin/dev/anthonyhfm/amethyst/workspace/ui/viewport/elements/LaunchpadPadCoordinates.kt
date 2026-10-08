package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout

internal fun LaunchpadViewportElement.containsLocalPad(x: Int, y: Int): Boolean =
    x in 0 until layout.cols && y in 0 until layout.rows

internal fun LaunchpadViewportElement.containsGlobalPad(x: Int, y: Int): Boolean =
    containsLocalPad(x = x - position.value.x.toInt(), y = y - position.value.y.toInt())

internal fun LaunchpadLayout.localPadForMidiIndex(index: Int): Pair<Int, Int> =
    index % 10 - offsetX to rows - 1 - (index / 10 - offsetY)

internal fun LaunchpadViewportElement.globalPadForMidiIndex(index: Int): Pair<Int, Int>? {
    val (x, y) = layout.localPadForMidiIndex(index = index)
    if (!containsLocalPad(x = x, y = y)) {
        return null
    }
    return x + position.value.x.toInt() to y + position.value.y.toInt()
}

internal fun LaunchpadLayout.midiIndexForLocalPad(x: Int, y: Int): Int =
    (rows - 1 - y + offsetY) * 10 + x + offsetX

internal fun LaunchpadViewportElement.canonicalMidiOrigin(): Offset = Offset(
    x = position.value.x - layout.offsetX,
    y = position.value.y + layout.rows - 10 + layout.offsetY,
)
