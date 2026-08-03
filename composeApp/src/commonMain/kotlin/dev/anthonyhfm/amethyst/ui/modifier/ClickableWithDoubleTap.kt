package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import kotlin.time.Clock.System

/**
 * A modifier that fires [onSingleClick] instantly on every tap (zero latency),
 * and additionally fires [onDoubleClick] when two taps occur within [doubleTapTimeoutMs].
 *
 * Unlike [combinedClickable], single clicks are NEVER delayed waiting for a second tap.
 */
fun Modifier.clickableWithDoubleTap(
    doubleTapTimeoutMs: Long = 350L,
    onSingleClick: () -> Unit,
    onDoubleClick: () -> Unit,
): Modifier = clickableWithDoubleTap(
    doubleTapTimeoutMs = doubleTapTimeoutMs,
    onSingleClick = { _, _ -> onSingleClick() },
    onDoubleClick = onDoubleClick,
)

/**
 * A modifier that fires [onSingleClick] with real-time hardware [isShiftPressed] and [isCmdOrCtrlPressed] states directly from the OS event.
 */
fun Modifier.clickableWithDoubleTap(
    doubleTapTimeoutMs: Long = 350L,
    onSingleClick: (isShiftPressed: Boolean, isCmdOrCtrlPressed: Boolean) -> Unit,
    onDoubleClick: () -> Unit,
): Modifier = this.pointerInput(onSingleClick, onDoubleClick, doubleTapTimeoutMs) {
    var lastTapTime = 0L

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
        ModifierKeysState.updateFromPointerModifiers(currentEvent.keyboardModifiers)

        val up = waitForUpOrCancellation(pass = PointerEventPass.Main)

        if (up != null) {
            up.consume()
            val modifiers = currentEvent.keyboardModifiers
            ModifierKeysState.updateFromPointerModifiers(modifiers)

            val isShift = modifiers.isShiftPressed
            val isCmdOrCtrl = modifiers.isMetaPressed || modifiers.isCtrlPressed

            val now = System.now().toEpochMilliseconds()
            val isDoubleTap = (now - lastTapTime) <= doubleTapTimeoutMs

            lastTapTime = if (isDoubleTap) 0L else now

            onSingleClick(isShift, isCmdOrCtrl)

            if (isDoubleTap) {
                onDoubleClick()
            }
        }
    }
}
