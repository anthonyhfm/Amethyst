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
