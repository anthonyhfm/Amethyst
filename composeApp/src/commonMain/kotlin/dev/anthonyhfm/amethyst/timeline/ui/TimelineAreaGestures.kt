package dev.anthonyhfm.amethyst.timeline.ui

import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

internal class TimelineAreaInteraction {
    var pointer by mutableStateOf<Offset?>(value = null)
    var gestureOwner by mutableStateOf<Any?>(value = null)
    var snapEnabled: Boolean = true
    var select: (Offset, Boolean, Boolean) -> Unit = { _, _, _ -> }
}

internal val LocalTimelineAreaInteraction = staticCompositionLocalOf<TimelineAreaInteraction?> { null }

@Composable
internal fun Modifier.timelineAreaGestures(
    gestureKey: Any? = null,
    onSelectPoint: ((Offset, Boolean) -> Unit)? = null,
    onPressOverride: (Offset) -> Boolean = { false },
    onDoubleClick: (Offset) -> Unit = {},
): Modifier {
    val inherited = LocalTimelineAreaInteraction.current
    val interaction = inherited ?: remember { TimelineAreaInteraction() }
    if (inherited == null && onSelectPoint == null) {
        return this
    }
    val fallback by rememberUpdatedState(newValue = onSelectPoint)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(value = null) }
    val overridePress by rememberUpdatedState(newValue = onPressOverride)
    val doubleClick by rememberUpdatedState(newValue = onDoubleClick)
    return onGloballyPositioned { coordinates = it }
        .pointerInput(key1 = interaction) {
            var lastTapTime: Long? = null
            var lastTapPosition: Offset? = null
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                if (!currentEvent.buttons.isPrimaryPressed) {
                    return@awaitEachGesture
                }
                if (overridePress(down.position)) {
                    down.consume()
                    return@awaitEachGesture
                }
                val root = coordinates?.localToRoot(relativeToLocal = down.position) ?: return@awaitEachGesture
                val extend = currentEvent.keyboardModifiers.isShiftPressed
                interaction.snapEnabled = !currentEvent.keyboardModifiers.isAltPressed
                if (inherited == null) {
                    fallback?.invoke(down.position, extend)
                } else {
                    interaction.select(root, extend, interaction.snapEnabled)
                }
                down.consume()
                val started = awaitDragOrCancellation(pointerId = down.id)?.takeIf { it.pressed }
                if (started != null) {
                    lastTapTime = null
                    lastTapPosition = null
                    fun update(position: Offset) {
                        val point = coordinates?.localToRoot(relativeToLocal = position) ?: return
                        interaction.pointer = point
                        if (inherited == null) {
                            fallback?.invoke(position, true)
                        } else {
                            interaction.select(point, true, interaction.snapEnabled)
                        }
                    }
                    try {
                        interaction.gestureOwner = gestureKey
                        update(position = started.position)
                        started.consume()
                        drag(pointerId = started.id) { change ->
                            update(position = change.position)
                            change.consume()
                        }
                    } finally {
                        interaction.pointer = null
                        interaction.gestureOwner = null
                    }
                } else {
                    val previousTime = lastTapTime
                    val previousPosition = lastTapPosition
                    if (!extend && previousTime != null && previousPosition != null &&
                        down.uptimeMillis - previousTime <= viewConfiguration.doubleTapTimeoutMillis &&
                        (down.position - previousPosition).getDistance() <= viewConfiguration.touchSlop * 2f
                    ) {
                        doubleClick(down.position)
                        lastTapTime = null
                        lastTapPosition = null
                    } else {
                        lastTapTime = down.uptimeMillis
                        lastTapPosition = down.position
                    }
                }
            }
        }
}
