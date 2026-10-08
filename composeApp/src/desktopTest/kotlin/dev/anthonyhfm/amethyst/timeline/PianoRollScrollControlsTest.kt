package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.timeline.ui.pianoroll.pianoRollScrollControls
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class PianoRollScrollControlsTest {
    @Test
    fun diagonalTrackpadScrollPansXAndDelegatesYToNativeScrollWhileShiftAndCtrlAreConsumed() {
        val pans = mutableListOf<Offset>()
        val zooms = mutableListOf<Pair<Float, Float>>()
        val consumed = mutableListOf<Boolean>()
        val verticalScrollState = ScrollState(initial = 0)
        val recomposer = FrameRecomposer(coroutineContext = Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(
            frameRecomposer = recomposer,
            size = IntSize(width = 200, height = 100),
        )
        try {
            scene.setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pianoRollScrollControls(
                            legendWidthPx = 50f,
                            onPan = { pans += it },
                            onZoom = { factor, anchor -> zooms += factor to anchor },
                        )
                        .pointerInput(key1 = Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Scroll) {
                                        consumed += event.changes.all { it.isConsumed }
                                    }
                                }
                            }
                        }
                        .verticalScroll(state = verticalScrollState)
                ) {
                    Spacer(modifier = Modifier.height(height = 1000.dp))
                }
            }
            recomposer.performFrame(frameTimeNanos = 0L)
            scene.measureAndLayout()
            scene.sendPointerEvent(
                eventType = PointerEventType.Scroll,
                position = Offset(x = 100f, y = 50f),
                scrollDelta = Offset(x = 0.01f, y = 2f),
            )
            scene.sendPointerEvent(
                eventType = PointerEventType.Scroll,
                position = Offset(x = 25f, y = 50f),
                scrollDelta = Offset(x = 0f, y = 1f),
            )
            scene.sendPointerEvent(
                eventType = PointerEventType.Scroll,
                position = Offset(x = 100f, y = 50f),
                scrollDelta = Offset(x = 0f, y = 3f),
                keyboardModifiers = PointerKeyboardModifiers(isShiftPressed = true),
            )
            scene.sendPointerEvent(
                eventType = PointerEventType.Scroll,
                position = Offset(x = 100f, y = 50f),
                scrollDelta = Offset(x = 1f, y = -1f),
                keyboardModifiers = PointerKeyboardModifiers(isCtrlPressed = true),
            )
            repeat(times = 60) { frame ->
                recomposer.performFrame(frameTimeNanos = (frame + 1) * 16_000_000L)
                scene.measureAndLayout()
            }
            assertEquals(expected = 2, actual = pans.size)
            assertEquals(expected = 0.4f, actual = pans[0].x, absoluteTolerance = 0.0001f)
            assertEquals(expected = 0f, actual = pans[0].y)
            assertEquals(expected = Offset(x = 120f, y = 0f), actual = pans[1])
            assertEquals(expected = listOf(false, false, true, true), actual = consumed)
            assertTrue(actual = verticalScrollState.value > 0)
            assertEquals(expected = 1, actual = zooms.size)
            assertEquals(expected = 50f, actual = zooms.single().second)
            assertTrue(actual = zooms.single().first > 1f)
        } finally {
            ModifierKeysState.updateFromPointerModifiers(modifiers = PointerKeyboardModifiers())
            scene.close()
            recomposer.close()
        }
    }
}
