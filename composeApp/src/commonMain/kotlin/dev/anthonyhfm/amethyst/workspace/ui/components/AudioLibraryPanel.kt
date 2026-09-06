package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.GripVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.ui.components.WaveformView
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollArea
import dev.anthonyhfm.amethyst.ui.dnd.fileDropTarget
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.h4
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.mutedText
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import dev.anthonyhfm.amethyst.workspace.audio.LocalAudioLibraryDragAndDropState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.extension
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun AudioLibraryPanel(modifier: Modifier = Modifier) {
    val sources by AudioLibraryRepository.sources.collectAsState()
    val sourceOrder by AudioLibraryRepository.sourceOrder.collectAsState()
    val preview by AudioLibraryRepository.previewState.collectAsState()
    val dragState = LocalAudioLibraryDragAndDropState.current
    val reorderState = rememberReorderState<AudioSource>()
    val scope = rememberCoroutineScope()
    var isFileHovering by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importFailed by remember { mutableStateOf(false) }

    fun importFiles(files: List<PlatformFile>) {
        val supported = files.filter { it.extension.lowercase() in Echo.getSupportedFormats() }
        if (supported.isEmpty()) return
        scope.launch {
            isImporting = true
            importFailed = false
            try {
                supported.forEach { file ->
                    if (AudioLibraryRepository.importFile(file) == null) importFailed = true
                }
            } catch (_: Exception) {
                importFailed = true
            } finally {
                isImporting = false
            }
        }
    }

    Column(
        modifier = modifier
            .width(350.dp)
            .fillMaxHeight()
            .background(Theme[colors][background])
            .then(if (isFileHovering) Modifier.border(2.dp, Theme[colors][primary]) else Modifier)
            .fileDropTarget(
                onHover = { hovering, _, files ->
                    isFileHovering = hovering && files.any {
                        it.extension.lowercase() in Echo.getSupportedFormats()
                    }
                },
                onDrop = { _, files ->
                    isFileHovering = false
                    importFiles(files)
                },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.audio_library_title),
                style = Theme[typography][h4].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][foreground],
            )
            Spacer(Modifier.weight(1f))
            val importLabel = stringResource(Res.string.audio_library_import)
            WorkspaceToolbarIconButton(
                onClick = {
                    scope.launch {
                        val file = FileKit.openFilePicker(
                            mode = FileKitMode.Single,
                            title = importLabel,
                            type = FileKitType.File(extensions = Echo.getSupportedFormats()),
                        )
                        file?.let { importFiles(listOf(it)) }
                    }
                },
                imageVector = Lucide.Plus,
                contentDescription = importLabel,
                enabled = !isImporting,
            )
        }

        if (isImporting || importFailed) {
            Text(
                text = stringResource(
                    if (isImporting) Res.string.audio_library_importing
                    else Res.string.audio_library_import_failed
                ),
                style = Theme[typography][mutedText],
                color = if (importFailed) Color(0xFFE57373) else Theme[colors][mutedForeground],
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (sources.isEmpty()) {
            AudioLibraryEmptyState(Modifier.weight(1f))
        } else {
            ScrollArea(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ReorderContainer(
                    state = reorderState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(start = 4.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        sourceOrder.mapNotNull(sources::get).forEachIndexed { index, source ->
                            val itemProgress = if (preview.sourceId == source.id) preview.progress(source) else 0f
                            val isPlaying = preview.sourceId == source.id && preview.isPlaying
                            ReorderableItem(
                                state = reorderState,
                                key = source.id,
                                data = source,
                                useDragAnchor = true,
                                draggableContent = { AudioLibraryDragPreview(source) },
                                onDragEnter = { dragged ->
                                    val currentOrder = AudioLibraryRepository.all()
                                    val fromIndex = currentOrder.indexOfFirst { it.id == dragged.data.id }
                                    if (fromIndex != -1 && fromIndex != index) {
                                        AudioLibraryRepository.move(dragged.data.id, index)
                                    }
                                },
                            ) {
                                val reorderHandle = Modifier
                                    .blockParentAudioDrag()
                                    .dragAnchor()
                                if (dragState == null) {
                                    AudioLibraryItem(
                                        source = source,
                                        progress = itemProgress,
                                        isPlaying = isPlaying,
                                        reorderHandleModifier = reorderHandle,
                                    )
                                } else {
                                    DraggableItem(
                                        state = dragState,
                                        key = source.id,
                                        data = source,
                                        useDragAnchor = true,
                                        requireFirstDownUnconsumed = true,
                                        draggableContent = { AudioLibraryDragPreview(source) },
                                    ) {
                                        val exportDragArea = Modifier.dragAnchor()
                                        AudioLibraryItem(
                                            source = source,
                                            progress = itemProgress,
                                            isPlaying = isPlaying,
                                            exportDragModifier = exportDragArea,
                                            reorderHandleModifier = reorderHandle,
                                            modifier = Modifier.alpha(
                                                if (
                                                    dragState.draggedItem?.key == source.id ||
                                                    reorderState.draggedItem?.key == source.id
                                                ) 0.42f else 1f
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLibraryItem(
    source: AudioSource,
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    exportDragModifier: Modifier = Modifier,
    reorderHandleModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DefaultShape)
            .background(Theme[colors][card])
            .border(1.dp, Theme[colors][border], DefaultShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = exportDragModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { AudioLibraryRepository.togglePreview(source.id) },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Icon,
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Lucide.Pause else Lucide.Play,
                    contentDescription = stringResource(
                        if (isPlaying) Res.string.audio_library_pause else Res.string.audio_library_play
                    ),
                    tint = Theme[colors][secondaryForeground],
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    text = source.fileName,
                    style = Theme[typography][small].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][foreground],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatDuration(source.totalDurationMs)} · ${formatSampleRate(source.sampleRate)} · ${source.channels} ch",
                    style = Theme[typography][mutedText],
                    color = Theme[colors][mutedForeground],
                    maxLines = 1,
                )
            }

            Box(modifier = reorderHandleModifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Lucide.GripVertical,
                    contentDescription = stringResource(Res.string.audio_library_reorder),
                    tint = Theme[colors][mutedForeground],
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(DefaultShape)
                .background(Theme[colors][muted]),
        ) {
            AudioLibraryWaveform(source, progress, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AudioLibraryWaveform(source: AudioSource, progress: Float, modifier: Modifier = Modifier) {
    val baseColor = Theme[colors][mutedForeground]
    val activeColor = Theme[colors][primary]
    Box(modifier) {
        WaveformView(
            rawData = source.rawData,
            sampleRate = source.sampleRate,
            channels = source.channels,
            bitDepth = source.bitDepth,
            timelineStartUs = 0L,
            startSample = 0L,
            endSample = source.totalSamples,
            zoomLevel = 1f,
            waveColor = baseColor,
            playedProgress = progress,
            playedWaveColor = activeColor,
            onSeek = { AudioLibraryRepository.seekPreview(source.id, it) },
            modifier = Modifier.fillMaxSize(),
        )
        if (progress > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width * progress.coerceIn(0f, 1f)
                drawLine(activeColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            }
        }
    }
}

@Composable
private fun AudioLibraryDragPreview(source: AudioSource) {
    Row(
        modifier = Modifier
            .width(260.dp)
            .clip(DefaultShape)
            .background(Theme[colors][card])
            .border(1.dp, Theme[colors][primary], DefaultShape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Lucide.Music, null, tint = Theme[colors][primary], modifier = Modifier.size(20.dp))
        Text(
            text = source.fileName,
            style = Theme[typography][small].copy(fontWeight = FontWeight.SemiBold),
            color = Theme[colors][foreground],
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AudioLibraryEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Theme[colors][secondary]),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Lucide.Music,
                    contentDescription = null,
                    tint = Theme[colors][mutedForeground],
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = stringResource(Res.string.audio_library_title),
                style = Theme[typography][h4].copy(fontWeight = FontWeight.Medium),
                color = Theme[colors][foreground],
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.audio_library_hint),
                style = Theme[typography][mutedText],
                color = Theme[colors][mutedForeground],
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    return "${totalSeconds / 60L}:${(totalSeconds % 60L).toString().padStart(2, '0')}"
}

private fun formatSampleRate(sampleRate: Int): String =
    if (sampleRate % 1000 == 0) "${sampleRate / 1000} kHz"
    else "${sampleRate / 1000f} kHz"

/** Keeps the reorder handle from also arming the parent library-export drag. */
private fun Modifier.blockParentAudioDrag(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
    }
}
