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
import com.composeunstyled.Icon
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
import dev.anthonyhfm.amethyst.timeline.data.resolvedDeviceIndex
import dev.anthonyhfm.amethyst.timeline.data.resolvedPadIndex
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
    bpm: Double,
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
    val beatDurationMs = millisecondsPerBeat(bpm)

    val noteHeightDp: Dp = 22.dp
    val beatsPerBar = 4
    val clipBeats = entry.durationMs.toFloat() / beatDurationMs.toFloat()

    val oobOverhangMs = 0L
    val oobOverhangRightMs = (entry.durationMs * 0.25).toLong().coerceAtLeast(2000L)
    val totalBeatsWithOverhang = (entry.durationMs + oobOverhangRightMs).toFloat() / beatDurationMs.toFloat()

    val metrics = remember(totalPitches, density, gridResolution, viewport.zoomX, oobOverhangMs, beatDurationMs) {
        PianoRollMetrics(
            totalPitches,
            noteHeightDp,
            viewport.zoomX,
            density,
            gridResolution,
            beatDurationMs = beatDurationMs,
            oobOffsetMs = oobOverhangMs,
        )
    }
    val latestMetrics by rememberUpdatedState(metrics)
    val latestOobOverhangMs by rememberUpdatedState(oobOverhangMs)
    val latestTotalBeatsWithOverhang by rememberUpdatedState(totalBeatsWithOverhang)

    val canvasHeightDp = noteHeightDp * totalPitches

    LaunchedEffect(Unit) {
        val initialScrollX = metrics.timeMsToXPx(0L).coerceAtLeast(0f)
        val contentWidthPx = viewport.zoomX * beatDurationMs.toFloat() * totalBeatsWithOverhang
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
    var draftAnchorCellStartMs by remember { mutableStateOf<Long?>(null) }
    var viewportWidthPx by remember { mutableStateOf(0) }
    var lastPointerX by remember { mutableStateOf<Float?>(null) }
    var lastTapUptimeMillis by remember { mutableStateOf<Long?>(null) }
    var lastTapPosition by remember { mutableStateOf<Offset?>(null) }

    val visibleNotes = remember(notesState, viewport.scrollX, viewport.zoomX, viewportWidthPx, beatDurationMs) {
        notesState.filter { note ->
            val left = viewport.contentToScreenX(metrics.timeMsToXPx(note.startTimeMs))
            val right = left + metrics.durationMsToWidthPx(note.durationMs)
            right >= 0f && left <= viewportWidthPx.toFloat()
        }
    }

    fun snapSelectedTimeMs(timeMs: Double, currentResolution: GridResolution): Long {
        return snapClipTimeToGrid(timeMs, currentResolution, beatDurationMs)
    }

    fun resolveGridPoint(offset: Offset): PianoRollGridPoint? = resolvePianoRollGridPoint(
        point = offset,
        verticalScrollPx = latestScrollOffsetPx,
        deviceHeaderHeightPx = latestHeaderOffsetPx,
        deviceRowHeightPx = latestMetrics.canvasHeightPx,
        deviceCount = launchpadCount,
    )

    fun noteRectsForDevice(deviceIndex: Int): List<PianoRollNoteRect> = buildNoteRectsScreenSpace(
        notes = notesState.filter { it.resolvedDeviceIndex == deviceIndex },
        metrics = latestMetrics,
        viewport = latestViewport,
    )

    fun noteRectsInViewport(): List<PianoRollNoteRect> {
        val blockHeightPx = latestHeaderOffsetPx + latestMetrics.canvasHeightPx
        return notesState
            .filter { it.resolvedDeviceIndex in 0 until launchpadCount }
            .map { note ->
                PianoRollNoteRect(
                    note = note,
                    left = latestViewport.contentToScreenX(latestMetrics.timeMsToXPx(note.startTimeMs)),
                    top = note.resolvedDeviceIndex * blockHeightPx + latestHeaderOffsetPx +
                        latestMetrics.pitchToYPx(note.resolvedPadIndex) - latestScrollOffsetPx,
                    width = latestMetrics.durationMsToWidthPx(note.durationMs),
                    height = latestMetrics.noteRenderHeightPx,
                )
            }
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
        val gridPoint = resolveGridPoint(offset) ?: return
        val contentX = latestViewport.screenToContentX(offset.x)
        val clipTimeMs = latestViewport.contentXToClipTimeMs(contentX, latestOobOverhangMs)
        val contentOffset = gridPoint.pointInDevice
        val pitch = latestMetrics.yPxToPitch(contentOffset.y)

        when (activeTool) {
            TimelineEditorTool.NORMAL -> {
                val clickedRect = noteRectsForDevice(gridPoint.deviceIndex)
                    .firstOrNull { it.contains(contentOffset) }

                if (clickedRect != null) {
                    val note = clickedRect.note
                    val targetSelectable = Selectable.PianoRollNote(trackIndex, entryStartMs, note)
                    val isSelected = selections.any {
                        it is Selectable.PianoRollNote &&
                            it.entryStartMs == entryStartMs &&
                            it.trackIndex == trackIndex &&
                            it.note.noteId == note.noteId
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
                val clickedNote = noteRectsForDevice(gridPoint.deviceIndex)
                    .firstOrNull { it.contains(contentOffset) }?.note

                if (clickedNote != null) {
                    onDeleteNotes(listOf(clickedNote))
                    notesState = notesState.filterNot { it.noteId == clickedNote.noteId }
                } else {
                    val startTimeMs = floorClipTimeToGrid(clipTimeMs, gridResolution, beatDurationMs)
                    val durationMs = currentCellDurationMs(gridResolution, beatDurationMs)

                    val newNote = MidiNote.withPaint(
                        device = gridPoint.deviceIndex,
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

        }
    }

    fun handleNoteDoubleTap(offset: Offset) {
        if (activeTool != TimelineEditorTool.NORMAL) return
        val gridPoint = resolveGridPoint(offset) ?: return
        val contentOffset = gridPoint.pointInDevice
        val clickedNote = noteRectsForDevice(gridPoint.deviceIndex)
            .firstOrNull { it.contains(contentOffset) }
            ?.note

        if (clickedNote != null) {
            val result = onDeleteNotes(listOf(clickedNote))
            if (result.didChange) {
                notesState = notesState.filterNot { it.noteId == clickedNote.noteId }
                SelectionManager.replaceSelections(
                    SelectionManager.selections.value.filterNot {
                        it is Selectable.PianoRollNote && it.note.noteId == clickedNote.noteId
                    }
                )
            }
            return
        }

        val contentX = latestViewport.screenToContentX(offset.x)
        val clipTimeMs = latestViewport.contentXToClipTimeMs(contentX, latestOobOverhangMs)
        val pitch = latestMetrics.yPxToPitch(contentOffset.y)
        val newNote = MidiNote.withPaint(
            device = gridPoint.deviceIndex,
            pitch = pitch,
            color = selectedColor,
            startTimeMs = floorClipTimeToGrid(clipTimeMs, gridResolution, beatDurationMs),
            durationMs = currentCellDurationMs(gridResolution, beatDurationMs),
            gradient = if (gradientMode) workingGradient else null,
        )
        val result = onCreateNotes(listOf(newNote))
        if (result.didChange) {
            notesState = notesState + newNote
            SelectionManager.select(Selectable.PianoRollNote(trackIndex, entryStartMs, newNote), single = true)
        }
    }

    fun handleNoteDragStart(offset: Offset) {
        val gridPoint = resolveGridPoint(offset) ?: return
        val contentX = latestViewport.screenToContentX(offset.x)
        val clipTimeMs = latestViewport.contentXToClipTimeMs(contentX, latestOobOverhangMs)
        val contentOffset = gridPoint.pointInDevice
        val pitch = latestMetrics.yPxToPitch(contentOffset.y)

        when (activeTool) {
            TimelineEditorTool.NORMAL -> {
                val noteRects = noteRectsForDevice(gridPoint.deviceIndex)
                val hitTarget = findPianoRollHitTarget(contentOffset, noteRects)

                when (hitTarget) {
                    is PianoRollHitTarget.NoteBody -> {
                        val note = hitTarget.note
                        activeDragNote = note
                        val isSelected = selections.any {
                            it is Selectable.PianoRollNote &&
                                it.entryStartMs == entryStartMs &&
                                it.trackIndex == trackIndex &&
                                it.note.noteId == note.noteId
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
                val anchorStartMs = floorClipTimeToGrid(clipTimeMs, gridResolution, beatDurationMs)
                draftAnchorCellStartMs = anchorStartMs
                val cellDurMs = currentCellDurationMs(gridResolution, beatDurationMs)
                draftNote = buildDraftNote(
                    device = gridPoint.deviceIndex,
                    pitch = pitch,
                    color = selectedColor,
                    gradient = if (gradientMode) workingGradient else null,
                    anchorCellStartMs = anchorStartMs,
                    currentCellStartMs = anchorStartMs,
                    cellDurationMs = cellDurMs,
                )
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
            val top = min(start.y, currentPos.y)
            val bottom = max(start.y, currentPos.y)

            val newlySelected = noteRectsInViewport()
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
            val gridPoint = resolveGridPoint(currentPos) ?: return
            val pitch = latestMetrics.yPxToPitch(gridPoint.pointInDevice.y)
            val currentCellStartMs = floorClipTimeToGrid(clipTimeMs, gridResolution, beatDurationMs)
            val cellDurMs = currentCellDurationMs(gridResolution, beatDurationMs)
            draftNote = buildDraftNote(
                device = gridPoint.deviceIndex,
                pitch = pitch,
                color = selectedColor,
                gradient = if (gradientMode) workingGradient else null,
                anchorCellStartMs = draftAnchorCellStartMs ?: draftNote!!.startTimeMs,
                currentCellStartMs = currentCellStartMs,
                cellDurationMs = cellDurMs,
            )
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
            draftAnchorCellStartMs = null
        }
    }

    fun handleNoteDragCancel() {
        marqueeStart = null
        marqueeCurrent = null
        marqueeGestureActive = false
        draftNote = null
        draftAnchorCellStartMs = null
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
                            val totalWidthPx = viewport.zoomX * beatDurationMs.toFloat() * latestTotalBeatsWithOverhang
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
                                            val totalContentWidthPx = targetZoomX * beatDurationMs.toFloat() * latestTotalBeatsWithOverhang

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
                                val downGridPoint = resolveGridPoint(down.position)
                                val noteOwnsGesture = activeTool == TimelineEditorTool.NORMAL &&
                                    downGridPoint != null &&
                                    findPianoRollHitTarget(
                                        downGridPoint.pointInDevice,
                                        noteRectsForDevice(downGridPoint.deviceIndex),
                                    ) !is PianoRollHitTarget.Empty
                                if (noteOwnsGesture) return@awaitEachGesture
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
                                    val previousTime = lastTapUptimeMillis
                                    val previousPosition = lastTapPosition
                                    val isDoubleTap = previousTime != null &&
                                        down.uptimeMillis - previousTime <= 400L &&
                                        previousPosition != null &&
                                        (down.position - previousPosition).getDistance() <= viewConfiguration.touchSlop * 2f
                                    if (isDoubleTap) {
                                        handleNoteDoubleTap(down.position)
                                        lastTapUptimeMillis = null
                                        lastTapPosition = null
                                    } else {
                                        handleNoteTap(down.position)
                                        lastTapUptimeMillis = down.uptimeMillis
                                        lastTapPosition = down.position
                                    }
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
                                visibleNotes.filter { it.resolvedDeviceIndex == index }.forEach { note ->
                                    val selected = selections.any {
                                        it is Selectable.PianoRollNote &&
                                            it.entryStartMs == entryStartMs &&
                                            it.trackIndex == trackIndex &&
                                            it.note.noteId == note.noteId
                                    }

                                    key(note.noteId) {
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
                                            onEditStart = {
                                                activeDragNote = note
                                                dragOffset = Offset.Zero
                                                resizeLeftDelta = 0f
                                                resizeRightDelta = 0f
                                                if (!selected) {
                                                    SelectionManager.select(
                                                        Selectable.PianoRollNote(trackIndex, entryStartMs, note),
                                                        single = !multiSelectModifierDown && !shiftModifierDown,
                                                    )
                                                }
                                            },
                                            onDoubleClick = {
                                                val result = onDeleteNotes(listOf(note))
                                                if (result.didChange) {
                                                    notesState = notesState.filterNot { it.noteId == note.noteId }
                                                    SelectionManager.replaceSelections(
                                                        SelectionManager.selections.value.filterNot {
                                                            it is Selectable.PianoRollNote && it.note.noteId == note.noteId
                                                        }
                                                    )
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

                                                val requestedPitchDelta = (-(dragOffset.y / metrics.noteHeightPx)).roundToInt()
                                                val minPitch = selectedNotes.minOf { it.resolvedPadIndex }
                                                val maxPitch = selectedNotes.maxOf { it.resolvedPadIndex }
                                                val pitchDelta = requestedPitchDelta.coerceIn(-minPitch, totalPitches - 1 - maxPitch)

                                                val timeAnchor = activeDragNote ?: selectedNotes.first()
                                                val anchorContentX = viewport.clipTimeMsToContentX(
                                                    timeAnchor.startTimeMs.toDouble(),
                                                    oobOverhangMs,
                                                )
                                                val requestedAnchorStartMs = snapClipTimeToGrid(
                                                    viewport.contentXToClipTimeMs(anchorContentX + dragOffset.x, oobOverhangMs),
                                                    gridResolution,
                                                    beatDurationMs,
                                                )
                                                val requestedTimeDelta = requestedAnchorStartMs - timeAnchor.startTimeMs
                                                val timeDelta = requestedTimeDelta.coerceAtLeast(-selectedNotes.minOf { it.startTimeMs })

                                                val noteUpdates = selectedNotes.map { noteToDrag ->
                                                    val newStartMs = noteToDrag.startTimeMs + timeDelta
                                                    val newPitch = noteToDrag.resolvedPadIndex + pitchDelta
                                                    val updatedNote = noteToDrag.copy(
                                                        startTimeMs = newStartMs,
                                                        device = noteToDrag.resolvedDeviceIndex,
                                                        pitch = newPitch,
                                                        led = noteToDrag.led.copy(index = newPitch),
                                                    )
                                                    noteToDrag to updatedNote
                                                }

                                                val result = onMoveNotes(
                                                    noteUpdates.map { TimelineEditedNote(before = it.first, after = it.second) }
                                                )

                                                if (result.didChange) {
                                                    val updatedNotes = notesState.map { existingNote ->
                                                        noteUpdates.find { it.first.noteId == existingNote.noteId }?.second ?: existingNote
                                                    }
                                                    notesState = updatedNotes
                                                    val replacements = noteUpdates.associate { it.first.noteId to it.second }
                                                    SelectionManager.replaceSelections(
                                                        SelectionManager.selections.value.map { selection ->
                                                            if (selection is Selectable.PianoRollNote) {
                                                                replacements[selection.note.noteId]?.let { selection.copy(note = it) }
                                                                    ?: selection
                                                            } else selection
                                                        }
                                                    )
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
                                                    val requestedStartMs = snapClipTimeToGrid(
                                                        viewport.contentXToClipTimeMs(newStartContentX, oobOverhangMs),
                                                        gridResolution,
                                                        beatDurationMs,
                                                    )
                                                    val newEndMs = noteToResize.endTimeMs
                                                    val minDur = currentCellDurationMs(gridResolution, beatDurationMs)
                                                    val newStartMs = requestedStartMs.coerceIn(0L, (newEndMs - minDur).coerceAtLeast(0L))
                                                    val newDurationMs = (newEndMs - newStartMs).coerceAtLeast(minDur)

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
                                                        noteUpdates.find { it.first.noteId == existingNote.noteId }?.second ?: existingNote
                                                    }
                                                    notesState = updatedNotes
                                                    val replacements = noteUpdates.associate { it.first.noteId to it.second }
                                                    SelectionManager.replaceSelections(
                                                        SelectionManager.selections.value.map { selection ->
                                                            if (selection is Selectable.PianoRollNote) {
                                                                replacements[selection.note.noteId]?.let { selection.copy(note = it) }
                                                                    ?: selection
                                                            } else selection
                                                        }
                                                    )
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
                                                        beatDurationMs,
                                                    )
                                                    val minDur = currentCellDurationMs(gridResolution, beatDurationMs)
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
                                                        noteUpdates.find { it.first.noteId == existingNote.noteId }?.second ?: existingNote
                                                    }
                                                    notesState = updatedNotes
                                                    val replacements = noteUpdates.associate { it.first.noteId to it.second }
                                                    SelectionManager.replaceSelections(
                                                        SelectionManager.selections.value.map { selection ->
                                                            if (selection is Selectable.PianoRollNote) {
                                                                replacements[selection.note.noteId]?.let { selection.copy(note = it) }
                                                                    ?: selection
                                                            } else selection
                                                        }
                                                    )
                                                }
                                                resizeRightDelta = 0f
                                                activeDragNote = null
                                            },
                                            dragOffset = if (selected && activeDragNote != null) {
                                                val anchor = activeDragNote!!
                                                val anchorContentX = viewport.clipTimeMsToContentX(
                                                    anchor.startTimeMs.toDouble(),
                                                    oobOverhangMs,
                                                )
                                                val snappedAnchorMs = snapClipTimeToGrid(
                                                    viewport.contentXToClipTimeMs(anchorContentX + dragOffset.x, oobOverhangMs),
                                                    gridResolution,
                                                    beatDurationMs,
                                                )
                                                val selectedStart = selections.filterIsInstance<Selectable.PianoRollNote>()
                                                    .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                                                    .minOfOrNull { it.note.startTimeMs } ?: anchor.startTimeMs
                                                val deltaMs = (snappedAnchorMs - anchor.startTimeMs)
                                                    .coerceAtLeast(-selectedStart)
                                                Offset(metrics.durationMsToWidthPx(deltaMs), dragOffset.y)
                                            } else Offset.Zero,
                                            resizeLeftDelta = if (selected && activeDragNote != null && resizeLeftDelta != 0f) {
                                                val requestedStartMs = snapClipTimeToGrid(
                                                    viewport.contentXToClipTimeMs(
                                                        viewport.clipTimeMsToContentX(note.startTimeMs.toDouble(), oobOverhangMs) + resizeLeftDelta,
                                                        oobOverhangMs,
                                                    ),
                                                    gridResolution,
                                                    beatDurationMs,
                                                )
                                                val minimumDuration = currentCellDurationMs(gridResolution, beatDurationMs)
                                                val previewStartMs = requestedStartMs.coerceIn(
                                                    0L,
                                                    (note.endTimeMs - minimumDuration).coerceAtLeast(0L),
                                                )
                                                metrics.durationMsToWidthPx(previewStartMs - note.startTimeMs)
                                            } else 0f,
                                            resizeRightDelta = if (selected && activeDragNote != null && resizeRightDelta != 0f) {
                                                val requestedEndMs = snapClipTimeToGrid(
                                                    viewport.contentXToClipTimeMs(
                                                        viewport.clipTimeMsToContentX(note.endTimeMs.toDouble(), oobOverhangMs) + resizeRightDelta,
                                                        oobOverhangMs,
                                                    ),
                                                    gridResolution,
                                                    beatDurationMs,
                                                )
                                                val minimumEndMs = note.startTimeMs + currentCellDurationMs(gridResolution, beatDurationMs)
                                                metrics.durationMsToWidthPx(requestedEndMs.coerceAtLeast(minimumEndMs) - note.endTimeMs)
                                            } else 0f
                                        )
                                    }
                                }

                                draftNote?.takeIf { it.resolvedDeviceIndex == index }?.let { draft ->
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

private fun currentCellDurationMs(currentResolution: GridResolution, beatDurationMs: Double): Long =
    (beatDurationMs / currentResolution.snapDivisionsPerBeat).toLong().coerceAtLeast(1L)
