package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
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
): Modifier = this.pointerInput(onSingleClick, onDoubleClick, doubleTapTimeoutMs) {
    var lastTapTime = 0L

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
        val up = waitForUpOrCancellation(pass = PointerEventPass.Main)

        if (up != null) {
            up.consume()

            val now = System.now().epochSeconds
            val isDoubleTap = (now - lastTapTime) <= doubleTapTimeoutMs

            lastTapTime = if (isDoubleTap) 0L else now

            onSingleClick()

            if (isDoubleTap) {
                onDoubleClick()
            }
        }
    }
}
