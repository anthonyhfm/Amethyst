package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlin.math.max
import kotlinx.coroutines.delay

private fun easeInOutCubic(t: Float): Float {
    return if (t < 0.5f) {
        4f * t * t * t
    } else {
        val p = -2f * t + 2f
        1f - (p * p * p) / 2f
    }
}

@Suppress("unused")
object ViewportController {
    suspend fun centerStage(
        viewportOffset: Offset,
        viewportZoom: Float,
        elements: List<LaunchpadViewportElement>,
        viewportSize: Size,
        gridSize: Int,
        onEvent: (WorkspaceContract.Event) -> Unit,
        animationDurationMs: Long = 320L,
        steps: Int = 12
    ) {
        if (elements.isEmpty()) return
        if (viewportSize.width <= 0f || viewportSize.height <= 0f) return

        val minX = elements.minOf { it.position.value.x }
        val maxX = elements.maxOf { it.position.value.x + it.size.width }
        val minY = elements.minOf { it.position.value.y }
        val maxY = elements.maxOf { it.position.value.y + it.size.height }

        val bboxWidthGrid = max(1f, maxX - minX)
        val bboxHeightGrid = max(1f, maxY - minY)

        val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)

        val scaledGridCurrent = gridSize * viewportZoom
        val bboxCenterPxX = (minX + maxX) / 2f * scaledGridCurrent
        val bboxCenterPxY = (minY + maxY) / 2f * scaledGridCurrent
        val desiredOffset = Offset(viewportCenter.x - bboxCenterPxX, viewportCenter.y - bboxCenterPxY)

        val stepCount = max(1, steps)
        val durationPerStep = animationDurationMs / stepCount

        var lastSentOffset = viewportOffset

        for (i in 1..stepCount) {
            val t = i.toFloat() / stepCount.toFloat()
            val eased = easeInOutCubic(t)

            val currentOffset = Offset(
                x = viewportOffset.x + (desiredOffset.x - viewportOffset.x) * eased,
                y = viewportOffset.y + (desiredOffset.y - viewportOffset.y) * eased
            )

            val panDelta = Offset(currentOffset.x - lastSentOffset.x, currentOffset.y - lastSentOffset.y)
            if (panDelta.x != 0f || panDelta.y != 0f) {
                onEvent(WorkspaceContract.Event.OnPanViewport(panDelta))
            }

            lastSentOffset = currentOffset

            delay(durationPerStep)
        }
    }

    suspend fun centerLaunchpads(
        viewportOffset: Offset,
        viewportZoom: Float,
        elements: List<LaunchpadViewportElement>,
        viewportSize: Size,
        gridSize: Int,
        onEvent: (WorkspaceContract.Event) -> Unit,
        animationDurationMs: Long = 320L,
        steps: Int = 12
    ) = centerStage(viewportOffset, viewportZoom, elements, viewportSize, gridSize, onEvent, animationDurationMs, steps)
}
