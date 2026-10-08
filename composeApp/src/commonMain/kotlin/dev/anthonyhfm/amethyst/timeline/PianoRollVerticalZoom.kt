package dev.anthonyhfm.amethyst.timeline

import kotlin.math.floor

internal fun pianoRollVerticalZoomScrollOffset(
    scrollOffsetPx: Float,
    anchorPx: Float,
    oldNoteHeightPx: Float,
    newNoteHeightPx: Float,
    totalPitches: Int,
    deviceHeaderHeightPx: Float,
): Float {
    if (oldNoteHeightPx <= 0f || newNoteHeightPx <= 0f || totalPitches <= 0) {
        return scrollOffsetPx
    }
    val oldDeviceHeightPx = deviceHeaderHeightPx + totalPitches * oldNoteHeightPx
    val newDeviceHeightPx = deviceHeaderHeightPx + totalPitches * newNoteHeightPx
    val oldContentY = scrollOffsetPx + anchorPx
    val deviceIndex = floor(x = oldContentY / oldDeviceHeightPx)
    val localY = oldContentY - deviceIndex * oldDeviceHeightPx
    val newLocalY = if (localY <= deviceHeaderHeightPx) {
        localY
    } else {
        deviceHeaderHeightPx + (localY - deviceHeaderHeightPx) / oldNoteHeightPx * newNoteHeightPx
    }
    return (deviceIndex * newDeviceHeightPx + newLocalY - anchorPx).coerceAtLeast(minimumValue = 0f)
}
