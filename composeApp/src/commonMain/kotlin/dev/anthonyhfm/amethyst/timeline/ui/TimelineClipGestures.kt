package dev.anthonyhfm.amethyst.timeline.ui

import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

@Composable
internal fun Modifier.timelineClipGestures(
    gestureKey: Any,
    onPress: (Offset, Boolean) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDoubleClick: () -> Unit,
): Modifier {
    val press by rememberUpdatedState(newValue = onPress)
    val dragStart by rememberUpdatedState(newValue = onDragStart)
    val dragUpdate by rememberUpdatedState(newValue = onDrag)
    val dragEnd by rememberUpdatedState(newValue = onDragEnd)
    val dragCancel by rememberUpdatedState(newValue = onDragCancel)
    val doubleClick by rememberUpdatedState(newValue = onDoubleClick)

    return pointerInput(gestureKey) {
        var lastTapTime: Long? = null
        var lastTapPosition: Offset? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            if (!currentEvent.buttons.isPrimaryPressed) {
                return@awaitEachGesture
            }
            press(down.position, currentEvent.keyboardModifiers.isShiftPressed)
            down.consume()
            var overSlop = Offset.Zero
            val startedDrag = if (down.type == PointerType.Mouse) {
                awaitDragOrCancellation(pointerId = down.id)
                    ?.takeIf { it.pressed }
                    ?.also { change ->
                        overSlop = change.positionChange()
                        change.consume()
                    }
            } else {
                awaitTouchSlopOrCancellation(pointerId = down.id) { change, over ->
                    overSlop = over
                    change.consume()
                }
            }
            if (startedDrag != null) {
                lastTapTime = null
                lastTapPosition = null
                dragStart(down.position)
                dragUpdate(startedDrag, overSlop)
                val completed = drag(pointerId = startedDrag.id) { change ->
                    dragUpdate(change, change.positionChange())
                    change.consume()
                }
                if (completed) {
                    dragEnd()
                } else {
                    dragCancel()
                }
            } else {
                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                if (up != null && !up.pressed && !up.isConsumed) {
                    val previousTime = lastTapTime
                    val previousPosition = lastTapPosition
                    if (
                        previousTime != null && previousPosition != null &&
                        down.uptimeMillis - previousTime <= viewConfiguration.doubleTapTimeoutMillis &&
                        (down.position - previousPosition).getDistance() <= viewConfiguration.touchSlop * 2f
                    ) {
                        doubleClick()
                        lastTapTime = null
                        lastTapPosition = null
                    } else {
                        lastTapTime = down.uptimeMillis
                        lastTapPosition = down.position
                    }
                    up.consume()
                }
            }
        }
    }
}
