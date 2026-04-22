package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
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
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.ui.TimelineContextMenuAction
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.utils.computeVisibleClipWindowPx
import dev.anthonyhfm.amethyst.timeline.utils.projectTimelineSpanPx
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.theme.TimelineClipRole
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.modifier.ResizeLeft
import dev.anthonyhfm.amethyst.ui.modifier.ResizeRight
import dev.anthonyhfm.amethyst.ui.modifier.onFocusSelectAll
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Suppress("UNUSED_PARAMETER")
@Composable
fun MidiClip(
    midiEntry: MidiEntry,
    viewport: EditorViewportState,
    isSelected: Boolean,
    onSelectEntry: () -> Unit,
    onMoveEntry: (newStartMs: Long) -> Unit,
    onResizeEntry: (oldStartMs: Long, newStartMs: Long, newDurationMs: Long) -> Unit,
    gridIntervalMs: Long,
    isLightsTrack: Boolean = false,
    onDoubleClick: () -> Unit = {},
    trackIndex: Int,
    entryStartMs: Long,
    bpm: Double,
    gridType: GridUtils.GridType
) {
    val zoomLevel = viewport.zoomX
    val timelineDimensions = TimelineTheme.dimensions
    val timelinePalette = TimelineTheme.palette
    val clipColors = TimelineTheme.clipColors(
        role = if (isLightsTrack) TimelineClipRole.Lights else TimelineClipRole.Midi,
        selected = isSelected
    )

    var rangeActive by remember { mutableStateOf(false) }
    var rangeStartMs by remember { mutableStateOf<Long?>(null) }
    var rangeEndMs by remember { mutableStateOf<Long?>(null) }

    val projectedSpan = projectTimelineSpanPx(
        startTimeMs = midiEntry.startTimeMs.toDouble(),
        endTimeMs = midiEntry.endTimeMs.toDouble(),
        zoomX = zoomLevel,
    )
    val startOffsetPx = projectedSpan.startPx
    val endOffsetPx = projectedSpan.endPx

    // State that must be declared before any early return so Compose hook order is stable.
    val dragOffsetPx = remember(midiEntry.startTimeMs) { mutableStateOf(0f) }
    var snapEnabled by remember { mutableStateOf(true) }

    var resizeLeftDeltaPx by remember(midiEntry.startTimeMs) { mutableStateOf(0f) }
    var resizeRightDeltaPx by remember(midiEntry.startTimeMs) { mutableStateOf(0f) }

    // Rename support
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

    val textValue = remember { mutableStateOf(TextFieldValue(midiEntry.name)) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(renaming) {
        if (renaming) {
            textValue.value = TextFieldValue(midiEntry.name)
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }

    LaunchedEffect(isSelected) {
        if (!isSelected && renaming) {
            renamingEntryIndex.value = null
            textValue.value = TextFieldValue(midiEntry.name)
        }
    }

    val previewStartMs by remember(dragOffsetPx.value, resizeLeftDeltaPx, zoomLevel, snapEnabled) {
        derivedStateOf {
            val rawDeltaMsDouble = (dragOffsetPx.value + resizeLeftDeltaPx) / zoomLevel
            val candidateMsDouble = midiEntry.startTimeMs.toDouble() + rawDeltaMsDouble
            val nonNegativeCandidate = candidateMsDouble.coerceAtLeast(0.0)
            if (snapEnabled && gridIntervalMs > 0) {
                val gridPxSpacing = gridIntervalMs * zoomLevel
                val thresholdPx = (gridPxSpacing * 0.35f).coerceAtLeast(5f)
                GridUtils.snapToGridWithThreshold(nonNegativeCandidate.roundToLong(), zoomLevel, WorkspaceRepository.bpm.value, WorkspaceRepository.gridType.value, thresholdPx)
            } else round(nonNegativeCandidate).toLong()
        }
    }

    val previewStartOffsetPx = startOffsetPx + resizeLeftDeltaPx.roundToInt()
    val previewEndOffsetPx = endOffsetPx + resizeRightDeltaPx.roundToInt()
    val clipWindow = computeVisibleClipWindowPx(
        contentStartPx = previewStartOffsetPx,
        contentEndPx = previewEndOffsetPx,
        viewport = viewport,
        screenOffsetPx = dragOffsetPx.value.roundToInt(),
    )
    if (clipWindow == null || clipWindow.visibleWidthPx <= 0) return

    val clipShape = RoundedCornerShape(
        topStart = if (clipWindow.isLeftEdgeVisible) timelineDimensions.clipCornerRadius else 0.dp,
        topEnd = if (clipWindow.isRightEdgeVisible) timelineDimensions.clipCornerRadius else 0.dp,
        bottomStart = if (clipWindow.isLeftEdgeVisible) timelineDimensions.clipCornerRadius else 0.dp,
        bottomEnd = if (clipWindow.isRightEdgeVisible) timelineDimensions.clipCornerRadius else 0.dp,
    )
    val clipHeaderShape = RoundedCornerShape(
        topStart = if (clipWindow.isLeftEdgeVisible) timelineDimensions.clipCornerRadius else 0.dp,
        topEnd = if (clipWindow.isRightEdgeVisible) timelineDimensions.clipCornerRadius else 0.dp,
    )
    val finalWidthDp = with(LocalDensity.current) { clipWindow.visibleWidthPx.toDp() }

    ContextMenu(
        modifier = Modifier
            .offset { IntOffset(clipWindow.visibleLeftPx, 0) }
            .width(finalWidthDp)
            .height(timelineDimensions.laneHeight),
        trigger = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(clipShape)
                    .background(clipColors.background.copy(alpha = if (isSelected) 0.98f else 0.90f))
                    .border(if (isSelected) 1.5.dp else 1.dp, clipColors.border, clipShape)
            ) {
        if (!renaming) {
            Text(
                text = midiEntry.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(timelineDimensions.clipHeaderHeight)
                    .background(clipColors.header, clipHeaderShape)
                    .pointerInput(midiEntry.startTimeMs) {
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
                    .pointerInput(midiEntry.startTimeMs, zoomLevel, gridIntervalMs) {
                        detectDragGestures(
                            onDragStart = { onSelectEntry() },
                            onDragEnd = {
                                if (dragOffsetPx.value != 0f) {
                                    val newStart = previewStartMs
                                    if (newStart != midiEntry.startTimeMs) onMoveEntry(newStart)
                                }
                                dragOffsetPx.value = 0f
                                snapEnabled = true
                            },
                            onDragCancel = { dragOffsetPx.value = 0f; snapEnabled = true },
                            onDrag = { change, dragAmount ->
                                change.consume(); dragOffsetPx.value += dragAmount.x
                            }
                        )
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleClick() }) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
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
                                textValue.value = TextFieldValue(midiEntry.name)
                                return@onKeyEvent true
                            }

                            return@onKeyEvent false
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
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
                .pointerInput(midiEntry.startTimeMs, zoomLevel, bpm, gridType) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val startMs = GridUtils.snapToGrid(
                                viewport.contentXToTimeMs((clipWindow.visibleContentStartPx + offset.x).toFloat()).roundToLong().coerceAtLeast(0L),
                                zoomLevel, bpm, gridType
                            )
                            rangeStartMs = startMs
                            rangeEndMs = startMs
                            rangeActive = true
                        },
                        onDrag = { change, _ ->
                            if (rangeActive && rangeStartMs != null) {
                                val currentMs = GridUtils.snapToGrid(
                                    viewport.contentXToTimeMs((clipWindow.visibleContentStartPx + change.position.x).toFloat()).roundToLong().coerceAtLeast(0L),
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
                    // Range-Overlay wie beim AudioClip
                    if (rangeActive && rangeStartMs != null && rangeEndMs != null) {
                        val start = kotlin.math.min(rangeStartMs!!, rangeEndMs!!)
                        val end = kotlin.math.max(rangeStartMs!!, rangeEndMs!!)
                        val clipStartMs = midiEntry.startTimeMs
                        val clipEndMs = midiEntry.startTimeMs + midiEntry.durationMs
                        val visibleStart = start.coerceIn(clipStartMs, clipEndMs)
                        val visibleEnd = end.coerceIn(clipStartMs, clipEndMs)
                        if (visibleEnd > visibleStart) {
                            val startX = projectTimelineSpanPx(
                                startTimeMs = visibleStart.toDouble(),
                                endTimeMs = visibleStart.toDouble(),
                                zoomX = zoomLevel,
                            ).startPx - clipWindow.visibleContentStartPx
                            val endX = projectTimelineSpanPx(
                                startTimeMs = visibleEnd.toDouble(),
                                endTimeMs = visibleEnd.toDouble(),
                                zoomX = zoomLevel,
                            ).startPx - clipWindow.visibleContentStartPx
                            drawRect(
                                color = timelinePalette.selectionFill,
                                topLeft = Offset(startX.toFloat(), 0f),
                                size = Size((endX - startX).coerceAtLeast(1).toFloat(), size.height)
                            )
                        }
                    }
                }
        ) {
            // Left resize handle
            if (clipWindow.isLeftEdgeVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(timelineDimensions.resizeHandleWidth)
                        .fillMaxHeight()
                        .pointerHoverIcon(PointerIcon.ResizeLeft)
                        .pointerInput(midiEntry.startTimeMs) {
                            detectDragGestures(
                                onDragStart = { onSelectEntry() },
                                onDragEnd = {
                                    if (resizeLeftDeltaPx != 0f) {
                                        val rawNewStartMs = (midiEntry.startTimeMs.toDouble() + (resizeLeftDeltaPx / zoomLevel)).roundToLong().coerceAtLeast(0L)
                                        val snappedStartMs = if (gridIntervalMs > 0) {
                                            val gridPxSpacing = gridIntervalMs * zoomLevel
                                            val thresholdPx = (gridPxSpacing * 0.35f).coerceAtLeast(5f)
                                            GridUtils.snapToGridWithThreshold(rawNewStartMs, zoomLevel, WorkspaceRepository.bpm.value, WorkspaceRepository.gridType.value, thresholdPx)
                                        } else rawNewStartMs
                                        val newDurationMs = (midiEntry.endTimeMs - snappedStartMs).coerceAtLeast(50L)
                                        onResizeEntry(midiEntry.startTimeMs, snappedStartMs, newDurationMs)
                                    }
                                    resizeLeftDeltaPx = 0f
                                },
                                onDragCancel = { resizeLeftDeltaPx = 0f },
                                onDrag = { change, dragAmount -> change.consume(); resizeLeftDeltaPx += dragAmount.x }
                            )
                        }
                )
            }
            // Right resize handle
            if (clipWindow.isRightEdgeVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(timelineDimensions.resizeHandleWidth)
                        .fillMaxHeight()
                        .pointerHoverIcon(PointerIcon.ResizeRight)
                        .pointerInput(midiEntry.startTimeMs) {
                            detectDragGestures(
                                onDragStart = { onSelectEntry() },
                                onDragEnd = {
                                    if (resizeRightDeltaPx != 0f) {
                                        val rawNewEndMs = (midiEntry.endTimeMs.toDouble() + (resizeRightDeltaPx / zoomLevel)).roundToLong().coerceAtLeast(midiEntry.startTimeMs + 50L)
                                        val snappedEndMs = if (gridIntervalMs > 0) {
                                            val gridPxSpacing = gridIntervalMs * zoomLevel
                                            val thresholdPx = (gridPxSpacing * 0.35f).coerceAtLeast(5f)
                                            GridUtils.snapToGridWithThreshold(rawNewEndMs, zoomLevel, WorkspaceRepository.bpm.value, WorkspaceRepository.gridType.value, thresholdPx)
                                        } else rawNewEndMs
                                        val newDurationMs = (snappedEndMs - midiEntry.startTimeMs).coerceAtLeast(50L)
                                        onResizeEntry(midiEntry.startTimeMs, midiEntry.startTimeMs, newDurationMs)
                                    }
                                    resizeRightDeltaPx = 0f
                                },
                                onDragCancel = { resizeRightDeltaPx = 0f },
                                onDrag = { change, dragAmount -> change.consume(); resizeRightDeltaPx += dragAmount.x }
                            )
                        }
                )
            }
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
