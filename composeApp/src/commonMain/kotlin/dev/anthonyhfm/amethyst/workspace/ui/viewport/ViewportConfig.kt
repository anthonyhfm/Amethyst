package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ViewportConfig(
    val enablePanning: Boolean = true,
    val enableZoom: Boolean = true,
    val draggableObjects: Boolean = false,
    val minZoom: Float = 0.5f,
    val maxZoom: Float = 2f,
    val contentPadding: Dp = 24.dp,
    val panBoundsPolicy: ViewportPanBoundsPolicy = ViewportPanBoundsPolicy.Unbounded,
    val showGrid: Boolean = false,
    val showOrigin: Boolean = false,
    val showActions: Boolean = false,
    val showRemoteCursors: Boolean = true,
)

sealed interface ViewportPanBoundsPolicy {
    data object Unbounded : ViewportPanBoundsPolicy

    data class ClampToContent(
        val allowedOutOfBoundsFraction: Float = 0.5f,
    ) : ViewportPanBoundsPolicy
}
