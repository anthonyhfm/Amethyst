package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

object ViewportController {
    fun centerAndZoom(
        viewportState: ViewportState,
        contentBounds: Rect,
        viewportSize: Size,
        paddingPx: Float,
        constraints: ViewportConstraints,
    ) {
        if (viewportSize.width <= 0f || viewportSize.height <= 0f) return
        viewportState.fitContent(
            contentBounds = contentBounds,
            viewportSize = viewportSize,
            paddingPx = paddingPx,
            constraints = constraints,
        )
    }
}
