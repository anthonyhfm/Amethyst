package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput

@Composable
internal fun Modifier.resetOnDoubleClick(enabled: Boolean, onReset: () -> Unit): Modifier {
    val currentOnReset = rememberUpdatedState(newValue = onReset)
    return pointerInput(enabled) {
        if (!enabled) {
            return@pointerInput
        }
        var lastTapTime: Long? = null
        var lastTapPosition = Offset.Unspecified
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (!currentEvent.buttons.isPrimaryPressed) {
                return@awaitEachGesture
            }
            var moved = false
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                }
                if (!change.pressed) {
                    val previous = lastTapTime
                    if (!moved && change.uptimeMillis - down.uptimeMillis <= viewConfiguration.longPressTimeoutMillis) {
                        if (previous != null && change.uptimeMillis - previous <= viewConfiguration.doubleTapTimeoutMillis &&
                            (change.position - lastTapPosition).getDistance() <= viewConfiguration.touchSlop
                        ) {
                            currentOnReset.value()
                            lastTapTime = null
                        } else {
                            lastTapTime = change.uptimeMillis
                            lastTapPosition = change.position
                        }
                    } else {
                        lastTapTime = null
                    }
                    break
                }
            }
        }
    }
}
