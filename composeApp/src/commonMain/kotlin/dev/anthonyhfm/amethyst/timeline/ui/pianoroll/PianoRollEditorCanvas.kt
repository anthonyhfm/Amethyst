package dev.anthonyhfm.amethyst.timeline.ui.pianoroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.timeline.*
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import dev.anthonyhfm.amethyst.timeline.contract.TimelineEditorTool
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.timeline.viewport.wheelZoomScaleFactor
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.h3
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Height of the "Pad N" header row rendered above each launchpad's keys/notes section. */
private val PIANO_ROLL_DEVICE_HEADER_HEIGHT = 24.dp

@Composable
fun PianoRollEditorCanvas(
    entry: MidiEntry,
    launchpads: List<LaunchpadViewportElement>,
    trackIndex: Int,
    entryStartMs: Long,
    multiSelectModifierDown: Boolean,
    shiftModifierDown: Boolean,
    selectedColor: Color,
    gradientMode: Boolean,
    workingGradient: List<NoteGradientStop>?,
    activeTool: TimelineEditorTool,
    onCreateNotes: (List<MidiNote>) -> TimelineCommandResult,
    onMoveNotes: (List<TimelineEditedNote>) -> TimelineCommandResult,
    onResizeNotes: (List<TimelineEditedNote>) -> TimelineCommandResult,
    onDeleteNotes: (List<MidiNote>) -> TimelineCommandResult,
    viewport: EditorViewportState,
    onViewportChange: (EditorViewportState) -> Unit,
    gridResolution: GridResolution,
    currentBpm: () -> Double,
    pressedKeysState: StateFlow<Map<Pair<Int, Int>, Boolean>>,
    selectedTimeMs: Long?,
    playheadPositionMs: Long?,
    onSelectedTimeMsChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestViewport by rememberUpdatedState(viewport)
    val latestOnViewportChange by rememberUpdatedState(onViewportChange)
    val pianoRollVerticalScrollState = rememberScrollState()
    val scrollCoroutineScope = rememberCoroutineScope()
    val latestScrollOffsetPx by rememberUpdatedState(pianoRollVerticalScrollState.value.toFloat())

    val density = LocalDensity.current
    val headerOffsetPx = with(density) { PIANO_ROLL_DEVICE_HEADER_HEIGHT.toPx() }
    val latestHeaderOffsetPx by rememberUpdatedState(headerOffsetPx)
    val timelinePalette = TimelineTheme.palette
    val gridColors = PianoRollGridColors(
        canvasColor = timelinePalette.canvas,
        rowColor = timelinePalette.laneSurface,
        pitchSeparatorColor = timelinePalette.gridMinor,
        quarterCellColor = timelinePalette.gridMinor,
        beatLineColor = timelinePalette.tickMinor,
        barLineColor = timelinePalette.tickMajor,
    )

    val launchpadCount = launchpads.size.coerceAtLeast(1)
    val totalPitches = 100

    val noteHeightDp: Dp = 22.dp
    val beatsPerBar = 4
    val clipBeats = entry.durationMs.toFloat() / MS_PER_BEAT.toFloat()

    val oobOverhangMs = 0L
    val oobOverhangRightMs = (entry.durationMs * 0.25).toLong().coerceAtLeast(2000L)
    val totalBeatsWithOverhang = (entry.durationMs + oobOverhangRightMs).toFloat() / MS_PER_BEAT.toFloat()

    val metrics = remember(totalPitches, density, gridResolution, viewport.zoomX, oobOverhangMs) {
        PianoRollMetrics(totalPitches, noteHeightDp, viewport.zoomX, density, gridResolution, oobOffsetMs = oobOverhangMs)
    }
    val latestMetrics by rememberUpdatedState(metrics)
    val latestOobOverhangMs by rememberUpdatedState(oobOverhangMs)
    val latestTotalBeatsWithOverhang by rememberUpdatedState(totalBeatsWithOverhang)

    val canvasHeightDp = noteHeightDp * totalPitches

    LaunchedEffect(Unit) {
        val initialScrollX = metrics.timeMsToXPx(0L).coerceAtLeast(0f)
        val contentWidthPx = viewport.zoomX * MS_PER_BEAT.toFloat() * totalBeatsWithOverhang
        latestOnViewportChange(
            viewport.copy(scrollX = initialScrollX, contentWidth = contentWidthPx)
        )
    }

    var notesState by remember { mutableStateOf(entry.notes) }
    LaunchedEffect(entry) { notesState = entry.notes }

    val pressedKeys by pressedKeysState.collectAsState()

    val pressedKeysPerDevice = remember(pressedKeys) {
        pressedKeys.entries
            .filter { it.value }
            .groupBy({ it.key.first }, { it.key.second })
            .mapValues { it.value.toSet() }
    }

    val selections by SelectionManager.selections.collectAsState()

    var marqueeStart by remember { mutableStateOf<Offset?>(null) }
    var marqueeCurrent by remember { mutableStateOf<Offset?>(null) }
    var marqueeGestureActive by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var resizeLeftDelta by remember { mutableStateOf(0f) }
    var resizeRightDelta by remember { mutableStateOf(0f) }
    var activeDragNote by remember { mutableStateOf<MidiNote?>(null) }
    var draftNote by remember { mutableStateOf<MidiNote?>(null) }
    var viewportWidthPx by remember { mutableStateOf(0) }
    var lastPointerX by remember { mutableStateOf<Float?>(null) }

    fun snapSelectedTimeMs(timeMs: Double, currentResolution: GridResolution): Long {
        return snapClipTimeToGrid(timeMs, currentResolution)
    }

    fun buildDraftNote(
        device: Int,
        pitch: Int,
        color: Color,
        gradient: List<NoteGradientStop>?,
        anchorCellStartMs: Long,
        currentCellStartMs: Long,
        cellDurationMs: Long,
    ): MidiNote {
        val span = resolveDraftSpan(
            anchorCellStartMs = anchorCellStartMs,
            currentCellStartMs = currentCellStartMs,
            cellDurationMs = cellDurationMs,
        )
        return MidiNote.withPaint(
            device = device,
            pitch = pitch,
            color = color,
            startTimeMs = span.startTimeMs,
            durationMs = span.durationMs,
            gradient = gradient,
        )
    }

    fun handleNoteTap(offset: Offset) {
        val contentX = latestViewport.screenToContentX(offset.x)
        val clipTimeMs = latestViewport.contentXToClipTimeMs(contentX, latestOobOverhangMs)
        val contentOffset = offset.copy(y = offset.y + latestScrollOffsetPx - latestHeaderOffsetPx)
        val pitch = latestMetrics.yPxToPitch(contentOffset.y)

        when (activeTool) {
            TimelineEditorTool.SELECT -> {
                val clickedRect = buildNoteRectsScreenSpace(notesState, latestMetrics, latestViewport)
                    .firstOrNull { it.contains(contentOffset) }

                if (clickedRect != null) {
                    val note = clickedRect.note
                    val targetSelectable = Selectable.PianoRollNote(trackIndex, entryStartMs, note)
                    val isSelected = selections.any {
                        it is Selectable.PianoRollNote &&
                            it.entryStartMs == entryStartMs &&
                            it.trackIndex == trackIndex &&
                            it.note == note
                    }
                    if (multiSelectModifierDown || shiftModifierDown) {
                        if (isSelected) {
                            SelectionManager.replaceSelections(SelectionManager.selections.value - targetSelectable)
                        } else {
                            SelectionManager.select(targetSelectable, single = false)
                        }
                    } else {
                        SelectionManager.select(targetSelectable, single = true)
                    }
                } else {
                    if (!multiSelectModifierDown && !shiftModifierDown) {
                        SelectionManager.clear()
                    }
                    val snappedTimeMs = snapSelectedTimeMs(clipTimeMs, gridResolution)
                    onSelectedTimeMsChange(snappedTimeMs.coerceAtLeast(0L).coerceAtMost(entry.durationMs))
                }
            }

            TimelineEditorTool.DRAW -> {
                val clickedNote = buildNoteRectsScreenSpace(notesState, latestMetrics, latestViewport)
                    .firstOrNull { it.contains(contentOffset) }?.note

                if (clickedNote != null) {
                    onDeleteNotes(listOf(clickedNote))
                    notesState = notesState.filter { it != clickedNote }
                } else {
                    val startTimeMs = floorClipTimeToGrid(clipTimeMs, gridResolution)
                    val durationMs = currentCellDurationMs(gridResolution)

                    val newNote = MidiNote.withPaint(
                        device = 0,
                        pitch = pitch,
                        color = selectedColor,
                        startTimeMs = startTimeMs,
                        durationMs = durationMs,
                        gradient = if (gradientMode) workingGradient else null
                    )
                    val result = onCreateNotes(listOf(newNote))
                    if (result.didChange) {
                        notesState = notesState + newNote
                        SelectionManager.select(
                            Selectable.PianoRollNote(trackIndex, entryStartMs, newNote),
                            single = !multiSelectModifierDown && !shiftModifierDown
                        )
                    }
                }
            }

            TimelineEditorTool.ERASE -> {
                val noteToErase = buildNoteRectsScreenSpace(notesState, latestMetrics, latestViewport)
                    .firstOrNull { it.contains(contentOffset) }?.note

                if (noteToErase != null) {
                    val result = onDeleteNotes(listOf(noteToErase))
                    if (result.didChange) {
                        notesState = notesState.filter { it != noteToErase }
                        SelectionManager.replaceSelections(
                            SelectionManager.selections.value - Selectable.PianoRollNote(trackIndex, entryStartMs, noteToErase)
                        )
                    }
                }
            }
        }
    }

    fun handleNoteDragStart(offset: Offset) {
        val contentX = latestViewport.screenToContentX(offset.x)
        val clipTimeMs = latestViewport.contentXToClipTimeMs(contentX, latestOobOverhangMs)
        val contentOffset = offset.copy(y = offset.y + latestScrollOffsetPx - latestHeaderOffsetPx)
        val pitch = latestMetrics.yPxToPitch(contentOffset.y)

        when (activeTool) {
            TimelineEditorTool.SELECT -> {
                val noteRects = buildNoteRectsScreenSpace(notesState, latestMetrics, latestViewport)
                val hitTarget = findPianoRollHitTarget(contentOffset, noteRects)

                when (hitTarget) {
                    is PianoRollHitTarget.NoteBody -> {
                        val note = hitTarget.note
                        activeDragNote = note
                        val isSelected = selections.any {
                            it is Selectable.PianoRollNote &&
                                it.entryStartMs == entryStartMs &&
                                it.trackIndex == trackIndex &&
                                it.note == note
                        }
                        if (!isSelected) {
                            SelectionManager.select(
                                Selectable.PianoRollNote(trackIndex, entryStartMs, note),
                                single = !multiSelectModifierDown && !shiftModifierDown
                            )
                        }
                    }

                    is PianoRollHitTarget.ResizeLeft -> {
                        activeDragNote = hitTarget.note
                    }

                    is PianoRollHitTarget.ResizeRight -> {
                        activeDragNote = hitTarget.note
                    }

                    is PianoRollHitTarget.Empty -> {
                        marqueeStart = offset
                        marqueeCurrent = offset
                        marqueeGestureActive = true
                        if (!multiSelectModifierDown && !shiftModifierDown) {
                            SelectionManager.clear()
                        }
                    }
                }
            }

            TimelineEditorTool.DRAW -> {
                val anchorStartMs = floorClipTimeToGrid(clipTimeMs, gridResolution)
                val cellDurMs = currentCellDurationMs(gridResolution)
                draftNote = buildDraftNote(
                    device = 0,
                    pitch = pitch,
                    color = selectedColor,
                    gradient = if (gradientMode) workingGradient else null,
                    anchorCellStartMs = anchorStartMs,
                    currentCellStartMs = anchorStartMs,
                    cellDurationMs = cellDurMs,
                )
            }

            TimelineEditorTool.ERASE -> {
                val noteToErase = buildNoteRectsScreenSpace(notesState, latestMetrics, latestViewport)
                    .firstOrNull { it.contains(contentOffset) }?.note

                if (noteToErase != null) {
                    val result = onDeleteNotes(listOf(noteToErase))
                    if (result.didChange) {
                        notesState = notesState.filter { it != noteToErase }
                    }
                }
            }
        }
    }

    fun handleNoteDrag(change: PointerInputChange, dragAmount: Offset) {
        change.consume()
        val currentPos = change.position
        val contentX = latestViewport.screenToContentX(currentPos.x)
        val clipTimeMs = latestViewport.contentXToClipTimeMs(contentX, latestOobOverhangMs)

        if (marqueeGestureActive) {
            marqueeCurrent = currentPos
            val start = marqueeStart ?: return
            val left = min(start.x, currentPos.x)
            val right = max(start.x, currentPos.x)
            val top = min(start.y, currentPos.y) + latestScrollOffsetPx - latestHeaderOffsetPx
            val bottom = max(start.y, currentPos.y) + latestScrollOffsetPx - latestHeaderOffsetPx

            val newlySelected = buildNoteRectsScreenSpace(notesState, latestMetrics, latestViewport)
                .filter { rect ->
                    rect.left <= right && rect.right >= left &&
                        rect.top <= bottom && rect.bottom >= top
                }
                .map { it.note }

            if (!multiSelectModifierDown && !shiftModifierDown) {
                SelectionManager.clear()
            }

            newlySelected.forEach { note ->
                SelectionManager.select(
                    Selectable.PianoRollNote(trackIndex, entryStartMs, note),
                    single = false
                )
            }
        } else if (draftNote != null) {
            val pitch = latestMetrics.yPxToPitch(currentPos.y + latestScrollOffsetPx - latestHeaderOffsetPx)
            val currentCellStartMs = floorClipTimeToGrid(clipTimeMs, gridResolution)
            val cellDurMs = currentCellDurationMs(gridResolution)
            draftNote = buildDraftNote(
                device = draftNote!!.device,
                pitch = pitch,
                color = selectedColor,
                gradient = if (gradientMode) workingGradient else null,
                anchorCellStartMs = draftNote!!.startTimeMs,
                currentCellStartMs = currentCellStartMs,
                cellDurationMs = cellDurMs,
            )
        } else if (activeTool == TimelineEditorTool.ERASE) {
            val contentPos = currentPos.copy(y = currentPos.y + latestScrollOffsetPx - latestHeaderOffsetPx)
            val noteToErase = buildNoteRectsScreenSpace(notesState, latestMetrics, latestViewport)
                .firstOrNull { it.contains(contentPos) }?.note

            if (noteToErase != null) {
                val result = onDeleteNotes(listOf(noteToErase))
                if (result.didChange) {
                    notesState = notesState.filter { it != noteToErase }
                }
            }
        }
    }

    fun handleNoteDragEnd() {
        if (marqueeGestureActive) {
            marqueeStart = null
            marqueeCurrent = null
            marqueeGestureActive = false
        }

        draftNote?.let { createdDraft ->
            val result = onCreateNotes(listOf(createdDraft))
            if (result.didChange) {
                notesState = notesState + createdDraft
                SelectionManager.select(
                    Selectable.PianoRollNote(trackIndex, entryStartMs, createdDraft),
                    single = !multiSelectModifierDown && !shiftModifierDown
                )
            }
            draftNote = null
        }
    }

    fun handleNoteDragCancel() {
        marqueeStart = null
        marqueeCurrent = null
        marqueeGestureActive = false
        draftNote = null
        activeDragNote = null
        dragOffset = Offset.Zero
        resizeLeftDelta = 0f
        resizeRightDelta = 0f
    }

    Column(modifier = modifier.fillMaxSize().background(Theme[colors][background])) {
        PianoRollHeader(
            clipBeats = clipBeats,
            metrics = metrics,
            beatsPerBar = beatsPerBar,
            viewport = viewport,
            onTap = { offset ->
                val contentX = viewport.screenToContentX(offset.x)
                val timeMs = snapSelectedTimeMs(
                    viewport.contentXToClipTimeMs(contentX, oobOverhangMs),
                    gridResolution
                )
                onSelectedTimeMsChange(timeMs.coerceAtLeast(0L).coerceAtMost(entry.durationMs))
            }
        )

        if (Heaven.devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .background(Theme[colors][input], shape = SmallShape)
                        .border(1.dp, Theme[colors][border], SmallShape)
                        .padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Theme[colors][background], shape = SmallShape)
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.Music,
                            contentDescription = null,
                            tint = Theme[colors][primary],
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "No Launchpads Connected",
                        style = Theme[typography][h3],
                    )

                    Text(
                        text = "Please connect at least one Launchpad device to view and edit notes in the Piano Roll.",
                        style = Theme[typography][p].copy(color = Theme[colors][mutedForeground]),
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .verticalScroll(pianoRollVerticalScrollState)
                ) {
                    launchpads.forEachIndexed { index, device ->
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(PIANO_ROLL_DEVICE_HEADER_HEIGHT)
                                    .background(Theme[colors][border]),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Device #${index + 1}",
                                    style = Theme[typography][p].copy(color = Theme[colors][foreground])
                                )
                            }
                            PianoKeysColumn(
                                totalPitches = totalPitches,
                                noteHeight = noteHeightDp,
                                deviceIndex = index,
                                pressedPitches = pressedKeysPerDevice[index].orEmpty()
                            )
                        }
                    }
                }

                val rowHeight = noteHeightDp * totalPitches

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onSizeChanged { size ->
                            viewportWidthPx = size.width
                            val totalWidthPx = viewport.zoomX * MS_PER_BEAT.toFloat() * latestTotalBeatsWithOverhang
                            val updatedViewport = viewport.withConstrainedViewport(
                                viewportWidth = size.width.toFloat(),
                                contentWidth = totalWidthPx
                            )
                            if (updatedViewport != viewport) {
                                latestOnViewportChange(updatedViewport)
                            }
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                lastPointerX = down.position.x

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    lastPointerX = change.position.x

                                    val isCtrlOrMeta = event.type == PointerEventType.Move &&
                                        (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed)

                                    if (event.type == PointerEventType.Scroll && (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed)) {
                                        val scrollDelta = change.scrollDelta.y
                                        if (scrollDelta != 0f) {
                                            val currentVP = latestViewport
                                            val anchorPx = resolveViewportRelativeCursorX(
                                                lastPointerX,
                                                change.position.x
                                            )
                                            val factor = wheelZoomScaleFactor(scrollDelta)
                                            val targetZoomX = (currentVP.zoomX * factor).coerceIn(currentVP.minZoomX, currentVP.maxZoomX)
                                            val actualScale = targetZoomX / currentVP.zoomX

                                            val newScrollX = anchorPx + actualScale * (currentVP.scrollX - anchorPx)
                                            val totalContentWidthPx = targetZoomX * MS_PER_BEAT.toFloat() * latestTotalBeatsWithOverhang

                                            val newVP = currentVP.withConstrainedViewport(
                                                zoomX = targetZoomX,
                                                scrollX = newScrollX,
                                                viewportWidth = currentVP.viewportWidth,
                                                contentWidth = totalContentWidthPx
                                            )
                                            latestOnViewportChange(newVP)
                                            change.consume()
                                        }
                                    } else if (event.type == PointerEventType.Scroll) {
                                        val scrollDeltaX = change.scrollDelta.x
                                        val scrollDeltaY = change.scrollDelta.y
                                        if (scrollDeltaX != 0f) {
                                            val currentVP = latestViewport
                                            val deltaPx = scrollDeltaX * 20f
                                            val newVP = currentVP.withConstrainedViewport(
                                                scrollX = currentVP.scrollX + deltaPx
                                            )
                                            latestOnViewportChange(newVP)
                                            change.consume()
                                        } else if (scrollDeltaY != 0f) {
                                            val deltaPx = scrollDeltaY * 20f
                                            scrollCoroutineScope.launch {
                                                pianoRollVerticalScrollState.scrollBy(deltaPx)
                                            }
                                            change.consume()
                                        }
                                    }

                                    if (!change.pressed) break
                                }
                            }
                        }
                        .pointerInput(activeTool, notesState) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var overSlop = Offset.Zero
                                var slopChange: PointerInputChange?
                                do {
                                    slopChange = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                        change.consume()
                                        overSlop = over
                                    }
                                } while (slopChange != null && !slopChange.isConsumed)

                                val startedDrag = slopChange
                                if (startedDrag != null) {
                                    handleNoteDragStart(startedDrag.position)
                                    handleNoteDrag(startedDrag, overSlop)
                                    val dragEndedNormally = drag(startedDrag.id) { change ->
                                        handleNoteDrag(change, change.positionChange())
                                    }
                                    if (dragEndedNormally) {
                                        handleNoteDragEnd()
                                    } else {
                                        handleNoteDragCancel()
                                    }
                                } else {
                                    handleNoteTap(down.position)
                                }
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(pianoRollVerticalScrollState)
                    ) {
                        launchpads.forEachIndexed { index, _ ->
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(PIANO_ROLL_DEVICE_HEADER_HEIGHT)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight)
                                        .pianoRollGridBackground(
                                            devicePitchRange = 0 until totalPitches,
                                            clipBeats = clipBeats,
                                            metrics = metrics,
                                            beatsPerBar = beatsPerBar,
                                            gridResolution = gridResolution,
                                            colors = gridColors,
                                            viewport = viewport
                                        )
                                ) {
                                notesState.forEach { note ->
                                    val selected = selections.any {
                                        it is Selectable.PianoRollNote &&
                                            it.entryStartMs == entryStartMs &&
                                            it.trackIndex == trackIndex &&
                                            it.note == note
                                    }

                                    key(note.startTimeMs, note.pitch) {
                                        NoteBox(
                                            note = note,
                                            metrics = metrics,
                                            viewport = viewport,
                                            isSelected = selected,
                                            activeTool = activeTool,
                                            clipDurationMs = entry.durationMs,
                                            onSelect = {
                                                val targetSelectable = Selectable.PianoRollNote(trackIndex, entryStartMs, note)
                                                if (multiSelectModifierDown || shiftModifierDown) {
                                                    if (selected) {
                                                        SelectionManager.replaceSelections(SelectionManager.selections.value - targetSelectable)
                                                    } else {
                                                        SelectionManager.select(targetSelectable, single = false)
                                                    }
                                                } else {
                                                    if (!selected) {
                                                        SelectionManager.select(targetSelectable, single = true)
                                                    }
                                                }
                                            },
                                            onDrag = { dragAmount ->
                                                dragOffset += dragAmount
                                            },
                                            onDragEnd = {
                                                val selectedNotes = selections.filterIsInstance<Selectable.PianoRollNote>()
                                                    .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                                                    .map { it.note }
                                                    .ifEmpty { activeDragNote?.let { listOf(it) } ?: emptyList() }

                                                if (selectedNotes.isEmpty() || dragOffset == Offset.Zero) {
                                                    dragOffset = Offset.Zero
                                                    activeDragNote = null
                                                    return@NoteBox
                                                }

                                                val pitchDelta = (- (dragOffset.y / metrics.noteHeightPx)).roundToInt()

                                                val noteUpdates = selectedNotes.map { noteToDrag ->
                                                    val startContentX = viewport.clipTimeMsToContentX(
                                                        noteToDrag.startTimeMs.toDouble(),
                                                        oobOverhangMs
                                                    )
                                                    val newStartContentX = startContentX + dragOffset.x
                                                    val newStartMs = snapClipTimeToGrid(
                                                        viewport.contentXToClipTimeMs(newStartContentX, oobOverhangMs),
                                                        gridResolution,
                                                    )
                                                    val newPitch = (noteToDrag.pitch + pitchDelta).coerceIn(0, totalPitches - 1)
                                                    val updatedNote = noteToDrag.copy(
                                                        startTimeMs = newStartMs,
                                                        pitch = newPitch
                                                    )
                                                    noteToDrag to updatedNote
                                                }

                                                val result = onMoveNotes(
                                                    noteUpdates.map { TimelineEditedNote(before = it.first, after = it.second) }
                                                )

                                                if (result.didChange) {
                                                    val updatedNotes = notesState.map { existingNote ->
                                                        noteUpdates.find { it.first == existingNote }?.second ?: existingNote
                                                    }
                                                    notesState = updatedNotes
                                                    SelectionManager.clear()
                                                    noteUpdates.forEach { (_, new) ->
                                                        SelectionManager.select(
                                                            Selectable.PianoRollNote(trackIndex, entryStartMs, new),
                                                            single = false
                                                        )
                                                    }
                                                }
                                                dragOffset = Offset.Zero
                                                activeDragNote = null
                                            },
                                            onResizeLeft = { resizeDelta ->
                                                resizeLeftDelta += resizeDelta
                                            },
                                            onResizeLeftEnd = {
                                                val selectedNotes = selections.filterIsInstance<Selectable.PianoRollNote>()
                                                    .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                                                    .map { it.note }
                                                    .ifEmpty { activeDragNote?.let { listOf(it) } ?: emptyList() }

                                                if (selectedNotes.isEmpty()) {
                                                    resizeLeftDelta = 0f
                                                    activeDragNote = null
                                                    return@NoteBox
                                                }

                                                val noteUpdates = selectedNotes.mapNotNull { noteToResize ->
                                                    val startContentX = viewport.clipTimeMsToContentX(
                                                        noteToResize.startTimeMs.toDouble(),
                                                        oobOverhangMs
                                                    )
                                                    val newStartContentX = startContentX + resizeLeftDelta
                                                    val newStartMs = snapClipTimeToGrid(
                                                        viewport.contentXToClipTimeMs(newStartContentX, oobOverhangMs),
                                                        gridResolution,
                                                    )
                                                    val newEndMs = noteToResize.endTimeMs
                                                    val minDur = MS_PER_BEAT / 4
                                                    val newDurationMs = (newEndMs - newStartMs).coerceAtLeast(minDur)

                                                    if (newDurationMs < minDur) return@mapNotNull null

                                                    val updatedNote = noteToResize.copy(
                                                        startTimeMs = newStartMs,
                                                        durationMs = newDurationMs
                                                    )
                                                    noteToResize to updatedNote
                                                }

                                                val result = onResizeNotes(
                                                    noteUpdates.map { TimelineEditedNote(before = it.first, after = it.second) }
                                                )

                                                if (result.didChange) {
                                                    val updatedNotes = notesState.map { existingNote ->
                                                        noteUpdates.find { it.first == existingNote }?.second ?: existingNote
                                                    }
                                                    notesState = updatedNotes
                                                    SelectionManager.clear()
                                                    noteUpdates.forEach { (_, new) ->
                                                        SelectionManager.select(
                                                            Selectable.PianoRollNote(trackIndex, entryStartMs, new),
                                                            single = false
                                                        )
                                                    }
                                                }
                                                resizeLeftDelta = 0f
                                                activeDragNote = null
                                            },
                                            onResizeRight = { resizeDelta ->
                                                resizeRightDelta += resizeDelta
                                            },
                                            onResizeRightEnd = {
                                                val selectedNotes = selections.filterIsInstance<Selectable.PianoRollNote>()
                                                    .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                                                    .map { it.note }
                                                    .ifEmpty { activeDragNote?.let { listOf(it) } ?: emptyList() }

                                                if (selectedNotes.isEmpty()) {
                                                    resizeRightDelta = 0f
                                                    activeDragNote = null
                                                    return@NoteBox
                                                }

                                                val noteUpdates = selectedNotes.mapNotNull { noteToResize ->
                                                    val endContentX = viewport.clipTimeMsToContentX(
                                                        (noteToResize.startTimeMs + noteToResize.durationMs).toDouble(),
                                                        oobOverhangMs
                                                    )
                                                    val newEndContentX = endContentX + resizeRightDelta
                                                    val newEndTimeMs = snapClipTimeToGrid(
                                                        viewport.contentXToClipTimeMs(newEndContentX, oobOverhangMs),
                                                        gridResolution,
                                                    )
                                                    val minDur = MS_PER_BEAT / 4
                                                    val newDurationMs = (newEndTimeMs - noteToResize.startTimeMs).coerceAtLeast(minDur)

                                                    if (newDurationMs < minDur) return@mapNotNull null

                                                    val updatedNote = noteToResize.copy(durationMs = newDurationMs)
                                                    noteToResize to updatedNote
                                                }

                                                val result = onResizeNotes(
                                                    noteUpdates.map { TimelineEditedNote(before = it.first, after = it.second) }
                                                )

                                                if (result.didChange) {
                                                    val updatedNotes = notesState.map { existingNote ->
                                                        noteUpdates.find { it.first == existingNote }?.second ?: existingNote
                                                    }
                                                    notesState = updatedNotes
                                                    SelectionManager.clear()
                                                    noteUpdates.forEach { (_, new) ->
                                                        SelectionManager.select(
                                                            Selectable.PianoRollNote(trackIndex, entryStartMs, new),
                                                            single = false
                                                        )
                                                    }
                                                }
                                                resizeRightDelta = 0f
                                                activeDragNote = null
                                            },
                                            dragOffset = if (selected) dragOffset else Offset.Zero,
                                            resizeLeftDelta = if (selected && activeDragNote != null) resizeLeftDelta else 0f,
                                            resizeRightDelta = if (selected && activeDragNote != null) resizeRightDelta else 0f
                                        )
                                    }
                                }

                                draftNote?.let { draft ->
                                    DraftNoteBox(
                                        note = draft,
                                        metrics = metrics,
                                        viewport = viewport
                                    )
                                }

                                PianoRollSelectedTimeCursor(
                                    selectedTimeMs = selectedTimeMs,
                                    viewport = viewport,
                                    oobOverhangMs = oobOverhangMs,
                                    rowHeight = rowHeight
                                )
                            }
                            }
                        }
                    }

                    PianoRollMarqueeOverlay(
                        marqueeStart = marqueeStart,
                        marqueeCurrent = marqueeCurrent
                    )
                }
            }
        }
    }
}

private fun currentCellDurationMs(currentResolution: GridResolution): Long =
    (MS_PER_BEAT / currentResolution.snapDivisionsPerBeat).coerceAtLeast(1L)
