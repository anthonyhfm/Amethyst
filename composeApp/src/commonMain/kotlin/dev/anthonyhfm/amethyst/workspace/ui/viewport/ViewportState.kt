package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs

@Stable
class ViewportState(
    initialOffset: Offset = Offset.Zero,
    initialZoom: Float = 1f
) {
    var offset by mutableStateOf(initialOffset)
    var zoom by mutableStateOf(initialZoom)
    private var completedAutoFitSignature by mutableStateOf<String?>(null)

    fun zoom(
        zoomDelta: Float,
        zoomCenter: Offset,
        constraints: ViewportConstraints = ViewportConstraints()
    ) {
        val currentZoom = zoom
        val newZoom = (currentZoom + zoomDelta).coerceIn(constraints.minZoom, constraints.maxZoom)
        if (abs(currentZoom) < 1e-6f || newZoom == currentZoom) {
            zoom = newZoom
            offset = ViewportMath.clampOffset(offset, newZoom, constraints)
            return
        }

        val zoomChange = newZoom / currentZoom
        val newOffset = Offset(
            x = zoomCenter.x + (offset.x - zoomCenter.x) * zoomChange,
            y = zoomCenter.y + (offset.y - zoomCenter.y) * zoomChange
        )

        zoom = newZoom
        offset = ViewportMath.clampOffset(newOffset, newZoom, constraints)
    }

    fun pan(
        panDelta: Offset,
        constraints: ViewportConstraints = ViewportConstraints()
    ) {
        offset = ViewportMath.clampOffset(offset + panDelta, zoom, constraints)
    }

    fun fitContent(
        contentBounds: Rect,
        viewportSize: Size,
        paddingPx: Float,
        constraints: ViewportConstraints = ViewportConstraints(),
    ) {
        val fit = ViewportMath.fitContent(
            contentBounds = contentBounds,
            viewportSize = viewportSize,
            paddingPx = paddingPx,
            minZoom = constraints.minZoom,
            maxZoom = constraints.maxZoom,
        )
        zoom = fit.zoom
        offset = ViewportMath.clampOffset(fit.offset, fit.zoom, constraints)
    }

    fun hasCompletedAutoFit(signature: String): Boolean {
        return completedAutoFitSignature == signature
    }

    fun markAutoFitComplete(signature: String) {
        completedAutoFitSignature = signature
    }

    fun resetAutoFit() {
        completedAutoFitSignature = null
    }
}

data class ViewportConstraints(
    val viewportSize: Size = Size.Zero,
    val contentBounds: Rect? = null,
    val viewportPaddingPx: Float = 0f,
    val minZoom: Float = 0.5f,
    val maxZoom: Float = 2f,
    val panBoundsPolicy: ViewportPanBoundsPolicy = ViewportPanBoundsPolicy.Unbounded,
)

data class ViewportTransform(
    val zoom: Float,
    val offset: Offset,
)

object ViewportMath {
    fun fitContent(
        contentBounds: Rect,
        viewportSize: Size,
        paddingPx: Float,
        minZoom: Float,
        maxZoom: Float,
    ): ViewportTransform {
        if (contentBounds == Rect.Zero || viewportSize.width <= 0f || viewportSize.height <= 0f) {
            return ViewportTransform(zoom = minZoom.coerceAtMost(1f), offset = Offset.Zero)
        }

        val contentWidth = contentBounds.width.coerceAtLeast(1f)
        val contentHeight = contentBounds.height.coerceAtLeast(1f)
        val availableWidth = (viewportSize.width - paddingPx * 2f).coerceAtLeast(1f)
        val availableHeight = (viewportSize.height - paddingPx * 2f).coerceAtLeast(1f)
        val targetZoom = minOf(
            availableWidth / contentWidth,
            availableHeight / contentHeight
        ).coerceIn(minZoom, maxZoom)

        val contentCenter = contentBounds.center
        return ViewportTransform(
            zoom = targetZoom,
            offset = Offset(
                x = viewportSize.width / 2f - contentCenter.x * targetZoom,
                y = viewportSize.height / 2f - contentCenter.y * targetZoom,
            ),
        )
    }

    fun clampOffset(
        candidate: Offset,
        zoom: Float,
        constraints: ViewportConstraints,
    ): Offset {
        val bounds = constraints.contentBounds ?: return candidate
        val viewportSize = constraints.viewportSize
        if (viewportSize.width <= 0f || viewportSize.height <= 0f || bounds == Rect.Zero) return candidate

        return when (val policy = constraints.panBoundsPolicy) {
            ViewportPanBoundsPolicy.Unbounded -> candidate
            is ViewportPanBoundsPolicy.ClampToContent -> {
                val fraction = policy.allowedOutOfBoundsFraction.coerceIn(0f, 1f)
                val minVisibleWidth = (bounds.width * zoom * (1f - fraction))
                    .coerceAtLeast(1f)
                val minVisibleHeight = (bounds.height * zoom * (1f - fraction))
                    .coerceAtLeast(1f)

                Offset(
                    x = clampAxis(
                        candidate = candidate.x,
                        contentMin = bounds.left,
                        contentMax = bounds.right,
                        viewportExtent = viewportSize.width,
                        viewportPadding = constraints.viewportPaddingPx,
                        zoom = zoom,
                        minVisibleExtent = minVisibleWidth,
                    ),
                    y = clampAxis(
                        candidate = candidate.y,
                        contentMin = bounds.top,
                        contentMax = bounds.bottom,
                        viewportExtent = viewportSize.height,
                        viewportPadding = constraints.viewportPaddingPx,
                        zoom = zoom,
                        minVisibleExtent = minVisibleHeight,
                    ),
                )
            }
        }
    }

    private fun clampAxis(
        candidate: Float,
        contentMin: Float,
        contentMax: Float,
        viewportExtent: Float,
        viewportPadding: Float,
        zoom: Float,
        minVisibleExtent: Float,
    ): Float {
        val padding = viewportPadding.coerceAtLeast(0f)
        val minOffset = padding + minVisibleExtent - contentMax * zoom
        val maxOffset = viewportExtent - padding - minVisibleExtent - contentMin * zoom
        return if (minOffset <= maxOffset) {
            candidate.coerceIn(minOffset, maxOffset)
        } else {
            (minOffset + maxOffset) / 2f
        }
    }
}

object ViewportStateStore {
    private val states = mutableMapOf<String, ViewportState>()

    fun getOrCreate(
        key: String,
        initialOffset: Offset = Offset.Zero,
        initialZoom: Float = 1f,
    ): ViewportState {
        return states.getOrPut(key) {
            ViewportState(
                initialOffset = initialOffset,
                initialZoom = initialZoom,
            )
        }
    }
}

@Composable
fun rememberViewportState(
    initialOffset: Offset = Offset.Zero,
    initialZoom: Float = 1f
): ViewportState {
    return remember {
        ViewportState(
            initialOffset = initialOffset,
            initialZoom = initialZoom
        )
    }
}

@Composable
fun rememberViewportState(
    key: String,
    initialOffset: Offset = Offset.Zero,
    initialZoom: Float = 1f,
): ViewportState {
    return remember(key) {
        ViewportStateStore.getOrCreate(
            key = key,
            initialOffset = initialOffset,
            initialZoom = initialZoom,
        )
    }
}
