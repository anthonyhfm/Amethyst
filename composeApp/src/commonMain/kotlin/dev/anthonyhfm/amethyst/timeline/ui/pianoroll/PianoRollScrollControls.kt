package dev.anthonyhfm.amethyst.timeline.ui.pianoroll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.timeline.resolveViewportRelativeCursorX
import dev.anthonyhfm.amethyst.timeline.viewport.wheelZoomScaleFactor

@Composable
internal fun Modifier.pianoRollScrollControls(
    legendWidthPx: Float,
    onPan: (Offset) -> Unit,
    onZoom: (Float, Float) -> Unit,
): Modifier {
    val latestOnPan by rememberUpdatedState(newValue = onPan)
    val latestOnZoom by rememberUpdatedState(newValue = onZoom)
    return pointerInput(key1 = legendWidthPx) {
        var lastPointerX: Float? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                ModifierKeysState.updateFromPointerModifiers(modifiers = event.keyboardModifiers)
                val change = event.changes.firstOrNull() ?: continue
                val pointerX = (change.position.x - legendWidthPx).coerceAtLeast(minimumValue = 0f)
                if (event.type == PointerEventType.Exit) {
                    lastPointerX = null
                } else if (event.type != PointerEventType.Scroll) {
                    lastPointerX = pointerX
                }
                if (event.type != PointerEventType.Scroll || event.changes.any { it.isConsumed }) {
                    continue
                }
                val delta = change.scrollDelta
                val zoomModifier = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                if (zoomModifier && delta.y != 0f) {
                    latestOnZoom(
                        wheelZoomScaleFactor(scrollDelta = -delta.y),
                        resolveViewportRelativeCursorX(trackedPointerX = lastPointerX, eventPointerX = pointerX),
                    )
                    event.changes.forEach { it.consume() }
                } else {
                    val horizontalDelta = if (event.keyboardModifiers.isShiftPressed) {
                        if (delta.x != 0f) {
                            delta.x
                        } else {
                            delta.y
                        }
                    } else {
                        delta.x
                    }
                    if (horizontalDelta != 0f) {
                        latestOnPan(Offset(x = horizontalDelta * 40f, y = 0f))
                    }
                    if (event.keyboardModifiers.isShiftPressed || (horizontalDelta != 0f && delta.y == 0f)) {
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}
