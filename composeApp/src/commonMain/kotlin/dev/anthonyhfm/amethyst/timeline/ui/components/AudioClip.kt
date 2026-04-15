package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.timeline.TimelineCommandExecutor
import dev.anthonyhfm.amethyst.timeline.TimelineCommandSurface
import dev.anthonyhfm.amethyst.timeline.TimelineEditCommand
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.endTimeUs
import dev.anthonyhfm.amethyst.timeline.data.msToUs
import dev.anthonyhfm.amethyst.timeline.ui.TimelineContextMenuAction
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.components.WaveformView
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.modifier.onFocusSelectAll
import dev.anthonyhfm.amethyst.ui.theme.TimelineClipRole
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun AudioClip(
    audioEntry: AudioEntry,
    viewport: EditorViewportState,
    isSelected: Boolean,
    onSelectEntry: () -> Unit,
    onMoveEntry: (newStartMs: Long) -> Unit,
    gridIntervalMs: Long,
    trackIndex: Int,
    entryStartMs: Long,
    bpm: Double,
    gridType: GridUtils.GridType
) {
    val zoomLevel = viewport.zoomX
    val scrollOffsetPx = viewport.scrollX
    val timelineDimensions = TimelineTheme.dimensions
    val timelinePalette = TimelineTheme.palette
    val clipColors = TimelineTheme.clipColors(
        role = TimelineClipRole.Audio,
        selected = isSelected
    )
    val clipShape = RoundedCornerShape(timelineDimensions.clipCornerRadius)
    val clipHeaderShape = RoundedCornerShape(
        topStart = timelineDimensions.clipCornerRadius,
        topEnd = timelineDimensions.clipCornerRadius
    )

    var rangeActive by remember { mutableStateOf(false) }
    var rangeStartMs by remember { mutableStateOf<Long?>(null) }
    var rangeEndMs by remember { mutableStateOf<Long?>(null) }

    fun timeUsToPx(timeUs: Long): Int =
        ((timeUs.toDouble() / 1000.0) * zoomLevel.toDouble()).roundToInt()

    // Project the exact audio bounds and derive width from the projected end to keep the
    // visual clip edge stable across split/zoom operations.
    val startOffsetPx = timeUsToPx(audioEntry.startTimeUs)
    val endOffsetPx = timeUsToPx(audioEntry.endTimeUs)
    val fullWidthPx = (endOffsetPx - startOffsetPx).coerceAtLeast(1)

    // State that must be declared before any early return so Compose hook order is stable.
    val dragOffsetPx = remember(audioEntry.startTimeMs) { mutableStateOf(0f) }
    var snapEnabled by remember { mutableStateOf(true) }

    // Screen-space left edge of the clip (can be negative when scrolled past clip start).
    val viewportWidthPx = viewport.viewportWidth.coerceAtLeast(1f).toInt()
    val screenLeftPx = startOffsetPx - scrollOffsetPx.roundToInt() + dragOffsetPx.value.roundToInt()
    val screenRightPx = screenLeftPx + fullWidthPx
    
    // Rename support
    val displayName = if (audioEntry.name.isNotEmpty()) audioEntry.name else audioEntry.fileName.substringBeforeLast('.')
    val selections by SelectionManager.selections.collectAsState()
    val contextEntryTargets = TimelineCommandSurface.entryTargetsForContext(trackIndex, entryStartMs, selections)
    val renameTarget = contextEntryTargets.singleOrNull()
    val renamingEntryIndex = remember { mutableStateOf<Pair<Int, Long>?>(null) }
    val renaming = renamingEntryIndex.value == Pair(trackIndex, entryStartMs)
    
    LaunchedEffect(trackIndex, entryStartMs) {
        SelectionManager.renameRequest.collect { req ->
            if (req is SelectionManager.RenameTarget.TimelineEntry &&
                req.trackIndex == trackIndex &&
                req.entryStartMs == entryStartMs) {
                renamingEntryIndex.value = Pair(trackIndex, entryStartMs)
            }
        }
    }

    val textValue = remember { mutableStateOf(TextFieldValue(displayName)) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(renaming) {
        if (renaming) {
            textValue.value = TextFieldValue(displayName)
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }

    LaunchedEffect(isSelected) {
        if (!isSelected && renaming) {
            renamingEntryIndex.value = null
            textValue.value = TextFieldValue(displayName)
        }
    }
    
    val previewStartMs by remember(dragOffsetPx.value, zoomLevel, snapEnabled) {
        derivedStateOf {
            val rawDeltaMsDouble = dragOffsetPx.value / zoomLevel
            val candidateMsDouble = audioEntry.startTimeMs.toDouble() + rawDeltaMsDouble
            val nonNegativeCandidate = candidateMsDouble.coerceAtLeast(0.0)
            if (snapEnabled && gridIntervalMs > 0) {
                val gridPxSpacing = gridIntervalMs * zoomLevel
                val thresholdPx = (gridPxSpacing * 0.35f).coerceAtLeast(5f)
                GridUtils.snapToGridWithThreshold(nonNegativeCandidate.roundToLong(), zoomLevel, WorkspaceRepository.bpm.value, WorkspaceRepository.gridType.value, thresholdPx)
            } else round(nonNegativeCandidate).toLong()
        }
    }
    
    // Visibility culling: skip layout entirely if clip is off-screen.
    // All state above must be declared before this return so Compose hook order stays stable.
    if (screenRightPx < -100 || screenLeftPx > viewportWidthPx + 100) return

    // Dynamic width cap: render at most enough pixels to cover the visible portion
    // plus a small overflow buffer. Compose's hard layout limit is 262 143 px.
    val overflowLeft = maxOf(0, -screenLeftPx)
    val widthPx = minOf(fullWidthPx, viewportWidthPx + overflowLeft + 200).coerceAtMost(260_000)
    val widthDp = with(LocalDensity.current) { widthPx.toDp() }

    ContextMenu(
        modifier = Modifier
            .offset { IntOffset(screenLeftPx, 0) }
            .height(timelineDimensions.laneHeight)
            .width(widthDp),
        trigger = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(clipShape)
                    .background(clipColors.background.copy(alpha = if (isSelected) 0.98f else 0.90f))
                    .border(if (isSelected) 1.5.dp else 1.dp, clipColors.border, clipShape)
            ) {
        if (!renaming) {
            Text(
                text = displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(timelineDimensions.clipHeaderHeight)
                    .background(clipColors.header, clipHeaderShape)
                    .pointerInput(audioEntry.startTimeMs) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press) {
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        val isShiftPressed = event.keyboardModifiers.isShiftPressed
                                        if (isShiftPressed) {
                                            // Multi-select mode
                                            SelectionManager.select(
                                                Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = entryStartMs),
                                                single = false
                                            )
                                        } else {
                                            // Single select mode
                                            onSelectEntry()
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(audioEntry.startTimeMs, zoomLevel, gridIntervalMs) {
                        detectDragGestures(
                            onDragStart = { onSelectEntry() },
                            onDragEnd = {
                                if (previewStartMs != audioEntry.startTimeMs) onMoveEntry(previewStartMs)
                                dragOffsetPx.value = 0f
                                snapEnabled = true
                            },
                            onDragCancel = { dragOffsetPx.value = 0f; snapEnabled = true },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetPx.value += dragAmount.x
                            }
                        )
                    }
                    .padding(4.dp),
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = MaterialTheme.typography.labelSmall.fontSize),
                color = clipColors.content,
                maxLines = 1
            )
        } else {
            val customTextSelectionColors = TextSelectionColors(
                handleColor = timelinePalette.selectionStroke,
                backgroundColor = timelinePalette.selectionFill
            )

            CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                BasicTextField(
                    value = textValue.value,
                    onValueChange = { textValue.value = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(timelineDimensions.clipHeaderHeight)
                        .background(clipColors.header, clipHeaderShape)
                        .focusRequester(focusRequester)
                        .onFocusSelectAll(textValue)
                        .onKeyEvent { ev ->
                            if (ev.key == Key.Enter) {
                                TimelineCommandExecutor.execute(
                                    TimelineEditCommand.RenameEntry(
                                        trackIndex = trackIndex,
                                        entryStartTime = entryStartMs,
                                        newName = textValue.value.text
                                    )
                                )
                                renamingEntryIndex.value = null
                                return@onKeyEvent true
                            }

                            if (ev.key == Key.Escape) {
                                renamingEntryIndex.value = null
                                textValue.value = TextFieldValue(displayName)
                                return@onKeyEvent true
                            }

                            return@onKeyEvent false
                        }
                        .padding(4.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Unspecified,
                        imeAction = ImeAction.Done
                    ),
                    textStyle = MaterialTheme.typography.labelSmall.copy(
                        lineHeight = MaterialTheme.typography.labelSmall.fontSize,
                        color = clipColors.content
                    ),
                    cursorBrush = SolidColor(clipColors.content),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(audioEntry.startTimeMs, zoomLevel, bpm, gridType) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // offset.x is clip-relative (0..clipWidth). Convert to timeline ms
                            // via the content-space position: clipStart_contentX + offset.x.
                            val startMs = GridUtils.snapToGrid(
                                viewport.contentXToTimeMs(startOffsetPx + offset.x).roundToLong().coerceAtLeast(0L),
                                zoomLevel, bpm, gridType
                            )
                            rangeStartMs = startMs
                            rangeEndMs = startMs
                            rangeActive = true
                        },
                        onDrag = { change, _ ->
                            if (rangeActive && rangeStartMs != null) {
                                val currentMs = GridUtils.snapToGrid(
                                    viewport.contentXToTimeMs(startOffsetPx + change.position.x).roundToLong().coerceAtLeast(0L),
                                    zoomLevel, bpm, gridType
                                )
                                if (currentMs != rangeEndMs) {
                                    rangeEndMs = currentMs
                                }
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (rangeActive && rangeStartMs != null && rangeEndMs != null) {
                                val start = rangeStartMs!!.coerceAtLeast(0L)
                                val end = rangeEndMs!!.coerceAtLeast(0L)
                                val normalizedStart = kotlin.math.min(start, end)
                                val normalizedEnd = kotlin.math.max(start, end)
                                if (normalizedEnd > normalizedStart) {
                                    SelectionManager.select(
                                        Selectable.TimelineRange(
                                            trackIndex = trackIndex,
                                            startMs = normalizedStart,
                                            endMs = normalizedEnd
                                        )
                                    )
                                }
                            }
                            rangeActive = false
                            rangeStartMs = null
                            rangeEndMs = null
                        },
                        onDragCancel = {
                            rangeActive = false
                            rangeStartMs = null
                            rangeEndMs = null
                        }
                    )
                }
                .drawWithContent {
                    drawContent()
                    // Draw range selection overlay
                    if (rangeActive && rangeStartMs != null && rangeEndMs != null) {
                        val start = kotlin.math.min(rangeStartMs!!, rangeEndMs!!)
                        val end = kotlin.math.max(rangeStartMs!!, rangeEndMs!!)

                        // Convert to clip-relative coordinates
                        val clipStartUs = audioEntry.startTimeUs
                        val clipEndUs = audioEntry.endTimeUs

                        // Clip the range to visible portion
                        val visibleStartUs = msToUs(start).coerceIn(clipStartUs, clipEndUs)
                        val visibleEndUs = msToUs(end).coerceIn(clipStartUs, clipEndUs)

                        if (visibleEndUs > visibleStartUs) {
                            val localStartPx = timeUsToPx(visibleStartUs) - startOffsetPx
                            val localEndPx = timeUsToPx(visibleEndUs) - startOffsetPx
                            val startX = localStartPx.toFloat()
                            val width = (localEndPx - localStartPx).coerceAtLeast(1).toFloat()

                            drawRect(
                                color = timelinePalette.selectionFill,
                                topLeft = Offset(startX, 0f),
                                size = Size(width, size.height)
                            )
                        }
                    }
                }
        ) {
            WaveformView(
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                waveColor = clipColors.content,
                rawData = audioEntry.source()?.rawData,
                sampleRate = audioEntry.sampleRate,
                channels = audioEntry.channels,
                bitDepth = audioEntry.bitDepth,
                timelineStartUs = audioEntry.startTimeUs,
                startSample = audioEntry.clipStartSample,
                endSample = audioEntry.clipEndSample,
                renderWidthPx = widthPx,
                zoomLevel = zoomLevel
            )
        }
            }
        }
    ) {
        TimelineContextMenuAction(
            label = "Rename Clip",
            shortcut = "⌘/Ctrl+R",
            enabled = renameTarget != null,
            onClick = {
                renameTarget?.let {
                    TimelineCommandSurface.requestEntryRename(
                        trackIndex = it.trackIndex,
                        entryStartMs = it.entryStartMs
                    )
                }
            }
        )
        TimelineContextMenuAction(
            label = if (contextEntryTargets.size > 1) "Duplicate Clips" else "Duplicate Clip",
            shortcut = "⌘/Ctrl+D",
            onClick = { TimelineCommandSurface.duplicateEntries(contextEntryTargets) }
        )
        ContextMenuSeparator()
        TimelineContextMenuAction(
            label = if (contextEntryTargets.size > 1) "Delete Clips" else "Delete Clip",
            shortcut = "Delete",
            destructive = true,
            onClick = { TimelineCommandSurface.deleteEntries(contextEntryTargets) }
        )
    }
}
