package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.timeline.data.ChainEffectEntry
import dev.anthonyhfm.amethyst.timeline.utils.computeVisibleClipWindowPx
import dev.anthonyhfm.amethyst.timeline.utils.projectTimelineSpanPx
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.modifier.ResizeLeft
import dev.anthonyhfm.amethyst.ui.modifier.ResizeRight
import dev.anthonyhfm.amethyst.ui.theme.TimelineClipRole
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt

@Composable
fun ChainEffectClip(
    entry: ChainEffectEntry,
    viewport: EditorViewportState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMove: (Long) -> Unit,
    onResize: (Long, Long) -> Unit,
    onDoubleClick: () -> Unit,
) {
    val palette = TimelineTheme.palette
    val dimensions = TimelineTheme.dimensions

    var dragDeltaPx by remember(entry.clipId, entry.startTimeMs) { mutableFloatStateOf(0f) }
    var resizeLeftDeltaPx by remember(entry.clipId, entry.startTimeMs) { mutableFloatStateOf(0f) }
    var resizeRightDeltaPx by remember(entry.clipId, entry.durationMs) { mutableFloatStateOf(0f) }

    val projectedSpan = projectTimelineSpanPx(
        startTimeMs = entry.startTimeMs.toDouble(),
        endTimeMs = entry.endTimeMs.toDouble(),
        zoomX = viewport.zoomX,
    )
    val contentStartPx = (projectedSpan.startPx + resizeLeftDeltaPx.roundToInt())
    val contentEndPx = (projectedSpan.endPx + resizeRightDeltaPx.roundToInt())

    val clipWindow = computeVisibleClipWindowPx(
        contentStartPx = contentStartPx,
        contentEndPx = contentEndPx,
        viewport = viewport,
        screenOffsetPx = dragDeltaPx.roundToInt(),
    )
    if (clipWindow == null || clipWindow.visibleWidthPx <= 0) return

    val clipShape = RoundedCornerShape(
        topStart = if (clipWindow.isLeftEdgeVisible) dimensions.clipCornerRadius else 0.dp,
        topEnd = if (clipWindow.isRightEdgeVisible) dimensions.clipCornerRadius else 0.dp,
        bottomStart = if (clipWindow.isLeftEdgeVisible) dimensions.clipCornerRadius else 0.dp,
        bottomEnd = if (clipWindow.isRightEdgeVisible) dimensions.clipCornerRadius else 0.dp,
    )
    val clipHeaderShape = RoundedCornerShape(
        topStart = if (clipWindow.isLeftEdgeVisible) dimensions.clipCornerRadius else 0.dp,
        topEnd = if (clipWindow.isRightEdgeVisible) dimensions.clipCornerRadius else 0.dp,
    )
    val finalWidthDp = with(LocalDensity.current) { clipWindow.visibleWidthPx.toDp() }

    val clipColors = TimelineTheme.clipColors(
        role = TimelineClipRole.Lights,
        selected = isSelected,
    )

    Box(
        modifier = Modifier
            .offset { IntOffset(clipWindow.visibleLeftPx, 0) }
            .width(finalWidthDp)
            .height(dimensions.laneHeight)
            .clip(clipShape)
            .background(clipColors.background.copy(alpha = if (isSelected) 0.98f else 0.90f))
            .border(if (isSelected) 1.5.dp else 1.dp, clipColors.border, clipShape)
            .pointerInput(entry.clipId, entry.startTimeMs, viewport.zoomX) {
                detectTapGestures(
                    onTap = { onSelect() },
                    onDoubleTap = { onDoubleClick() },
                )
            }
            .pointerInput(entry.clipId, entry.startTimeMs, viewport.zoomX) {
                detectDragGestures(
                    onDragStart = { dragDeltaPx = 0f; onSelect() },
                    onDrag = { change, amount ->
                        dragDeltaPx += amount.x
                        change.consume()
                    },
                    onDragEnd = {
                        onMove((entry.startTimeMs + dragDeltaPx / viewport.zoomX).toLong().coerceAtLeast(0L))
                        dragDeltaPx = 0f
                    },
                    onDragCancel = { dragDeltaPx = 0f },
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.clipHeaderHeight)
                    .background(clipColors.header, clipHeaderShape)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (entry.isPlayable) Icons.Default.AutoAwesome else Icons.Default.Add,
                    contentDescription = null,
                    tint = clipColors.content,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = entry.name,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = clipColors.content,
                    style = Theme[typography][small],
                )
                if (entry.isCapped) {
                    Icon(
                        imageVector = Icons.Default.VerticalAlignBottom,
                        contentDescription = "Capped",
                        tint = clipColors.content,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }

        val barMs = (60_000.0 / dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.bpm.value.coerceAtLeast(1.0)).toLong() * 4L

        if (clipWindow.isLeftEdgeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(dimensions.resizeHandleWidth)
                    .fillMaxHeight()
                    .pointerHoverIcon(PointerIcon.ResizeLeft)
                    .pointerInput(entry.clipId, entry.startTimeMs, viewport.zoomX) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, amount ->
                                change.consume()
                                resizeLeftDeltaPx += amount.x
                            },
                            onDragEnd = {
                                if (resizeLeftDeltaPx != 0f) {
                                    val deltaMs = (resizeLeftDeltaPx / viewport.zoomX).toLong()
                                    val rawNewStartMs = (entry.startTimeMs + deltaMs).coerceAtLeast(0L)
                                    val newDurationMs = (entry.endTimeMs - rawNewStartMs).coerceAtLeast(barMs)
                                    onResize(rawNewStartMs, newDurationMs)
                                }
                                resizeLeftDeltaPx = 0f
                            },
                            onDragCancel = { resizeLeftDeltaPx = 0f },
                        )
                    }
            )
        }

        if (clipWindow.isRightEdgeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(dimensions.resizeHandleWidth)
                    .fillMaxHeight()
                    .pointerHoverIcon(PointerIcon.ResizeRight)
                    .pointerInput(entry.clipId, entry.durationMs, viewport.zoomX) {
                        detectDragGestures(
                            onDragStart = { onSelect() },
                            onDrag = { change, amount ->
                                change.consume()
                                resizeRightDeltaPx += amount.x
                            },
                            onDragEnd = {
                                if (resizeRightDeltaPx != 0f) {
                                    val deltaMs = (resizeRightDeltaPx / viewport.zoomX).toLong()
                                    val newDurationMs = (entry.durationMs + deltaMs).coerceAtLeast(barMs)
                                    onResize(entry.startTimeMs, newDurationMs)
                                }
                                resizeRightDeltaPx = 0f
                            },
                            onDragCancel = { resizeRightDeltaPx = 0f },
                        )
                    }
            )
        }
    }
}

