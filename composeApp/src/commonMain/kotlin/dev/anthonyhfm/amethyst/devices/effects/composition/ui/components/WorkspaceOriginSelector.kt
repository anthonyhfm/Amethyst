package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuCheckboxItem
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.secondary
import kotlin.math.min

@Composable
fun WorkspaceOriginSelector(
    originX: Float,
    originY: Float,
    bounds: Pair<IntOffset, IntSize>,
    onOriginChange: (Offset, IntSize) -> Unit,
    modifier: Modifier = Modifier,
    boundToOrigin: Boolean = false,
    onBoundToOriginChange: ((Boolean) -> Unit)? = null,
    extraContextMenuContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val aspectRatio = bounds.second.width.toFloat() / bounds.second.height.coerceAtLeast(1).toFloat()
    val density = LocalDensity.current
    val currentOnOriginChange = rememberUpdatedState(onOriginChange)
    val currentBoundToOrigin = rememberUpdatedState(boundToOrigin)

    @Composable
    fun RenderSelector(selectorModifier: Modifier) {
        BoxWithConstraints(
            modifier = selectorModifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val maxHeightPx = with(density) { maxHeight.toPx() }
            val widthPx = min(maxWidthPx, maxHeightPx * aspectRatio)
            val heightPx = widthPx / aspectRatio
            val pickerWidth = with(density) { widthPx.toDp() }
            val pickerHeight = with(density) { heightPx.toDp() }

            Box(
                modifier = Modifier
                    .width(pickerWidth)
                    .height(pickerHeight)
                    .clip(DefaultShape)
                    .background(Theme[colors][secondary])
                    .alpha(if (boundToOrigin) 0.4f else 1f)
                    .pointerHoverIcon(if (boundToOrigin) PointerIcon.Default else PointerIcon.Hand)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { position ->
                                if (!currentBoundToOrigin.value) {
                                    currentOnOriginChange.value(position, size)
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { position ->
                                if (!currentBoundToOrigin.value) {
                                    currentOnOriginChange.value(position, size)
                                }
                            },
                            onDrag = { change, _ ->
                                if (!currentBoundToOrigin.value) {
                                    change.consume()
                                    currentOnOriginChange.value(change.position, size)
                                }
                            },
                        )
                    },
            ) {
                if (!boundToOrigin) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(
                                x = originX.coerceIn(0f, 1f) * size.width,
                                y = originY.coerceIn(0f, 1f) * size.height,
                            ),
                        )
                    }
                }
            }

            if (boundToOrigin) {
                Box(
                    modifier = Modifier
                        .width(pickerWidth)
                        .height(pickerHeight)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "The origin is bound to your input",
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                }
            }
        }
    }

    if (onBoundToOriginChange != null || extraContextMenuContent != null) {
        ContextMenu(
            modifier = modifier,
            trigger = { RenderSelector(Modifier.fillMaxSize()) },
        ) {
            extraContextMenuContent?.invoke(this)
            if (extraContextMenuContent != null && onBoundToOriginChange != null) {
                ContextMenuSeparator()
            }
            if (onBoundToOriginChange != null) {
                ContextMenuCheckboxItem(
                    checked = boundToOrigin,
                    onCheckedChange = onBoundToOriginChange,
                ) {
                    Text(text = "Bind to Origin")
                }
            }
        }
    } else {
        RenderSelector(modifier)
    }
}
