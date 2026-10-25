@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.locators.TimelineLocatorRepository
import dev.anthonyhfm.amethyst.timeline.ui.components.TimelineRuler
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.components.primitives.Slider
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import kotlinx.coroutines.Dispatchers
import kotlin.test.*

class Release072ControlsTest {
    @Test
    fun selectScrollReachesAndSelectsLastOfSixtySources() {
        val selected = mutableStateOf(value = "None")
        var lastBounds = Rect.Zero
        val recomposer = FrameRecomposer(coroutineContext = Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(
            frameRecomposer = recomposer,
            size = IntSize(width = 400, height = 500),
            platformContext = object : PlatformContext.Empty() {
                override val windowInfo = object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize = IntSize(width = 400, height = 500)
                }
            },
        )
        try {
            scene.setContent {
                CompositionLocalProvider(LocalWindowInfo provides object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize = IntSize(width = 400, height = 500)
                }) {
                AmethystTheme {
                    Select(value = selected.value, onValueChange = {}, modifier = Modifier.width(width = 240.dp)) {
                        repeat(times = 60) { index ->
                            SelectItem(
                                text = "Source $index",
                                selected = selected.value == "Source $index",
                                onClick = { selected.value = "Source $index" },
                                modifier = if (index == 59) Modifier.onGloballyPositioned { lastBounds = it.boundsInWindow() } else Modifier,
                            )
                        }
                    }
                }
                }
            }
            fun frame(time: Long) {
                recomposer.performFrame(frameTimeNanos = time * 1_000_000L)
                scene.measureAndLayout()
            }
            frame(time = 0L)
            scene.sendPointerEvent(eventType = PointerEventType.Press, position = Offset(x = 100f, y = 20f), timeMillis = 100L, buttons = PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(eventType = PointerEventType.Release, position = Offset(x = 100f, y = 20f), timeMillis = 120L)
            frame(time = 200L)
            frame(time = 500L)
            scene.sendPointerEvent(eventType = PointerEventType.Scroll, position = Offset(x = 100f, y = 150f), timeMillis = 600L, scrollDelta = Offset(x = 0f, y = 5000f))
            for (time in 1L..20L) {
                frame(time = 600L + time * 50L)
            }
            assertTrue(actual = lastBounds.height > 0f && lastBounds.center.y in 0f..500f, message = "Last source bounds: $lastBounds")
            scene.sendPointerEvent(eventType = PointerEventType.Press, position = lastBounds.center, timeMillis = 1800L, buttons = PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(eventType = PointerEventType.Release, position = lastBounds.center, timeMillis = 1820L)
            frame(time = 1900L)
            assertEquals(expected = "Source 59", actual = selected.value)
        } finally {
            scene.close()
            recomposer.close()
        }
    }

    @Test
    fun sliderDoubleClickUsesConfiguredRangeEdgeDefaultAndDragStillWorks() {
        val value = mutableStateOf(value = 0.75f)
        val recomposer = FrameRecomposer(coroutineContext = Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(frameRecomposer = recomposer, size = IntSize(width = 240, height = 100))
        try {
            scene.setContent {
                AmethystTheme {
                    Box(modifier = Modifier.size(width = 240.dp, height = 60.dp)) {
                        Slider(value = value.value, onValueChange = { value.value = it }, valueRange = -2f..2f, defaultValue = -2f)
                    }
                }
            }
            recomposer.performFrame(frameTimeNanos = 0L)
            scene.measureAndLayout()
            fun pointer(type: PointerEventType, x: Float, time: Long, pressed: Boolean) {
                scene.sendPointerEvent(eventType = type, position = Offset(x = x, y = 10f), timeMillis = time, buttons = PointerButtons(isPrimaryPressed = pressed))
                recomposer.performFrame(frameTimeNanos = (time + 1L) * 1_000_000L)
            }
            pointer(type = PointerEventType.Press, x = 150f, time = 100L, pressed = true)
            pointer(type = PointerEventType.Release, x = 150f, time = 120L, pressed = false)
            pointer(type = PointerEventType.Press, x = 150f, time = 200L, pressed = true)
            pointer(type = PointerEventType.Release, x = 150f, time = 220L, pressed = false)
            assertEquals(expected = -2f, actual = value.value)
            pointer(type = PointerEventType.Press, x = 30f, time = 1000L, pressed = true)
            pointer(type = PointerEventType.Move, x = 210f, time = 1050L, pressed = true)
            pointer(type = PointerEventType.Release, x = 210f, time = 1100L, pressed = false)
            assertTrue(actual = value.value > 0f)
        } finally {
            scene.close()
            recomposer.close()
        }
    }

    @Test
    fun dialDoubleClickUsesItsExplicitDefault() {
        val value = mutableStateOf(value = 0.8f)
        val recomposer = FrameRecomposer(coroutineContext = Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(frameRecomposer = recomposer, size = IntSize(width = 120, height = 100))
        try {
            scene.setContent {
                AmethystTheme {
                    Dial(type = DialType.Continuous, value = value.value, defaultValue = 0.13f, onValueChange = { value.value = it })
                }
            }
            recomposer.performFrame(frameTimeNanos = 0L)
            scene.measureAndLayout()
            for ((type, time, pressed) in listOf(
                Triple(PointerEventType.Press, 100L, true), Triple(PointerEventType.Release, 120L, false),
                Triple(PointerEventType.Press, 200L, true), Triple(PointerEventType.Release, 220L, false),
            )) {
                scene.sendPointerEvent(eventType = type, position = Offset(x = 30f, y = 24f), timeMillis = time, buttons = PointerButtons(isPrimaryPressed = pressed))
                recomposer.performFrame(frameTimeNanos = (time + 1L) * 1_000_000L)
            }
            assertEquals(expected = 0.13f, actual = value.value)
        } finally {
            scene.close()
            recomposer.close()
        }
    }

    @Test
    fun rulerDragSeeksContinuouslyAtScrolledZoomedCoordinates() {
        TimelineLocatorRepository.load(locators = emptyList())
        TimelineRepository.setPlayheadPosition(positionMs = 0L)
        val recomposer = FrameRecomposer(coroutineContext = Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(frameRecomposer = recomposer, size = IntSize(width = 400, height = 100))
        try {
            scene.setContent {
                AmethystTheme {
                    TimelineRuler(viewport = EditorViewportState(zoomX = 2f, scrollX = 300f, viewportWidth = 400f, contentWidth = 4000f), bpm = 120.0, gridType = GridUtils.GridType.Fixed._1_4)
                }
            }
            recomposer.performFrame(frameTimeNanos = 0L)
            scene.measureAndLayout()
            scene.sendPointerEvent(eventType = PointerEventType.Press, position = Offset(x = 40f, y = 10f), timeMillis = 100L, buttons = PointerButtons(isPrimaryPressed = true))
            assertEquals(expected = 170L, actual = TimelineRepository.playheadPositionMs.value)
            scene.sendPointerEvent(eventType = PointerEventType.Move, position = Offset(x = 160f, y = 10f), timeMillis = 140L, buttons = PointerButtons(isPrimaryPressed = true))
            assertEquals(expected = 230L, actual = TimelineRepository.playheadPositionMs.value)
            scene.sendPointerEvent(eventType = PointerEventType.Move, position = Offset(x = 500f, y = 10f), timeMillis = 160L, buttons = PointerButtons(isPrimaryPressed = true))
            assertEquals(expected = 400L, actual = TimelineRepository.playheadPositionMs.value)
            scene.sendPointerEvent(eventType = PointerEventType.Release, position = Offset(x = 500f, y = 10f), timeMillis = 180L)
            scene.sendPointerEvent(eventType = PointerEventType.Move, position = Offset(x = 10f, y = 10f), timeMillis = 200L)
            assertEquals(expected = 400L, actual = TimelineRepository.playheadPositionMs.value)
        } finally {
            scene.close()
            recomposer.close()
            TimelineRepository.setPlayheadPosition(positionMs = 0L)
        }
    }
}
