package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.LinePoint
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.secondary
import kotlin.math.min
import kotlin.math.pow

@Composable
fun WorkspaceLineSelector(
    points: List<LinePoint>,
    selectedIndex: Int,
    bounds: Pair<IntOffset, IntSize>,
    onPointsChange: (newPoints: List<LinePoint>, newSelectedIndex: Int) -> Unit,
    onSelectPoint: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val aspectRatio = bounds.second.width.toFloat() / bounds.second.height.coerceAtLeast(1).toFloat()
    val density = LocalDensity.current
    val currentPoints = rememberUpdatedState(points)
    val currentSelectedIndex = rememberUpdatedState(selectedIndex)
    val currentOnPointsChange = rememberUpdatedState(onPointsChange)
    val currentOnSelectPoint = rememberUpdatedState(onSelectPoint)
    var activeDragIndex by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val widthPx = min(maxWidthPx, maxHeightPx * aspectRatio)
        val heightPx = widthPx / aspectRatio
        val pickerWidth = with(density) { widthPx.toDp() }
        val pickerHeight = with(density) { heightPx.toDp() }

        fun findClosestPointIndex(position: Offset, size: IntSize): Int {
            val pts = currentPoints.value
            if (pts.isEmpty()) return 0
            var minDistanceSq = Float.MAX_VALUE
            var closestIdx = 0
            pts.forEachIndexed { index, point ->
                val px = Offset(point.x.coerceIn(0f, 1f) * size.width, point.y.coerceIn(0f, 1f) * size.height)
                val distSq = (position.x - px.x).pow(2) + (position.y - px.y).pow(2)
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq
                    closestIdx = index
                }
            }
            return closestIdx
        }

        Box(
            modifier = Modifier
                .width(pickerWidth)
                .height(pickerHeight)
                .clip(DefaultShape)
                .background(Theme[colors][secondary])
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { position ->
                            val pts = currentPoints.value
                            if (pts.isEmpty()) return@detectTapGestures
                            val idx = findClosestPointIndex(position, size)
                            val px = Offset(pts[idx].x * size.width, pts[idx].y * size.height)
                            val distSq = (position.x - px.x).pow(2) + (position.y - px.y).pow(2)
                            val hitRadiusPx = with(density) { 24.dp.toPx() }
                            if (distSq <= hitRadiusPx.pow(2)) {
                                currentOnSelectPoint.value(idx)
                            } else {
                                val targetIdx = currentSelectedIndex.value.coerceIn(0, pts.size - 1)
                                val newPoints = pts.toMutableList()
                                newPoints[targetIdx] = LinePoint(
                                    x = (position.x / size.width).coerceIn(0f, 1f),
                                    y = (position.y / size.height).coerceIn(0f, 1f),
                                )
                                currentOnPointsChange.value(newPoints, targetIdx)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            val pts = currentPoints.value
                            if (pts.isEmpty()) return@detectDragGestures
                            val idx = findClosestPointIndex(position, size)
                            activeDragIndex = idx
                            currentOnSelectPoint.value(idx)
                            val newPoints = pts.toMutableList()
                            newPoints[idx] = LinePoint(
                                x = (position.x / size.width).coerceIn(0f, 1f),
                                y = (position.y / size.height).coerceIn(0f, 1f),
                            )
                            currentOnPointsChange.value(newPoints, idx)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pts = currentPoints.value
                            if (pts.isEmpty()) return@detectDragGestures
                            val targetIdx = (activeDragIndex ?: currentSelectedIndex.value).coerceIn(0, pts.size - 1)
                            val newPoints = pts.toMutableList()
                            newPoints[targetIdx] = LinePoint(
                                x = (change.position.x / size.width).coerceIn(0f, 1f),
                                y = (change.position.y / size.height).coerceIn(0f, 1f),
                            )
                            currentOnPointsChange.value(newPoints, targetIdx)
                        },
                        onDragEnd = { activeDragIndex = null },
                        onDragCancel = { activeDragIndex = null },
                    )
                },
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val pts = points
                // Draw connecting lines
                for (i in 0 until pts.size - 1) {
                    val p1 = Offset(pts[i].x.coerceIn(0f, 1f) * size.width, pts[i].y.coerceIn(0f, 1f) * size.height)
                    val p2 = Offset(pts[i + 1].x.coerceIn(0f, 1f) * size.width, pts[i + 1].y.coerceIn(0f, 1f) * size.height)
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = p1,
                        end = p2,
                        strokeWidth = 2.dp.toPx(),
                    )
                }

                // Draw handles
                val handleRadiusPx = 6.dp.toPx()
                val strokeWidthPx = 2.dp.toPx()
                pts.forEachIndexed { index, point ->
                    val ptPx = Offset(point.x.coerceIn(0f, 1f) * size.width, point.y.coerceIn(0f, 1f) * size.height)
                    if (index == selectedIndex) {
                        // Selected point: solid filled circle
                        drawCircle(
                            color = Color.White,
                            radius = handleRadiusPx,
                            center = ptPx,
                        )
                    } else {
                        // Non-selected point: hollow circle with exact same outer bounds
                        drawCircle(
                            color = Color.White.copy(alpha = 0.85f),
                            radius = handleRadiusPx - strokeWidthPx / 2f,
                            center = ptPx,
                            style = Stroke(width = strokeWidthPx),
                        )
                    }
                }
            }
        }
    }
}
