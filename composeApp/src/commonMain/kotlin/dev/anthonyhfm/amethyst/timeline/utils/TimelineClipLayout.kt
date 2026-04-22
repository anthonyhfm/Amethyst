package dev.anthonyhfm.amethyst.timeline.utils

import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import kotlin.math.roundToInt

internal data class TimelineProjectedSpanPx(
    val startPx: Int,
    val endPx: Int,
) {
    val widthPx: Int
        get() = (endPx - startPx).coerceAtLeast(1)
}

internal fun projectTimelineSpanPx(
    startTimeMs: Double,
    endTimeMs: Double,
    zoomX: Float,
): TimelineProjectedSpanPx {
    val startPx = (startTimeMs * zoomX.toDouble()).roundToInt()
    val endPx = (endTimeMs * zoomX.toDouble()).roundToInt().coerceAtLeast(startPx + 1)
    return TimelineProjectedSpanPx(
        startPx = startPx,
        endPx = endPx,
    )
}

internal fun computeTimelineContentWidthPx(
    maxTimelineEndMs: Double,
    zoomX: Float,
    viewportWidthPx: Float,
    trailingMarginPx: Float,
): Float {
    val projectedEndPx = if (maxTimelineEndMs > 0.0) {
        projectTimelineSpanPx(
            startTimeMs = 0.0,
            endTimeMs = maxTimelineEndMs,
            zoomX = zoomX,
        ).endPx.toFloat()
    } else {
        0f
    }

    val desiredWidthPx = if (projectedEndPx > 0f) {
        projectedEndPx + trailingMarginPx.coerceAtLeast(0f)
    } else {
        0f
    }

    return maxOf(viewportWidthPx.coerceAtLeast(0f), desiredWidthPx)
}

internal data class TimelineVisibleClipWindowPx(
    val screenStartPx: Int,
    val screenEndPx: Int,
    val visibleLeftPx: Int,
    val visibleRightPx: Int,
    val hiddenLeftPx: Int,
    val hiddenRightPx: Int,
    val visibleContentStartPx: Int,
    val visibleContentEndPx: Int,
) {
    val visibleWidthPx: Int
        get() = (visibleRightPx - visibleLeftPx).coerceAtLeast(0)

    val isLeftEdgeVisible: Boolean
        get() = hiddenLeftPx == 0

    val isRightEdgeVisible: Boolean
        get() = hiddenRightPx == 0
}

internal fun computeVisibleClipWindowPx(
    contentStartPx: Int,
    contentEndPx: Int,
    viewport: EditorViewportState,
    screenOffsetPx: Int = 0,
    cullPaddingPx: Int = 100,
): TimelineVisibleClipWindowPx? {
    val viewportWidthPx = viewport.viewportWidth.roundToInt().coerceAtLeast(0)
    if (viewportWidthPx <= 0) return null

    val normalizedEndPx = contentEndPx.coerceAtLeast(contentStartPx + 1)
    val widthPx = normalizedEndPx - contentStartPx
    val screenStartPx = contentStartPx - viewport.scrollX.roundToInt() + screenOffsetPx
    val screenEndPx = screenStartPx + widthPx

    if (screenEndPx < -cullPaddingPx || screenStartPx > viewportWidthPx + cullPaddingPx) {
        return null
    }

    val visibleLeftPx = screenStartPx.coerceAtLeast(0)
    val visibleRightPx = screenEndPx.coerceAtMost(viewportWidthPx)
    if (visibleRightPx <= visibleLeftPx) return null

    val hiddenLeftPx = (visibleLeftPx - screenStartPx).coerceAtLeast(0)
    val hiddenRightPx = (screenEndPx - visibleRightPx).coerceAtLeast(0)

    return TimelineVisibleClipWindowPx(
        screenStartPx = screenStartPx,
        screenEndPx = screenEndPx,
        visibleLeftPx = visibleLeftPx,
        visibleRightPx = visibleRightPx,
        hiddenLeftPx = hiddenLeftPx,
        hiddenRightPx = hiddenRightPx,
        visibleContentStartPx = contentStartPx + hiddenLeftPx,
        visibleContentEndPx = normalizedEndPx - hiddenRightPx,
    )
}
