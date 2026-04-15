package dev.anthonyhfm.amethyst.timeline.viewport

import kotlin.math.abs

/**
 * Immutable viewport/camera model for 1-D-scrolling editor surfaces (Timeline, Piano Roll).
 *
 * Coordinate spaces
 * -----------------
 *   Content-space:  pixels measured from the content origin (time = 0, pitch = 0).
 *                   contentX = timeMs * zoomX
 *   Screen-space:   pixels measured from the visible left/top edge of the viewport.
 *                   screenX  = contentX - scrollX
 *
 * All mutating operations return a new copy (reducer style).
 */
data class EditorViewportState(
    /** Horizontal scroll: content-space x of the left viewport edge. */
    val scrollX: Float = 0f,
    /** Vertical scroll: content-space y of the top viewport edge. */
    val scrollY: Float = 0f,
    /** Horizontal zoom: pixels per millisecond. */
    val zoomX: Float = 1f,
    /** Vertical zoom: pixels per logical unit (e.g. semitone row height). */
    val zoomY: Float = 1f,
    /** Visible width of the viewport in pixels. */
    val viewportWidth: Float = 0f,
    /** Visible height of the viewport in pixels. */
    val viewportHeight: Float = 0f,
    /** Total scrollable content width in content-space pixels. */
    val contentWidth: Float = 0f,
    /** Total scrollable content height in content-space pixels. */
    val contentHeight: Float = 0f,
    val minZoomX: Float = 0.0025f,
    val maxZoomX: Float = 5f,
    val minZoomY: Float = 0.25f,
    val maxZoomY: Float = 8f,
) {

    // ── Screen ↔ Content projections ──────────────────────────────────────

    fun screenToContentX(screenX: Float): Float = screenX + scrollX
    fun contentToScreenX(contentX: Float): Float = contentX - scrollX

    fun screenToContentY(screenY: Float): Float = screenY + scrollY
    fun contentToScreenY(contentY: Float): Float = contentY - scrollY

    // ── Time ↔ Screen / Content conversions ───────────────────────────────

    /** Content-space x → milliseconds. */
    fun contentXToTimeMs(contentX: Float): Double =
        if (zoomX > 0f) contentX / zoomX.toDouble() else 0.0

    /** Milliseconds → content-space x in pixels. */
    fun timeMsToContentX(timeMs: Double): Float = (timeMs * zoomX).toFloat()

    /** Screen-space x → milliseconds. */
    fun screenToTimeMs(screenX: Float): Double = contentXToTimeMs(screenToContentX(screenX))

    /** Milliseconds → screen-space x in pixels. */
    fun timeMsToScreenX(timeMs: Double): Float = contentToScreenX(timeMsToContentX(timeMs))

    // ── OOB-aware clip-time projections ──────────────────────────────────────

    /**
     * Content-space X → clip-relative milliseconds.
     *
     * [oobOffsetMs] is the OOB lead-in duration: the canvas extends this many
     * milliseconds before clip-time 0, so content-x 0 equals clip-time −[oobOffsetMs].
     */
    fun contentXToClipTimeMs(contentX: Float, oobOffsetMs: Long): Double =
        contentXToTimeMs(contentX) - oobOffsetMs

    /**
     * Screen-space X → clip-relative milliseconds.
     * Equivalent to [contentXToClipTimeMs] after [screenToContentX].
     */
    fun screenXToClipTimeMs(screenX: Float, oobOffsetMs: Long): Double =
        screenToTimeMs(screenX) - oobOffsetMs

    /**
     * Clip-relative milliseconds → content-space X.
     */
    fun clipTimeMsToContentX(clipTimeMs: Double, oobOffsetMs: Long): Float =
        timeMsToContentX(clipTimeMs + oobOffsetMs)

    /**
     * Clip-relative milliseconds → screen-space X.
     */
    fun clipTimeMsToScreenX(clipTimeMs: Double, oobOffsetMs: Long): Float =
        timeMsToScreenX(clipTimeMs + oobOffsetMs)

    // ── Reducers ──────────────────────────────────────────────────────────

    /** Translate the viewport by [dx] / [dy] screen-space pixels. */
    fun panBy(dx: Float, dy: Float = 0f): EditorViewportState =
        copy(scrollX = scrollX + dx, scrollY = scrollY + dy).clamp()

    /**
     * Zoom the horizontal axis by [scaleDelta] (multiplicative), anchoring the
     * content at [focusScreenX] so that the time under the cursor stays fixed.
     */
    fun zoomAtX(scaleDelta: Float, focusScreenX: Float): EditorViewportState {
        if (abs(scaleDelta) < 1e-6f) return this
        val newZoomX = (zoomX * scaleDelta).coerceIn(minZoomX, maxZoomX)
        if (newZoomX == zoomX) return this
        val timeAtFocus = screenToTimeMs(focusScreenX)
        val newScrollX = (timeAtFocus * newZoomX - focusScreenX).toFloat()
        return copy(zoomX = newZoomX, scrollX = newScrollX).clamp()
    }

    /**
     * Zoom the vertical axis by [scaleDelta] (multiplicative), anchoring the
     * content at [focusScreenY] so that the row under the cursor stays fixed.
     */
    fun zoomAtY(scaleDelta: Float, focusScreenY: Float): EditorViewportState {
        if (abs(scaleDelta) < 1e-6f) return this
        val newZoomY = (zoomY * scaleDelta).coerceIn(minZoomY, maxZoomY)
        if (newZoomY == zoomY) return this
        val contentYAtFocus = screenToContentY(focusScreenY) / zoomY * newZoomY
        val newScrollY = contentYAtFocus - focusScreenY
        return copy(zoomY = newZoomY, scrollY = newScrollY).clamp()
    }

    /** Update the visible viewport dimensions (call from `onSizeChanged`). */
    fun setViewportSize(width: Float, height: Float): EditorViewportState =
        copy(viewportWidth = width, viewportHeight = height).clamp()

    /** Update the total scrollable content extent (recomputed when tracks/zoom change). */
    fun setContentExtent(width: Float, height: Float): EditorViewportState =
        copy(contentWidth = width, contentHeight = height).clamp()

    /**
     * Clamp scrollX/scrollY so the viewport never scrolls past the content bounds.
     * Safe to call even when contentWidth/viewportWidth are 0.
     */
    fun clamp(): EditorViewportState {
        val maxScrollX = (contentWidth - viewportWidth).coerceAtLeast(0f)
        val maxScrollY = (contentHeight - viewportHeight).coerceAtLeast(0f)
        val clampedX = scrollX.coerceIn(0f, maxScrollX)
        val clampedY = scrollY.coerceIn(0f, maxScrollY)
        return if (clampedX == scrollX && clampedY == scrollY) this
        else copy(scrollX = clampedX, scrollY = clampedY)
    }
}

// ── Wheel/pinch helpers ────────────────────────────────────────────────────────

/**
 * Convert a raw vertical scroll wheel delta (in scroll ticks) to a multiplicative
 * scale factor suitable for [EditorViewportState.zoomAtX] / [EditorViewportState.zoomAtY].
 *
 * @param scrollDelta  Raw scroll delta (positive = scroll up / zoom in).
 * @param sensitivity  Scaling strength; 0.7 matches the existing timeline feel.
 * @return A scale factor > 0; pass directly as the [scaleDelta] argument.
 */
fun wheelZoomScaleFactor(scrollDelta: Float, sensitivity: Float = 0.7f): Float {
    val normalized = (scrollDelta / 10f).coerceIn(-1f, 1f)
    return (1f + normalized * sensitivity).coerceAtLeast(0.1f)
}
