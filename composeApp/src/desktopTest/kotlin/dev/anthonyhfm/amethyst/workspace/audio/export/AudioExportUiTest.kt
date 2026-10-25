@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.anthonyhfm.amethyst.workspace.audio.export

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.ViewModelStore
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.TimelineViewModel
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.ui.views.TimelineLaneView
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat

class AudioExportUiTest {
    @AfterTest
    fun reset() {
        AudioExport.close()
        SelectionManager.clear()
        TimelineRepository.updateTracksSnapshot(updatedTracks = emptyList())
        AudioLibraryRepository.clear()
    }

    @Test
    fun sourceExportDialogShowsFormatAndQualityOptionsInBothThemes() {
        val source = audio()
        for (dark in listOf(true, false)) {
            AudioExport.openSource(source = source)
            val scene = ImageComposeScene(width = 900, height = 760) {
                AmethystTheme(darkMode = dark) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AudioExportHost()
                    }
                }
            }
            try {
                scene.render(nanoTime = 0L).close()
                scene.render(nanoTime = 500_000_000L).close()
                val image = scene.render(nanoTime = 900_000_000L)
                try {
                    saveImage(name = if (dark) "export-dark" else "export-light", bytes = assertNotNull(actual = image.encodeToData(format = EncodedImageFormat.PNG)).bytes)
                    val text = scene.semanticsOwners.flatMap { descendants(node = it.rootSemanticsNode) }
                        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.joinToString { it.text }
                    assertTrue(actual = text.contains(other = "1000 Hz"))
                    assertTrue(actual = text.contains(other = "24 Bit"))
                    assertTrue(actual = text.contains(other = "WAV"))
                    assertTrue(actual = text.contains(other = source.fileName))
                } finally {
                    image.close()
                }
            } finally {
                scene.close()
                AudioExport.close()
            }
        }
    }

    @Test
    fun waveformClicksAndDragsSelectAcrossRealTimelineLanes() {
        Dispatchers.setMain(dispatcher = UnconfinedTestDispatcher())
        val previousGrid = WorkspaceRepository.gridType.value
        val store = ViewModelStore()
        val source = AudioLibraryRepository.add(source = audio())
        val tracks = List(size = 3) {
            AudioTimelineTrack().apply {
                entries[0L] = AudioEntry(
                    startTimeMs = 0L,
                    durationMs = 400L,
                    sourceId = source.id,
                    fileName = source.fileName,
                    clipStartSample = 0L,
                    clipEndSample = 400L,
                    sampleRate = 1000,
                    channels = 1,
                    bitDepth = 16,
                )
            }
        }
        TimelineRepository.updateTracksSnapshot(updatedTracks = tracks)
        WorkspaceRepository.setGridType(type = GridUtils.GridType.NoGrid)
        val viewModel = TimelineViewModel()
        store.put(key = "timeline", viewModel = viewModel)
        viewModel.updateViewport { it.copy(zoomX = 1f, viewportWidth = 600f, contentWidth = 800f) }
        val scene = ImageComposeScene(width = 600, height = 440) {
            AmethystTheme {
                TimelineLaneView(viewModel = viewModel)
            }
        }
        try {
            scene.render(nanoTime = 0L).close()
            scene.render(nanoTime = 16_000_000L).close()
            fun pointer(type: PointerEventType, x: Float, y: Float, time: Long, pressed: Boolean, shift: Boolean = false) {
                scene.sendPointerEvent(
                    eventType = type,
                    position = Offset(x = x, y = y),
                    timeMillis = time,
                    buttons = PointerButtons(isPrimaryPressed = pressed),
                    keyboardModifiers = PointerKeyboardModifiers(isShiftPressed = shift),
                )
            }
            pointer(type = PointerEventType.Press, x = 50f, y = 55f, time = 1000L, pressed = true)
            pointer(type = PointerEventType.Release, x = 50f, y = 55f, time = 1020L, pressed = false)
            pointer(type = PointerEventType.Press, x = 250f, y = 307f, time = 2000L, pressed = true, shift = true)
            pointer(type = PointerEventType.Release, x = 250f, y = 307f, time = 2020L, pressed = false, shift = true)
            val ranges = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
            assertEquals(expected = listOf(0, 1, 2), actual = ranges.map { it.trackIndex })
            assertTrue(actual = ranges.all { it.startMs == 50L && it.endMs == 250L })
            val image = scene.render(nanoTime = 2_100_000_000L)
            saveImage(name = "timeline-area", bytes = assertNotNull(actual = image.encodeToData(format = EncodedImageFormat.PNG)).bytes)
            image.close()

            pointer(type = PointerEventType.Press, x = 100f, y = 55f, time = 3000L, pressed = true)
            pointer(type = PointerEventType.Move, x = 200f, y = 181f, time = 3020L, pressed = true)
            pointer(type = PointerEventType.Release, x = 200f, y = 181f, time = 3040L, pressed = false)
            val dragged = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
            assertEquals(expected = listOf(0, 1), actual = dragged.map { it.trackIndex })
            assertTrue(actual = dragged.all { it.startMs == 100L && it.endMs == 200L })

            pointer(type = PointerEventType.Press, x = 50f, y = 8f, time = 4000L, pressed = true)
            pointer(type = PointerEventType.Release, x = 50f, y = 8f, time = 4020L, pressed = false)
            assertTrue(actual = SelectionManager.selections.value.single() is Selectable.TimelineEntryItem)
        } finally {
            scene.close()
            store.clear()
            WorkspaceRepository.setGridType(type = previousGrid)
            Dispatchers.resetMain()
        }
    }

    private fun audio(): AudioSource = AudioSource(
        id = "export-ui-audio",
        fileName = "Vocals.wav",
        rawData = ByteArray(size = 800) { index -> if (index % 2 == 0) 0 else 32 },
        sampleRate = 1000,
        channels = 1,
        bitDepth = 16,
    )

    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { descendants(node = it) }

    private fun saveImage(name: String, bytes: ByteArray) {
        val directory = Paths.get("build", "reports", "audio-export")
        Files.createDirectories(directory)
        Files.write(directory.resolve("$name.png"), bytes)
    }
}
