@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.audio.source.AudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.Float32AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.ui.components.AudioClip
import dev.anthonyhfm.amethyst.timeline.ui.components.TimelineClipDragPreview
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WaveformDragRenderingTest {
    @Test
    fun validAndInvalidDragWaveformsMatchThePlacedClipInBothThemes() {
        val asset = dev.anthonyhfm.amethyst.timeline.data.AudioSource(
            id = "waveform-drag-rendering",
            fileName = "waveform.wav",
            rawData = ByteArray(size = 2000) { index -> if (index % 2 == 0) 0 else 64 },
            sampleRate = 1000,
            channels = 1,
            bitDepth = 16,
            isLibraryAsset = false,
        )
        val source = AudioLibraryRepository.add(source = asset)
        val entry = AudioEntry(
            startTimeMs = 0L,
            durationMs = 1000L,
            sourceId = source.id,
            fileName = source.fileName,
            clipEndSample = 1000L,
            sampleRate = 1000,
            channels = 1,
        )
        try {
            for (darkMode in listOf(true, false)) {
                val scene = ImageComposeScene(width = 240, height = 360) {
                    AmethystTheme(darkMode = darkMode) {
                        Column {
                            Box(modifier = Modifier.size(width = 240.dp, height = 120.dp)) {
                                AudioClip(
                                    audioEntry = entry,
                                    viewport = EditorViewportState(zoomX = 0.24f, viewportWidth = 240f, contentWidth = 240f),
                                    isSelected = true,
                                    onSelectEntry = {},
                                    onMoveEntry = {},
                                    gridIntervalMs = 500L,
                                    trackIndex = 0,
                                    entryStartMs = 0L,
                                    bpm = 120.0,
                                    gridType = GridUtils.GridType.Fixed._1_4,
                                )
                            }
                            for (valid in listOf(true, false)) {
                                Box(modifier = Modifier.size(width = 240.dp, height = 120.dp)) {
                                    TimelineClipDragPreview(entry = entry, valid = valid, zoomX = 0.24f)
                                }
                            }
                        }
                    }
                }
                try {
                    var frame = 0L
                    var pixels = scene.render(nanoTime = frame).toComposeImageBitmap().toPixelMap()
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L)
                    while (pixels[100, 52] == pixels[100, 26] ||
                        pixels[100, 172] == pixels[100, 146] ||
                        pixels[100, 292] == pixels[100, 266]
                    ) {
                        assertTrue(actual = System.nanoTime() < deadline, message = "Waveforms did not render")
                        Thread.sleep(2L)
                        frame += 16_000_000L
                        pixels = scene.render(nanoTime = frame).toComposeImageBitmap().toPixelMap()
                    }
                    assertNotEquals(illegal = pixels[100, 26], actual = pixels[100, 52])
                    assertEquals(expected = pixels[100, 52], actual = pixels[100, 172], message = "Valid drag color, dark=$darkMode")
                    assertEquals(expected = pixels[100, 52], actual = pixels[100, 292], message = "Invalid drag color, dark=$darkMode")
                } finally {
                    scene.close()
                }
            }
        } finally {
            if (source.id == asset.id) {
                AudioLibraryRepository.remove(sourceId = asset.id)
            }
        }
    }

    @Test
    fun movingAClipDoesNotDiscardOrRecalculateItsWidthBasedWaveform() {
        val source = Float32AudioSource(id = "drag-envelope", sampleRate = 1000, channels = 1, samples = FloatArray(size = 128) { 0.5f })
        val position = mutableStateOf(value = 0L)
        val observed = mutableListOf<FloatArray>()
        val recomposer = FrameRecomposer(coroutineContext = Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(frameRecomposer = recomposer, size = IntSize(width = 64, height = 64))
        var frame = 0L
        fun render() {
            frame += 16_000_000L
            recomposer.performFrame(frameTimeNanos = frame)
        }
        try {
            scene.setContent {
                val envelope = rememberWaveformEnvelope(
                    source = source,
                    startSample = 0L,
                    endSample = 128L,
                    zoomLevel = 1f,
                    sampleRate = 1000,
                    timelineStartUs = position.value,
                    widthPx = 64,
                )
                SideEffect { observed.add(element = envelope) }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L)
            while (observed.lastOrNull()?.isNotEmpty() != true) {
                assertTrue(actual = System.nanoTime() < deadline)
                render()
                Thread.sleep(2L)
            }
            val original = observed.last()
            observed.clear()
            for (offset in listOf(125_000L, 375_000L, 1_000_000L)) {
                position.value = offset
                render()
            }
            assertTrue(actual = observed.isNotEmpty())
            observed.forEach { assertSame(expected = original, actual = it) }
        } finally {
            scene.close()
            recomposer.close()
        }
    }

    @Test
    fun changingTheVisibleRangeKeepsTheLastWaveformWhileNewBucketsAreComputed() {
        val calculating = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blockReads = AtomicBoolean(false)
        val source = object : AudioSource {
            override val id = "blocked-envelope"
            override val sampleRate = 1000
            override val channels = 1
            override val frameCount = 128L
            override fun sample(frameIndex: Long, channel: Int): Float {
                if (blockReads.get()) {
                    calculating.countDown()
                    assertTrue(actual = release.await(3L, TimeUnit.SECONDS))
                }
                return 0.5f
            }
        }
        val start = mutableStateOf(value = 0L)
        val observed = mutableListOf<FloatArray>()
        val recomposer = FrameRecomposer(coroutineContext = Dispatchers.Unconfined)
        val scene = CanvasLayersComposeScene(frameRecomposer = recomposer, size = IntSize(width = 64, height = 64))
        var frame = 0L
        fun render() {
            frame += 16_000_000L
            recomposer.performFrame(frameTimeNanos = frame)
        }
        try {
            scene.setContent {
                val envelope = rememberWaveformEnvelope(source = source, startSample = start.value, endSample = 128L, zoomLevel = 1f, sampleRate = 1000, widthPx = 64)
                SideEffect { observed.add(element = envelope) }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L)
            while (observed.lastOrNull()?.isNotEmpty() != true) {
                assertTrue(actual = System.nanoTime() < deadline)
                render()
                Thread.sleep(2L)
            }
            val original = observed.last()
            observed.clear()
            blockReads.set(true)
            start.value = 32L
            render()
            assertTrue(actual = calculating.await(3L, TimeUnit.SECONDS))
            assertTrue(actual = observed.isNotEmpty())
            observed.forEach { assertSame(expected = original, actual = it) }
        } finally {
            release.countDown()
            scene.close()
            recomposer.close()
        }
    }
}
