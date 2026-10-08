package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.ui.timelineClipGestures
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class TimelineClipGestureTest {
    @Test
    fun clipOwnsSelectionDoubleClickAndDragWithoutParentTimeSelection() {
        var selections = 0
        var parentSelections = 0
        var doubleClicks = 0
        var dragEnds = 0
        var dragDistance = 0f
        val recomposer = FrameRecomposer(coroutineContext = Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(frameRecomposer = recomposer, size = IntSize(width = 200, height = 100))
        try {
            scene.setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(key1 = Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                    if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed && event.changes.none { it.isConsumed }) {
                                        parentSelections++
                                    }
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .timelineClipGestures(
                                gestureKey = "clip",
                                onPress = { _, _ -> selections++ },
                                onDragStart = {},
                                onDrag = { change, amount ->
                                    dragDistance += amount.x
                                    change.consume()
                                },
                                onDragEnd = { dragEnds++ },
                                onDragCancel = {},
                                onDoubleClick = { doubleClicks++ },
                            )
                    )
                }
            }
            recomposer.performFrame(frameTimeNanos = 0L)
            scene.measureAndLayout()
            fun pointer(type: PointerEventType, x: Float, time: Long, pressed: Boolean) {
                scene.sendPointerEvent(
                    eventType = type,
                    position = Offset(x = x, y = 40f),
                    timeMillis = time,
                    buttons = PointerButtons(isPrimaryPressed = pressed),
                )
            }
            pointer(type = PointerEventType.Press, x = 40f, time = 0L, pressed = true)
            assertEquals(expected = 1, actual = selections)
            pointer(type = PointerEventType.Release, x = 40f, time = 20L, pressed = false)
            pointer(type = PointerEventType.Press, x = 40f, time = 100L, pressed = true)
            pointer(type = PointerEventType.Release, x = 40f, time = 120L, pressed = false)
            assertEquals(expected = 1, actual = doubleClicks)
            pointer(type = PointerEventType.Press, x = 40f, time = 1000L, pressed = true)
            pointer(type = PointerEventType.Move, x = 80f, time = 1020L, pressed = true)
            pointer(type = PointerEventType.Move, x = 100f, time = 1040L, pressed = true)
            pointer(type = PointerEventType.Release, x = 100f, time = 1060L, pressed = false)
            assertTrue(actual = dragDistance > 0f)
            assertEquals(expected = 1, actual = dragEnds)
            assertEquals(expected = 0, actual = parentSelections)
        } finally {
            scene.close()
            recomposer.close()
        }
    }

    @Test
    fun nestedRightClickHasExactlyOneOwnerAndEmptyLaneStillWorks() {
        var laneMenus = 0
        var clipMenus = 0
        val recomposer = FrameRecomposer(coroutineContext = Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(frameRecomposer = recomposer, size = IntSize(width = 200, height = 100))
        try {
            scene.setContent {
                Box(modifier = Modifier.fillMaxSize().rightClickable { laneMenus++ }) {
                    Box(modifier = Modifier.width(width = 100.dp).fillMaxHeight().rightClickable { clipMenus++ })
                }
            }
            recomposer.performFrame(frameTimeNanos = 0L)
            scene.measureAndLayout()
            scene.sendPointerEvent(
                eventType = PointerEventType.Press,
                position = Offset(x = 40f, y = 40f),
                buttons = PointerButtons(isSecondaryPressed = true),
            )
            assertEquals(expected = 1, actual = clipMenus)
            assertEquals(expected = 0, actual = laneMenus)
            scene.sendPointerEvent(
                eventType = PointerEventType.Release,
                position = Offset(x = 40f, y = 40f),
                buttons = PointerButtons(),
            )
            scene.sendPointerEvent(
                eventType = PointerEventType.Press,
                position = Offset(x = 150f, y = 40f),
                buttons = PointerButtons(isSecondaryPressed = true),
            )
            assertEquals(expected = 1, actual = clipMenus)
            assertEquals(expected = 1, actual = laneMenus)
        } finally {
            scene.close()
            recomposer.close()
        }
    }
}
