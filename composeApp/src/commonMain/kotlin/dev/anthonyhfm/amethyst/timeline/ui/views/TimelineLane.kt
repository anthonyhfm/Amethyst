package dev.anthonyhfm.amethyst.timeline.ui.views

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.vinceglb.filekit.PlatformFile
import dev.anthonyhfm.amethyst.ui.dnd.fileDropTarget
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isPrimaryPressed
import dev.anthonyhfm.amethyst.timeline.ui.components.AudioClip
import dev.anthonyhfm.amethyst.timeline.ui.components.MidiClip
import dev.anthonyhfm.amethyst.timeline.ui.components.ChainEffectClip
import dev.anthonyhfm.amethyst.timeline.ui.components.SelectionCursor
import dev.anthonyhfm.amethyst.timeline.ui.components.timelineGridOverlay
import dev.anthonyhfm.amethyst.timeline.utils.computeSnappedTimeFromContentX
import dev.anthonyhfm.amethyst.timeline.utils.findHeaderEntryHit
import dev.anthonyhfm.amethyst.timeline.utils.isPointInsideAnyEntry
import dev.anthonyhfm.amethyst.timeline.utils.trackIndexOf
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.TimelineClipRole
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.nameWithoutExtension
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuContent
import dev.anthonyhfm.amethyst.ui.components.ContextMenuItem
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import dev.anthonyhfm.amethyst.settings.data.ExperimentalSettings
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.vinceglb.filekit.path
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipKey
import dev.anthonyhfm.amethyst.timeline.ui.TimelineClipDragCallbacks
import dev.anthonyhfm.amethyst.timeline.ui.components.AudioClipSkeletonView
import dev.anthonyhfm.amethyst.timeline.utils.computeSnappedTimeFromViewport
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState

