package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composeunstyled.Icon
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground

/**
 * A thin divider occupying the gap between the two composition panes. It reads as empty
 * space but exposes a Lucide grip glyph that can be dragged horizontally to resize the panes.
 *
 * @param onDragByPx invoked with the horizontal drag delta in pixels.
 */
@Composable
fun GraphSplitHandle(
    onDragByPx: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDragByPx by rememberUpdatedState(onDragByPx)
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging

    val gripColor by animateColorAsState(
        targetValue = if (active) {
            Theme[colors][mutedForeground]
        } else {
            Theme[colors][mutedForeground].copy(alpha = 0.45f)
        },
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(12.dp)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDragByPx(dragAmount.x)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Lucide.EllipsisVertical,
                contentDescription = "Resize panes",
                tint = gripColor,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
