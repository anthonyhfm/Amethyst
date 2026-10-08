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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.pointer.isAltPressed
import dev.anthonyhfm.amethyst.timeline.ui.timelineClipGestures
import dev.anthonyhfm.amethyst.timeline.utils.ChainEffectEditMode
import dev.anthonyhfm.amethyst.timeline.utils.ChainEffectSpan
import kotlin.math.roundToLong
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
    resolveEdit: (ChainEffectEditMode, Long, Boolean) -> ChainEffectSpan,
) {
    val focusManager = LocalFocusManager.current
    val windowInfo = LocalWindowInfo.current
    val dimensions = TimelineTheme.dimensions

    var dragDeltaPx by remember(entry.clipId, entry.startTimeMs) { mutableFloatStateOf(0f) }
    var resizeLeftDeltaPx by remember(entry.clipId, entry.startTimeMs) { mutableFloatStateOf(0f) }
    var resizeRightDeltaPx by remember(entry.clipId, entry.durationMs) { mutableFloatStateOf(0f) }

    val snapEnabled = !windowInfo.keyboardModifiers.isAltPressed
    fun resolve(mode: ChainEffectEditMode, deltaPx: Float): ChainEffectSpan {
        return resolveEdit(mode, (deltaPx / viewport.zoomX).roundToLong(), snapEnabled)
    }
    val preview = when {
        resizeLeftDeltaPx != 0f -> resolve(mode = ChainEffectEditMode.LEFT_EDGE, deltaPx = resizeLeftDeltaPx)
        resizeRightDeltaPx != 0f -> resolve(mode = ChainEffectEditMode.RIGHT_EDGE, deltaPx = resizeRightDeltaPx)
        dragDeltaPx != 0f -> resolve(mode = ChainEffectEditMode.MOVE, deltaPx = dragDeltaPx)
        else -> ChainEffectSpan(startMs = entry.startTimeMs, durationMs = entry.durationMs)
    }
    val projectedSpan = projectTimelineSpanPx(
        startTimeMs = preview.startMs.toDouble(),
        endTimeMs = preview.endMs.toDouble(),
        zoomX = viewport.zoomX,
    )
    val clipWindow = computeVisibleClipWindowPx(
        contentStartPx = projectedSpan.startPx,
        contentEndPx = projectedSpan.endPx,
        viewport = viewport,
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
            .timelineClipGestures(
                gestureKey = entry.clipId,
                onPress = { _, _ ->
                    focusManager.clearFocus()
                    onSelect()
                },
                onDragStart = { dragDeltaPx = 0f },
                onDrag = { change, amount ->
                    dragDeltaPx += amount.x
                    change.consume()
                },
                onDragEnd = {
                    val span = resolve(mode = ChainEffectEditMode.MOVE, deltaPx = dragDeltaPx)
                    if (span.startMs != entry.startTimeMs) {
                        onMove(span.startMs)
                    }
                    dragDeltaPx = 0f
                },
                onDragCancel = { dragDeltaPx = 0f },
                onDoubleClick = onDoubleClick,
            )
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

        if (clipWindow.isLeftEdgeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(dimensions.resizeHandleWidth)
                    .fillMaxHeight()
                    .pointerHoverIcon(PointerIcon.ResizeLeft)
                    .pointerInput(entry.clipId, entry.startTimeMs, viewport.zoomX) {
                        detectDragGestures(
                            onDragStart = {
                                focusManager.clearFocus()
                                onSelect()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                resizeLeftDeltaPx += amount.x
                            },
                            onDragEnd = {
                                if (resizeLeftDeltaPx != 0f) {
                                    val span = resolve(mode = ChainEffectEditMode.LEFT_EDGE, deltaPx = resizeLeftDeltaPx)
                                    onResize(span.startMs, span.durationMs)
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
                            onDragStart = {
                                focusManager.clearFocus()
                                onSelect()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                resizeRightDeltaPx += amount.x
                            },
                            onDragEnd = {
                                if (resizeRightDeltaPx != 0f) {
                                    val span = resolve(mode = ChainEffectEditMode.RIGHT_EDGE, deltaPx = resizeRightDeltaPx)
                                    onResize(span.startMs, span.durationMs)
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