@Composable
fun TimelineLane(
    track: TimelineTrack<*>,
    trackIndex: Int,
    nestingLevel: Int = 0,
    viewport: EditorViewportState,
    selectedTimeMs: Long?,
    selectedEntryStarts: Set<Long> = emptySet(),
    selectedChainEffectIds: Set<String> = emptySet(),
    onDropInFile: (file: PlatformFile, atTimeMs: Long) -> Unit = { _, _ -> },
    onSelectTime: (Long) -> Unit = {},
    onSelectEntry: (Long) -> Unit = {},
    onMoveEntry: (oldStart: Long, newStart: Long) -> Unit = { _, _ -> },
    clipDragCallbacks: (TimelineClipKey) -> TimelineClipDragCallbacks? = { null },
    onLaneBoundsChanged: (Int, Rect) -> Unit = { _, _ -> },
    onResizeEntry: (oldStart: Long, newStart: Long, newDuration: Long) -> Unit = { _, _, _ -> },
    onDoubleClickLane: (Long) -> Unit = {},
    onCreateMidiClip: (Long, Long) -> Unit = { _, _ -> },
    onCreateChainEffect: (Long) -> Unit = {},
    onSelectChainEffect: (String) -> Unit = {},
    onMoveChainEffect: (String, Long) -> Unit = { _, _ -> },
    onResizeChainEffect: (String, Long, Long) -> Unit = { _, _, _ -> },
    onOpenChainEffect: (String) -> Unit = {},
) {
    val zoomLevel = viewport.zoomX
    val bpm by WorkspaceRepository.bpm.collectAsState()
    val gridType by WorkspaceRepository.gridType.collectAsState()
    var rangeStartMs by remember(track, zoomLevel) { mutableStateOf<Long?>(null) }
    var rangeEndMs by remember(track, zoomLevel) { mutableStateOf<Long?>(null) }
    var rangeActive by remember { mutableStateOf(false) }
    var isFileHovering by remember { mutableStateOf(false) }
    var hoverOffset by remember { mutableStateOf<Offset?>(null) }
    var hoverFiles by remember { mutableStateOf<List<PlatformFile>>(emptyList()) }
    var probedDurationMs by remember { mutableStateOf<Long?>(null) }
    var probedFileName by remember { mutableStateOf<String?>(null) }
    var lastProbedPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isFileHovering, hoverFiles) {
        if (isFileHovering) {
            val candidatePath = hoverFiles.firstOrNull { it.extension.lowercase() in Echo.getSupportedFormats() }?.path
                ?: Echo.getActiveDragFile()

            if (candidatePath != null && candidatePath != lastProbedPath) {
                lastProbedPath = candidatePath
                val ext = candidatePath.substringAfterLast('.').lowercase()
                if (ext in Echo.getSupportedFormats()) {
                    val meta = Echo.probeAudioFile(candidatePath)
                    if (meta != null && meta.durationMs > 0) {
                        probedDurationMs = meta.durationMs
                        probedFileName = candidatePath.substringAfterLast('/').substringBeforeLast('.')
                    }
                }
            }
        } else {
            probedDurationMs = null
            probedFileName = null
            lastProbedPath = null
        }
    }

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(Offset.Zero) }
    val selections by SelectionManager.selections.collectAsState()
    val selectedRange = selections.filterIsInstance<Selectable.TimelineRange>().firstOrNull { it.trackIndex == trackIndexOf(
        track
    )
    }
    val timelinePalette = TimelineTheme.palette
    val timelineDimensions = TimelineTheme.dimensions
    val headerHeightPx = with(LocalDensity.current) { timelineDimensions.clipHeaderHeight.toPx() }
    val childLaneTint = timelinePalette.selectionStroke.copy(alpha = 0.08f)
    val overlayAutomationLanes = track.overlayAutomationLanes()
    val stackedAutomationLanes = track.stackedAutomationLanes()
    val automationOverlayActive = overlayAutomationLanes.isNotEmpty()
    // Always-fresh viewport for pointer-input closures that are not keyed on scroll changes.
    val currentViewport = rememberUpdatedState(viewport)
    val currentSnapEnabled = rememberUpdatedState(!ModifierKeysState.isAltPressed)
    val laneInteractionModifier = if (automationOverlayActive) {
        Modifier
    } else {
        Modifier
            .pointerInput(track, zoomLevel, bpm, gridType) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                            val change = event.changes.firstOrNull() ?: continue
                            val pos = change.position
                            val headerHit = findHeaderEntryHit(
                                track,
                                currentViewport.value.screenToContentX(pos.x),
                                pos.y,
                                currentViewport.value.zoomX,
                                headerHeightPx
                            )
                            if (headerHit != null) {
                                onSelectEntry(headerHit)
                                change.consume()
                                continue
                            }
                            val snappedMs = computeSnappedTimeFromContentX(
                                currentViewport.value.screenToContentX(pos.x),
                                currentViewport.value.zoomX,
                                bpm,
                                gridType,
                                snapEnabled = currentSnapEnabled.value,
                            )
                            onSelectTime(snappedMs)
                            change.consume()
                        }
                    }
                }
            }
            .pointerInput(track, zoomLevel, bpm, gridType) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val headerHit = findHeaderEntryHit(
                            track,
                            currentViewport.value.screenToContentX(offset.x),
                            offset.y,
                            currentViewport.value.zoomX,
                            headerHeightPx
                        )
                        if (headerHit != null) {
                            onSelectEntry(headerHit)
                            rangeActive = false
                            rangeStartMs = null
                            rangeEndMs = null
                            return@detectDragGestures
                        }
                        val startMs = computeSnappedTimeFromContentX(
                            currentViewport.value.screenToContentX(offset.x),
                            currentViewport.value.zoomX,
                            bpm,
                            gridType,
                            snapEnabled = currentSnapEnabled.value,
                        )
                        if (!isPointInsideAnyEntry(track, startMs)) {
                            rangeStartMs = startMs
                            rangeEndMs = startMs
                            rangeActive = true
                        } else {
                            rangeActive = false
                            rangeStartMs = null
                            rangeEndMs = null
                        }
                    },
                    onDrag = { change, _ ->
                        if (rangeActive && rangeStartMs != null) {
                            val currentMs = computeSnappedTimeFromContentX(
                                currentViewport.value.screenToContentX(change.position.x),
                                currentViewport.value.zoomX,
                                bpm,
                                gridType,
                                snapEnabled = currentSnapEnabled.value,
                            )
                            if (currentMs != rangeEndMs) rangeEndMs = currentMs
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (rangeActive && rangeStartMs != null && rangeEndMs != null) {
                            val start = rangeStartMs!!.coerceAtLeast(0L)
                            val end = rangeEndMs!!.coerceAtLeast(0L)
                            val normalizedStart = min(start, end)
                            val normalizedEnd = maxOf(start, end)
                            if (normalizedEnd > normalizedStart) {
                                SelectionManager.select(
                                    Selectable.TimelineRange(
                                        trackIndex = trackIndexOf(track),
                                        startMs = normalizedStart,
                                        endMs = normalizedEnd
                                    )
                                )
                            } else {
                                SelectionManager.select(
                                    Selectable.TimelineTime(
                                        trackIndex = trackIndexOf(track),
                                        timeMs = normalizedStart
                                    )
                                )
                            }
                        }
                        rangeActive = false
                    },
                    onDragCancel = {
                        rangeActive = false
                        rangeStartMs = null
                        rangeEndMs = null
                    }
                )
            }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TimelineAutomationLaneRowSpacing)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(timelineDimensions.laneHeight)
                .onGloballyPositioned { onLaneBoundsChanged(trackIndex, it.boundsInRoot()) }
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            timelinePalette.laneSurfaceRaised.copy(alpha = 0.42f),
                            when {
                                nestingLevel > 0 -> timelinePalette.laneSurface.mixWith(childLaneTint)
                                else -> timelinePalette.laneSurface
                            }
                        )
                    ),
                )
                .timelineGridOverlay(
                    viewport = viewport,
                    bpm = bpm,
                    gridType = gridType,
                    drawBehind = true,
                )
                .border(
                    width = 1.dp,
                    color = timelinePalette.shellBorder.copy(alpha = 0.78f),
                )
                .fileDropTarget(
                    onHover = { hovering, offset, files ->
                        isFileHovering = hovering
                        hoverOffset = offset
                        hoverFiles = files
                    },
                    onDrop = { offset, files ->
                        isFileHovering = false
                        hoverOffset = null
                        hoverFiles = emptyList()
                        val audioFiles = files.filter { it.extension.lowercase() in Echo.getSupportedFormats() }
                        if (audioFiles.isNotEmpty()) {
                            val localX = offset?.x ?: 0f
                            val dropTimeMs = computeSnappedTimeFromViewport(
                                screenX = localX,
                                viewport = currentViewport.value,
                                bpm = bpm,
                                gridType = gridType,
                                snapEnabled = currentSnapEnabled.value,
                            ).coerceAtLeast(0L)
                            onDropInFile(audioFiles.first(), dropTimeMs)
                        }
                    }
                )
                .clipToBounds()
                .rightClickable { position ->
                    if (track is MidiTimelineTrack) {
                        contextMenuPosition = position
                        showContextMenu = true
                    }
                }
                .then(laneInteractionModifier)
        ) {
        if (isFileHovering && hoverOffset != null && track is AudioTimelineTrack) {
            val localX = hoverOffset!!.x
            val snappedHoverTimeMs = computeSnappedTimeFromViewport(
                screenX = localX,
                viewport = currentViewport.value,
                bpm = bpm,
                gridType = gridType,
                snapEnabled = currentSnapEnabled.value,
            ).coerceAtLeast(0L)

            val screenStartPx = currentViewport.value.timeMsToScreenX(snappedHoverTimeMs.toDouble())
            val defaultBarMs = ((60_000.0 / bpm.coerceAtLeast(1.0)) * 4.0).toLong()
            val durationMs = probedDurationMs ?: (defaultBarMs * 4L)
            val durationWidthPx = (durationMs.toDouble() * zoomLevel.toDouble()).toFloat()
            val previewWidthDp = maxOf(with(LocalDensity.current) { durationWidthPx.toDp() }, 140.dp)

            val audioClipColors = TimelineTheme.clipColors(
                role = TimelineClipRole.Audio,
                selected = false
            )
            val clipShape = RoundedCornerShape(timelineDimensions.clipCornerRadius)
            val clipHeaderShape = RoundedCornerShape(
                topStart = timelineDimensions.clipCornerRadius,
                topEnd = timelineDimensions.clipCornerRadius
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(screenStartPx.roundToInt(), 0) }
                    .width(previewWidthDp)
                    .height(timelineDimensions.laneHeight)
                    .zIndex(20f)
                    .clip(clipShape)
                    .background(audioClipColors.background.copy(alpha = 0.88f))
                    .border(
                        width = 1.dp,
                        color = audioClipColors.border,
                        shape = clipShape
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Identical clip header bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(timelineDimensions.clipHeaderHeight)
                            .background(audioClipColors.header, clipHeaderShape)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val fileNameText = probedFileName ?: hoverFiles.firstOrNull()?.name?.substringBeforeLast('.')
                        val formattedDuration = if (probedDurationMs != null) {
                            val totalSecs = (durationMs / 1000L).coerceAtLeast(0L)
                            val mins = totalSecs / 60L
                            val secs = totalSecs % 60L
                            "$mins:${secs.toString().padStart(2, '0')}"
                        } else null

                        val label = when {
                            fileNameText != null && formattedDuration != null -> "$fileNameText ($formattedDuration)"
                            fileNameText != null -> fileNameText
                            formattedDuration != null -> "Audio Clip ($formattedDuration)"
                            else -> "Audio Clip"
                        }
                        Text(
                            text = label,
                            style = Theme[typography][small].copy(
                                color = audioClipColors.content,
                                fontWeight = FontWeight.SemiBold
                            ),
                            singleLine = true,
                        )
                    }

                    // Body: clean neutral skeleton with center baseline
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerY = size.height / 2f
                            drawLine(
                                color = audioClipColors.content.copy(alpha = 0.40f),
                                start = Offset(0f, centerY),
                                end = Offset(size.width, centerY),
                                strokeWidth = 1f
                            )
                        }
                    }
                }
            }
        }
        if (showContextMenu && track is MidiTimelineTrack) {
            val targetStartMs: Long
            val targetEndMs: Long
            if (selectedRange != null) {
                targetStartMs = selectedRange.startMs
                targetEndMs = selectedRange.endMs
            } else {
                val clickedTimeMs = viewport.screenToTimeMs(contextMenuPosition.x).toLong().coerceAtLeast(0L)
                targetStartMs = GridUtils.snapToGrid(clickedTimeMs, zoomLevel, WorkspaceRepository.bpm.value, gridType)
                val defaultBarMs = (60_000.0 / WorkspaceRepository.bpm.value.coerceAtLeast(1.0)).toLong() * 4L
                targetEndMs = targetStartMs + defaultBarMs
            }

            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val x = anchorBounds.left + contextMenuPosition.x.toInt()
                        val y = anchorBounds.top + contextMenuPosition.y.toInt()
                        return IntOffset(
                            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
                        )
                    }
                },
                onDismissRequest = { showContextMenu = false },
                properties = PopupProperties(focusable = true),
            ) {
                ContextMenuContent {
                    ContextMenuItem(
                        label = stringResource(Res.string.timeline_lane_create_midi_clip),
                        icon = Lucide.Plus,
                        onClick = {
                            onCreateMidiClip(targetStartMs, targetEndMs)
                            showContextMenu = false
                        }
                    )

                    if (ExperimentalSettings.timelineChainEffects.value) {
                        ContextMenuSeparator()
                        
                        ContextMenuItem(
                            label = stringResource(Res.string.timeline_lane_create_chain_effect),
                            icon = Lucide.Plus,
                            onClick = {
                                onCreateChainEffect(targetStartMs)
                                showContextMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Viewport-relative clip rendering: no large offset container is used.
        // Each clip positions itself at (contentX - scrollX) in screen space, which
        // keeps clamp() and zoomAtX() anchoring correct for any timeline length.
        if (nestingLevel > 0) {
            Box(
                modifier = Modifier
                    .width((nestingLevel * 10).dp)
                    .height(timelineDimensions.laneHeight)
                    .background(timelinePalette.selectionStroke.copy(alpha = 0.08f))
                    .zIndex(0.4f)
            )
        }

        val overlayStart = when {
            rangeActive && rangeStartMs != null && rangeEndMs != null -> min(rangeStartMs!!, rangeEndMs!!)
            selectedRange != null -> selectedRange.startMs
            else -> null
        }
        val overlayEnd = when {
            rangeActive && rangeStartMs != null && rangeEndMs != null -> maxOf(rangeStartMs!!, rangeEndMs!!)
            selectedRange != null -> selectedRange.endMs
            else -> null
        }
        if (overlayStart != null && overlayEnd != null && overlayEnd > overlayStart) {
            // Use screen-space X so the overlay follows scroll correctly.
            val screenStartPx = viewport.timeMsToScreenX(overlayStart.toDouble())
            val widthPx = viewport.timeMsToContentX(overlayEnd.toDouble()) - viewport.timeMsToContentX(overlayStart.toDouble())
            Box(
                modifier = Modifier
                    .offset { IntOffset(screenStartPx.roundToInt(), 0) }
                    .width(with(LocalDensity.current) { widthPx.toDp() })
                    .height(timelineDimensions.laneHeight)
                    .background(timelinePalette.selectionFill)
                    .border(1.dp, timelinePalette.selectionStroke, RoundedCornerShape(timelineDimensions.selectionCornerRadius))
                    .zIndex(0.5f)
            ) {}
        }
        // Clips fill the lane Box directly — their offset is computed in screen space.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(timelineDimensions.laneHeight)
        ) {
            when (track) {
                is AudioTimelineTrack -> {
                    track.entries.values.sortedBy { it.startTimeMs }.forEach { audioEntry ->
                        androidx.compose.runtime.key(audioEntry.startTimeMs) {
                            val isSelectedEntry = audioEntry.startTimeMs in selectedEntryStarts
                            AudioClip(
                                audioEntry = audioEntry,
                                viewport = viewport,
                                isSelected = isSelectedEntry,
                                automationOverlayActive = automationOverlayActive,
                                onSelectEntry = { onSelectEntry(audioEntry.startTimeMs) },
                                onMoveEntry = { newStart -> onMoveEntry(audioEntry.startTimeMs, newStart) },
                                gridIntervalMs = GridUtils.computeWithGridType(zoomLevel, bpm, gridType).intervalMs,
                                trackIndex = trackIndex,
                                entryStartMs = audioEntry.startTimeMs,
                                bpm = bpm,
                                gridType = gridType,
                                dragCallbacks = clipDragCallbacks(TimelineClipKey(trackIndex, audioEntry.startTimeMs)),
                            )
                        }
                    }
                }
                is MidiTimelineTrack -> {
                    track.entries.values.sortedBy { it.startTimeMs }.forEach { midiEntry ->
                        androidx.compose.runtime.key(midiEntry.startTimeMs) {
                            val isSelectedEntry = midiEntry.startTimeMs in selectedEntryStarts
                            MidiClip(
                                midiEntry = midiEntry,
                                viewport = viewport,
                                isSelected = isSelectedEntry,
                                onSelectEntry = { onSelectEntry(midiEntry.startTimeMs) },
                                onMoveEntry = { newStart -> onMoveEntry(midiEntry.startTimeMs, newStart) },
                                onResizeEntry = { oldStart, newStart, newDuration -> onResizeEntry(oldStart, newStart, newDuration) },
                                gridIntervalMs = GridUtils.computeWithGridType(zoomLevel, bpm, gridType).intervalMs,
                                isLightsTrack = true,
                                onDoubleClick = { onDoubleClickLane(midiEntry.startTimeMs) },
                                trackIndex = trackIndex,
                                entryStartMs = midiEntry.startTimeMs,
                                bpm = bpm,
                                gridType = gridType,
                                dragCallbacks = clipDragCallbacks(TimelineClipKey(trackIndex, midiEntry.startTimeMs)),
                            )
                        }
                    }
                    val experimentalChainEffectEnabled by dev.anthonyhfm.amethyst.settings.data.ExperimentalSettings.timelineChainEffects.flow.collectAsState()
                    if (experimentalChainEffectEnabled) {
                        track.chainEffectEntries.values.sortedBy { it.startTimeMs }.forEach { chainEntry ->
                            androidx.compose.runtime.key(chainEntry.clipId) {
                                ChainEffectClip(
                                    entry = chainEntry,
                                    viewport = viewport,
                                    isSelected = chainEntry.clipId in selectedChainEffectIds,
                                    onSelect = { onSelectChainEffect(chainEntry.clipId) },
                                    onMove = { newStart -> onMoveChainEffect(chainEntry.clipId, newStart) },
                                    onResize = { newStart, newDuration ->
                                        onResizeChainEffect(chainEntry.clipId, newStart, newDuration)
                                    },
                                    onDoubleClick = { onOpenChainEffect(chainEntry.clipId) },
                                )
                            }
                        }
                    }
                }
            }
        }
        SelectionCursor(
            selectedTimeMs = selectedTimeMs,
            viewport = viewport,
            laneHeight = timelineDimensions.laneHeight,
        )
        overlayAutomationLanes.forEach { automationLane ->
            TimelineAutomationLaneRow(
                trackIndex = trackIndex,
                track = track,
                lane = automationLane,
                viewport = viewport,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1.2f),
                rowHeight = timelineDimensions.laneHeight,
                overlayMode = true,
            )
        }
        }

        stackedAutomationLanes.forEach { automationLane ->
            TimelineAutomationLaneRow(
                trackIndex = trackIndex,
                track = track,
                lane = automationLane,
                viewport = viewport,
            )
        }
    }
}

@Composable
private fun TrackLaneBadge(
    text: String,
    tint: Color,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .background(tint.copy(alpha = 0.14f), SmallShape)
            .border(1.dp, tint.copy(alpha = 0.35f), SmallShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = Theme[typography][small].copy(
                color = tint.copy(alpha = 0.92f),
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

private fun Color.mixWith(other: Color): Color {
    return Color(
        red = (red + other.red) / 2f,
        green = (green + other.green) / 2f,
        blue = (blue + other.blue) / 2f,
        alpha = (alpha + other.alpha) / 2f
    )
}
