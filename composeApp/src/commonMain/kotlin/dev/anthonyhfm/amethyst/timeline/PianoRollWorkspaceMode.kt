package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.ColorControls
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MS_PER_BEAT: Long = 500L

private enum class GridResolution(val snapDivisionsPerBeat: Int, val subBeatsPerBeat: Int) {
    Quarter(snapDivisionsPerBeat = 4, subBeatsPerBeat = 1),
    Eighth(snapDivisionsPerBeat = 8, subBeatsPerBeat = 2),
    Sixteenth(snapDivisionsPerBeat = 16, subBeatsPerBeat = 4),
    ThirtySecond(snapDivisionsPerBeat = 32, subBeatsPerBeat = 8),
    SixtyFourth(snapDivisionsPerBeat = 64, subBeatsPerBeat = 16),
    OneTwentyEighth(snapDivisionsPerBeat = 128, subBeatsPerBeat = 32),
}

private class PianoRollMetrics(
    val totalPitches: Int,
    val noteHeightDp: Dp,
    val pixelsPerBeatDp: Dp,
    private val density: Density,
    private val gridResolution: GridResolution
) {
    val noteHeightPx: Float = with(density) { noteHeightDp.toPx() }
    val pixelsPerBeatPx: Float = with(density) { pixelsPerBeatDp.toPx() }
    val noteRenderHeightPx: Float = noteHeightPx - 4f

    fun pitchToYPx(pitch: Int): Float = (totalPitches - 1 - pitch) * noteHeightPx
    fun yPxToPitch(y: Float): Int = (totalPitches - 1 - (y / noteHeightPx).toInt()).coerceIn(0, totalPitches - 1)

    fun timeMsToXPx(startTimeMs: Long): Float = (startTimeMs / MS_PER_BEAT.toFloat()) * pixelsPerBeatPx
    fun durationMsToWidthPx(durationMs: Long): Float = (durationMs / MS_PER_BEAT.toFloat()) * pixelsPerBeatPx

    fun xPxToTimeMs(x: Float): Long {
        val beatTime = x / pixelsPerBeatPx
        val snappedBeatTime = (beatTime * gridResolution.snapDivisionsPerBeat).roundToInt() / gridResolution.snapDivisionsPerBeat.toFloat()
        return (snappedBeatTime * MS_PER_BEAT).toLong().coerceAtLeast(0L)
    }

    fun xPxToBeatStartTimeMs(x: Float): Long {
        val beatIndex = (x / pixelsPerBeatPx).toInt().coerceAtLeast(0)
        return beatIndex * MS_PER_BEAT
    }
}

class PianoRollWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Piano Roll"
    override val selectable: Boolean = false
    override val claimInputs: Boolean = true

    var currentEntry by mutableStateOf<MidiEntry?>(null)
    var trackIndex: Int = -1
    var entryStartMs: Long = 0L
    
    var onNoteAdd: ((MidiNote) -> Unit)? = null
    var onNoteUpdate: ((MidiNote, MidiNote) -> Unit)? = null
    var onNoteDelete: ((MidiNote) -> Unit)? = null
    var modeClose: (() -> Unit)? = null

    var selectedColor by mutableStateOf(Color(0xFFFF6B35))

    var multiSelectModifierDown by mutableStateOf(false)

    private fun pitchToXY(pitch: Int): Pair<Int, Int> {
        val launchpadIndex = pitch / 100
        val localPitch = pitch % 100
        val x = localPitch % 10
        val y = localPitch / 10
        return Pair(x, y)
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    @Composable
    fun ModeContent(paddingValues: PaddingValues) {
        val entry = currentEntry ?: return
        val launchpads = Heaven.devices
        val selections by SelectionManager.selections.collectAsState()

        LaunchedEffect(selections) {
            val selectedNotes = selections.filterIsInstance<Selectable.PianoRollNote>()
                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

            if (selectedNotes.size == 1) {
                val note = selectedNotes.first().note
                selectedColor = Color(note.led.red, note.led.green, note.led.blue)
            }
        }

        var zoomFactor by remember { mutableStateOf(1f) }
        var gridResolution by remember { mutableStateOf(GridResolution.Quarter) }

        fun resolutionForZoom(z: Float): GridResolution = when {
            z < 1.5f -> GridResolution.Quarter
            z < 2.5f -> GridResolution.Eighth
            z < 4f -> GridResolution.Sixteenth
            z < 6f -> GridResolution.ThirtySecond
            z < 9f -> GridResolution.SixtyFourth
            else -> GridResolution.OneTwentyEighth
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ColorControls(
                        color = selectedColor,
                        onColorChange = { newColor ->
                            selectedColor = newColor
                            WorkspaceRepository.addRecentColor(
                                Triple(newColor.red, newColor.green, newColor.blue)
                            )

                            val selected = SelectionManager.selections.value.filterIsInstance<Selectable.PianoRollNote>()
                                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

                            if (selected.isNotEmpty()) {
                                val notesBefore = selected.map { it.note }
                                val updatedNotes = mutableListOf<MidiNote>()

                                selected.forEach { sel ->
                                    val updatedNote = sel.note.copy(
                                        led = sel.note.led.copy(
                                            red = newColor.red,
                                            green = newColor.green,
                                            blue = newColor.blue
                                        )
                                    )
                                    updatedNotes.add(updatedNote)
                                    onNoteUpdate?.invoke(sel.note, updatedNote)
                                }

                                UndoManager.addAction(
                                    UndoableAction.PianoRollNoteColorChange(
                                        trackIndex = trackIndex,
                                        entryStartMs = entryStartMs,
                                        notesBefore = notesBefore,
                                        notesAfter = updatedNotes,
                                        onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                                        currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                                        currentEntrySetter = { entry -> this@PianoRollWorkspaceMode.currentEntry = entry }
                                    )
                                )

                                currentEntry = currentEntry?.copy(
                                    notes = currentEntry?.notes?.map { note ->
                                        if (selected.any { it.note == note }) {
                                            note.copy(
                                                led = note.led.copy(
                                                    red = newColor.red,
                                                    green = newColor.green,
                                                    blue = newColor.blue
                                                )
                                            )
                                        } else note
                                    } ?: emptyList()
                                )

                                SelectionManager.clear()

                                updatedNotes.forEach { updatedNote ->
                                    SelectionManager.select(
                                        Selectable.PianoRollNote(trackIndex, entryStartMs, updatedNote),
                                        single = false
                                    )
                                }
                            }
                        }
                    )
                }
            }

            PianoRollEditor(
                entry = entry,
                launchpads = launchpads,
                trackIndex = trackIndex,
                entryStartMs = entryStartMs,
                multiSelectModifierDown = multiSelectModifierDown,
                selectedColor = selectedColor,
                onNoteAdd = onNoteAdd,
                onNoteUpdate = onNoteUpdate,
                onNoteDelete = onNoteDelete,
                zoomFactorState = zoomFactor,
                onZoomFactorChange = { newZoom ->
                    val clamped = newZoom.coerceIn(0.75f, 12f)

                    zoomFactor = clamped
                    val targetRes = resolutionForZoom(zoomFactor)
                    if (targetRes != gridResolution) {
                        gridResolution = targetRes
                    }
                },
                gridResolution = gridResolution
            )
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.CtrlLeft, Key.CtrlRight, Key.MetaLeft, Key.MetaRight -> multiSelectModifierDown = true
            }
        } else if (event.type == KeyEventType.KeyUp) {
            when (event.key) {
                Key.CtrlLeft, Key.CtrlRight, Key.MetaLeft, Key.MetaRight -> multiSelectModifierDown = false
            }
        }

        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.Escape -> { modeClose?.invoke(); return true }

                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                    val selected = SelectionManager.selections.value.filterIsInstance<Selectable.PianoRollNote>()
                        .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

                    if (selected.isNotEmpty()) {
                        val currentEntry = currentEntry ?: return false

                        val launchpadCount = Heaven.devices.size.coerceAtLeast(1)
                        val totalPitches = launchpadCount * 100

                        // Berechne Zellengröße basierend auf BPM (nicht fest 500ms)
                        val bpm = WorkspaceRepository.bpm.value
                        val msPerBeat = (60000.0 / bpm).toLong()
                        val cellDurationMs = msPerBeat / 4 // Viertelnote

                        val pitchDelta = when (event.key) {
                            Key.DirectionUp -> 1
                            Key.DirectionDown -> -1
                            else -> 0
                        }

                        val timeDelta = when (event.key) {
                            Key.DirectionRight -> cellDurationMs
                            Key.DirectionLeft -> -cellDurationMs
                            else -> 0L
                        }

                        val noteUpdatesBefore = mutableListOf<MidiNote>()
                        val noteUpdatesAfter = mutableListOf<MidiNote>()

                        selected.forEach { sel ->
                            val newPitch = (sel.note.pitch + pitchDelta).coerceIn(0, totalPitches - 1)
                            val newStartTime = (sel.note.startTimeMs + timeDelta).coerceIn(0, currentEntry.durationMs - sel.note.durationMs)

                            val updatedNote = sel.note.copy(
                                pitch = newPitch,
                                startTimeMs = newStartTime,
                                led = sel.note.led.copy(index = newPitch)
                            )

                            noteUpdatesBefore.add(sel.note)
                            noteUpdatesAfter.add(updatedNote)
                            onNoteUpdate?.invoke(sel.note, updatedNote)
                        }

                        UndoManager.addAction(
                            UndoableAction.PianoRollNoteMove(
                                trackIndex = trackIndex,
                                entryStartMs = entryStartMs,
                                notesBefore = noteUpdatesBefore,
                                notesAfter = noteUpdatesAfter,
                                onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                                currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                                currentEntrySetter = { entry -> this@PianoRollWorkspaceMode.currentEntry = entry }
                            )
                        )

                        this.currentEntry = currentEntry.copy(
                            notes = currentEntry.notes.map { note ->
                                noteUpdatesBefore.zip(noteUpdatesAfter).find { it.first == note }?.second ?: note
                            }
                        )

                        SelectionManager.clear()
                        noteUpdatesAfter.forEach { updatedNote ->
                            SelectionManager.select(
                                Selectable.PianoRollNote(trackIndex, entryStartMs, updatedNote),
                                single = false
                            )
                        }

                        return true
                    }
                }

                Key.D -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        val selected = SelectionManager.selections.value.filterIsInstance<Selectable.PianoRollNote>()
                            .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

                        if (selected.isNotEmpty()) {
                            val currentEntry = currentEntry ?: return false

                            val latestEndTime = selected.maxOf { it.note.endTimeMs }

                            val earliestStartTime = selected.minOf { it.note.startTimeMs }
                            val offset = latestEndTime - earliestStartTime

                            val duplicates = selected.map { sel ->
                                sel.note.copy(
                                    startTimeMs = (sel.note.startTimeMs + offset).coerceAtMost(currentEntry.durationMs - sel.note.durationMs)
                                )
                            }

                            UndoManager.addAction(
                                UndoableAction.PianoRollNoteDuplication(
                                    trackIndex = trackIndex,
                                    entryStartMs = entryStartMs,
                                    duplicates = duplicates,
                                    onNoteAdd = { note -> onNoteAdd?.invoke(note) },
                                    onNoteDelete = { note -> onNoteDelete?.invoke(note) },
                                    currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                                    currentEntrySetter = { entry -> this@PianoRollWorkspaceMode.currentEntry = entry }
                                )
                            )

                            duplicates.forEach { duplicate ->
                                onNoteAdd?.invoke(duplicate)
                            }

                            this.currentEntry = currentEntry.copy(notes = currentEntry.notes + duplicates)

                            SelectionManager.clear()
                            duplicates.forEach { duplicate ->
                                SelectionManager.select(
                                    Selectable.PianoRollNote(trackIndex, entryStartMs, duplicate),
                                    single = false
                                )
                            }

                            return true
                        }
                    }
                }

                Key.Delete, Key.Backspace -> {
                    val selected = SelectionManager.selections.value.filterIsInstance<Selectable.PianoRollNote>()
                        .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                    if (selected.isNotEmpty()) {
                        val notesToDelete = selected.map { it.note }

                        UndoManager.addAction(
                            UndoableAction.PianoRollNoteDeletion(
                                trackIndex = trackIndex,
                                entryStartMs = entryStartMs,
                                notes = notesToDelete,
                                onNoteAdd = { note -> onNoteAdd?.invoke(note) },
                                onNoteDelete = { note -> onNoteDelete?.invoke(note) },
                                currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                                currentEntrySetter = { entry -> this@PianoRollWorkspaceMode.currentEntry = entry }
                            )
                        )

                        selected.forEach { sel ->
                            onNoteDelete?.invoke(sel.note)
                            currentEntry = currentEntry?.copy(notes = currentEntry?.notes?.filter { it != sel.note } ?: emptyList())
                        }
                        SelectionManager.clear(); return true
                    }
                }
            }
        }
        return false
    }
}

@Composable
private fun PianoRollEditor(
    entry: MidiEntry,
    launchpads: List<*>,
    trackIndex: Int,
    entryStartMs: Long,
    multiSelectModifierDown: Boolean,
    selectedColor: Color,
    onNoteAdd: ((MidiNote) -> Unit)?,
    onNoteUpdate: ((MidiNote, MidiNote) -> Unit)?,
    onNoteDelete: ((MidiNote) -> Unit)?,
    zoomFactorState: Float,
    onZoomFactorChange: (Float) -> Unit,
    gridResolution: GridResolution
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val density = LocalDensity.current

    val launchpadCount = launchpads.size.coerceAtLeast(1)
    val totalPitches = launchpadCount * 100

    val noteHeightDp: Dp = 22.dp
    val basePixelsPerBeatDp: Dp = 80.dp
    val effectivePixelsPerBeatDp = basePixelsPerBeatDp * zoomFactorState
    val beatsPerBar = 4

    val clipBeats = entry.durationMs.toFloat() / MS_PER_BEAT.toFloat()

    val metrics = remember(totalPitches, density, gridResolution, effectivePixelsPerBeatDp) {
        PianoRollMetrics(totalPitches, noteHeightDp, effectivePixelsPerBeatDp, density, gridResolution)
    }

    val canvasHeightDp = noteHeightDp * totalPitches
    val canvasWidthDp = effectivePixelsPerBeatDp * clipBeats

    var notesState by remember { mutableStateOf(entry.notes) }
    LaunchedEffect(entry) { notesState = entry.notes }

    val selections by SelectionManager.selections.collectAsState()

    var marqueeStart by remember { mutableStateOf<Offset?>(null) }
    var marqueeCurrent by remember { mutableStateOf<Offset?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var resizeLeftDelta by remember { mutableStateOf(0f) }
    var resizeRightDelta by remember { mutableStateOf(0f) }
    var activeDragNote by remember { mutableStateOf<MidiNote?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            PianoKeysColumn(
                totalPitches = totalPitches,
                noteHeight = noteHeightDp,
                verticalScroll = verticalScroll
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF1A1A1A))
                    .horizontalScroll(horizontalScroll)
                    .verticalScroll(verticalScroll)
                    .pointerInput(zoomFactorState, gridResolution) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val isZoomModifier = event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed
                                    val change = event.changes.firstOrNull()
                                    val deltaY = change?.scrollDelta?.y ?: 0f
                                    if (isZoomModifier && deltaY != 0f) {
                                        val direction = if (deltaY > 0f) -1f else 1f
                                        val factor = 1f + 0.03f * direction // Sehr smooth zoom
                                        val newZoom = (zoomFactorState * factor).coerceIn(0.75f, 12f)
                                        onZoomFactorChange(newZoom)
                                        change?.consume()
                                    }
                                }
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .width(canvasWidthDp)
                        .height(canvasHeightDp)
                        .drawBehind {
                            val widthPx = size.width
                            val heightPx = size.height
                            val darkBackground = Color(0xFF1A1A1A)
                            val lightRow = Color(0xFF242424)
                            val beatLine = Color(0xFF444444)
                            val barLine = Color(0xFF555555)
                            val quarterCellLine = Color(0xFF2A2A2A) // Kaum sichtbar für Viertel-Zellen

                            for (pitch in 0 until totalPitches) {
                                val y = pitch * metrics.noteHeightPx
                                val isWhiteKey = pitch % 12 in listOf(0, 2, 4, 5, 7, 9, 11)
                                drawRect(
                                    color = if (isWhiteKey) darkBackground else lightRow,
                                    topLeft = Offset(0f, y),
                                    size = Size(widthPx, metrics.noteHeightPx)
                                )
                            }

                            for (pitch in 0..totalPitches) {
                                val y = pitch * metrics.noteHeightPx
                                drawLine(Color(0xFF333333), Offset(0f, y), Offset(widthPx, y), 1f)
                            }

                            // Zeichne Viertel-Zellen Grid-Linien (kaum sichtbar)
                            val quarterSubdivisions = (clipBeats * 4).roundToInt()
                            for (quarterIndex in 0..quarterSubdivisions) {
                                val beatIndex = quarterIndex.toFloat() / 4
                                val x = beatIndex * metrics.pixelsPerBeatPx
                                if (x > widthPx) break
                                drawLine(
                                    color = quarterCellLine,
                                    start = Offset(x, 0f),
                                    end = Offset(x, heightPx),
                                    strokeWidth = 0.5f
                                )
                            }

                            // Zeichne Haupt-Grid-Linien (basierend auf aktuellem Grid)
                            val subdivisionsPerBeat = gridResolution.subBeatsPerBeat
                            val totalSubdivisions = (clipBeats * subdivisionsPerBeat).roundToInt()
                            for (subIndex in 0..totalSubdivisions) {
                                val beatIndex = subIndex.toFloat() / subdivisionsPerBeat
                                val x = beatIndex * metrics.pixelsPerBeatPx
                                if (x > widthPx) break
                                val isBarLine = (beatIndex % beatsPerBar) == 0f
                                drawLine(
                                    color = if (isBarLine) barLine else beatLine,
                                    start = Offset(x, 0f),
                                    end = Offset(x, heightPx),
                                    strokeWidth = if (isBarLine) 2f else 1f
                                )
                            }
                        }
                        .pointerInput(entry, onNoteAdd) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val clickedNote = notesState.firstOrNull { note ->
                                        val yPx = metrics.pitchToYPx(note.pitch)
                                        val xPx = metrics.timeMsToXPx(note.startTimeMs)
                                        val wPx = metrics.durationMsToWidthPx(note.durationMs)
                                        offset.x in xPx..(xPx + wPx) &&
                                            offset.y >= yPx && offset.y <= yPx + metrics.noteRenderHeightPx
                                    }
                                    if (clickedNote != null) {
                                        SelectionManager.select(
                                            Selectable.PianoRollNote(trackIndex, entryStartMs, clickedNote),
                                            single = !multiSelectModifierDown
                                        )
                                    } else if (!multiSelectModifierDown) SelectionManager.clear()
                                },
                                onDoubleTap = { offset ->
                                    val pitch = metrics.yPxToPitch(offset.y)

                                    val currentGridResolution = when {
                                        zoomFactorState < 1.5f -> GridResolution.Quarter
                                        zoomFactorState < 2.5f -> GridResolution.Eighth
                                        zoomFactorState < 4f -> GridResolution.Sixteenth
                                        zoomFactorState < 6f -> GridResolution.ThirtySecond
                                        zoomFactorState < 9f -> GridResolution.SixtyFourth
                                        else -> GridResolution.OneTwentyEighth
                                    }

                                    val subdivisions = currentGridResolution.subBeatsPerBeat
                                    val cellWidthPx = metrics.pixelsPerBeatPx / subdivisions
                                    val cellIndex = (offset.x / cellWidthPx).toInt().coerceAtLeast(0)

                                    val bpm = WorkspaceRepository.bpm.value
                                    val msPerBeat = (60000.0 / bpm).toLong()
                                    val startMs = (cellIndex * msPerBeat / subdivisions)

                                    if (startMs >= entry.durationMs) return@detectTapGestures
                                    val cellDurationMs = msPerBeat / subdivisions
                                    var durationMs = cellDurationMs
                                    if (startMs + durationMs > entry.durationMs) {
                                        durationMs = (entry.durationMs - startMs).coerceAtLeast(0L)
                                    }
                                    if (durationMs <= 0L) return@detectTapGestures
                                    val existing = notesState.firstOrNull { note ->
                                        val yPx = metrics.pitchToYPx(note.pitch)
                                        val xPx = metrics.timeMsToXPx(note.startTimeMs)
                                        val wPx = metrics.durationMsToWidthPx(note.durationMs)
                                        offset.x in xPx..(xPx + wPx) &&
                                            offset.y >= yPx && offset.y <= yPx + metrics.noteRenderHeightPx
                                    }
                                    if (existing != null) {
                                        SelectionManager.select(
                                            Selectable.PianoRollNote(trackIndex, entryStartMs, existing),
                                            single = !multiSelectModifierDown
                                        )
                                    } else {
                                        val newNote = MidiNote.withColor(
                                            pitch = pitch,
                                            color = selectedColor,
                                            startTimeMs = startMs,
                                            durationMs = durationMs
                                        )
                                        onNoteAdd?.invoke(newNote)
                                        notesState = notesState + newNote

                                        UndoManager.addAction(
                                            UndoableAction.PianoRollNoteCreation(
                                                trackIndex = trackIndex,
                                                entryStartMs = entryStartMs,
                                                note = newNote,
                                                onNoteAdd = { note -> onNoteAdd?.invoke(note) },
                                                onNoteDelete = { note -> onNoteDelete?.invoke(note) },
                                                currentEntryGetter = { entry },
                                                currentEntrySetter = { /* Entry is managed by parent */ }
                                            )
                                        )

                                        SelectionManager.select(
                                            Selectable.PianoRollNote(trackIndex, entryStartMs, newNote),
                                            single = !multiSelectModifierDown
                                        )
                                    }
                                }
                            )
                        }
                        .pointerInput(notesState, multiSelectModifierDown) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    marqueeStart = offset; marqueeCurrent = offset
                                    if (!multiSelectModifierDown) SelectionManager.clear()
                                },
                                onDragEnd = {
                                    val s = marqueeStart; val c = marqueeCurrent
                                    if (s != null && c != null) {
                                        val left = min(s.x, c.x); val right = max(s.x, c.x)
                                        val top = min(s.y, c.y); val bottom = max(s.y, c.y)
                                        notesState.forEach { note ->
                                            val yPx = metrics.pitchToYPx(note.pitch)
                                            val xPx = metrics.timeMsToXPx(note.startTimeMs)
                                            val wPx = metrics.durationMsToWidthPx(note.durationMs)
                                            val hPx = metrics.noteRenderHeightPx
                                            val overlaps = xPx < right && xPx + wPx > left && yPx < bottom && yPx + hPx > top
                                            if (overlaps) SelectionManager.select(
                                                Selectable.PianoRollNote(trackIndex, entryStartMs, note),
                                                single = false
                                            )
                                        }
                                    }
                                    marqueeStart = null; marqueeCurrent = null
                                },
                                onDragCancel = { marqueeStart = null; marqueeCurrent = null },
                                onDrag = { change, dragAmount -> change.consume(); marqueeCurrent = marqueeCurrent?.let { it + dragAmount } }
                            )
                        }
                ) {
                    notesState.forEach { note ->
                        if (note.pitch in 0 until totalPitches) {
                            val selected = selections.filterIsInstance<Selectable.PianoRollNote>().any { it.note == note && it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

                            NoteBox(
                                note = note,
                                metrics = metrics,
                                isSelected = selected,
                                onSelect = {
                                    if (!selected) {
                                        SelectionManager.select(
                                            Selectable.PianoRollNote(trackIndex, entryStartMs, note),
                                            single = !multiSelectModifierDown
                                        )
                                    }
                                    activeDragNote = note
                                },
                                onDrag = { dragAmount ->
                                    dragOffset += dragAmount
                                },
                                onDragEnd = {
                                    val selectedNotes = selections.filterIsInstance<Selectable.PianoRollNote>()
                                        .map { it.note }
                                        .ifEmpty { listOf(note) }

                                    val pitchDelta = if (dragOffset.y != 0f) {
                                        -kotlin.math.round(dragOffset.y / metrics.noteHeightPx).toInt()
                                    } else {
                                        0
                                    }

                                    val notesBefore = selectedNotes.toList()
                                    val noteUpdates = selectedNotes.map { noteToDrag ->
                                        val baseX = metrics.timeMsToXPx(noteToDrag.startTimeMs)
                                        val newX = baseX + dragOffset.x
                                        var newTimeMs = metrics.xPxToTimeMs(newX)
                                        val newPitch = (noteToDrag.pitch + pitchDelta).coerceIn(0, metrics.totalPitches - 1)

                                        val maxStart = (entry.durationMs - noteToDrag.durationMs).coerceAtLeast(0L)
                                        if (newTimeMs > maxStart) newTimeMs = maxStart
                                        val updatedNote = noteToDrag.copy(
                                            startTimeMs = newTimeMs,
                                            pitch = newPitch,
                                            led = noteToDrag.led.copy(index = newPitch)
                                        )
                                        noteToDrag to updatedNote
                                    }

                                    val notesAfter = noteUpdates.map { it.second }

                                    UndoManager.addAction(
                                        UndoableAction.PianoRollNoteMove(
                                            trackIndex = trackIndex,
                                            entryStartMs = entryStartMs,
                                            notesBefore = notesBefore,
                                            notesAfter = notesAfter,
                                            onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                                            currentEntryGetter = { entry },
                                            currentEntrySetter = { /* Managed by parent */ }
                                        )
                                    )

                                    noteUpdates.forEach { (old, new) -> onNoteUpdate?.invoke(old, new) }
                                    val updatedNotes = notesState.map { n ->
                                        noteUpdates.find { it.first == n }?.second ?: n
                                    }
                                    notesState = updatedNotes
                                    SelectionManager.clear()
                                    noteUpdates.forEach { (_, new) ->
                                        SelectionManager.select(
                                            Selectable.PianoRollNote(trackIndex, entryStartMs, new),
                                            single = false
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
                                        .map { it.note }
                                        .ifEmpty { activeDragNote?.let { listOf(it) } ?: emptyList() }

                                    if (selectedNotes.isEmpty()) {
                                        resizeLeftDelta = 0f
                                        activeDragNote = null
                                        return@NoteBox
                                    }

                                    val notesBefore = selectedNotes.toList()
                                    val noteUpdates = selectedNotes.mapNotNull { noteToResize ->
                                        val newX = metrics.timeMsToXPx(noteToResize.startTimeMs) + resizeLeftDelta
                                        val newStartMs = metrics.xPxToTimeMs(newX).coerceAtLeast(0L)
                                        val newEndMs = noteToResize.endTimeMs
                                        val minDur = MS_PER_BEAT / 4
                                        var newDurationMs = (newEndMs - newStartMs).coerceAtLeast(minDur)
                                        if (newStartMs + newDurationMs > entry.durationMs) {
                                            newDurationMs = (entry.durationMs - newStartMs).coerceAtLeast(minDur)
                                        }

                                        if (newDurationMs < minDur || newStartMs < 0) return@mapNotNull null

                                        val updatedNote = noteToResize.copy(
                                            startTimeMs = newStartMs,
                                            durationMs = newDurationMs
                                        )
                                        noteToResize to updatedNote
                                    }

                                    val notesAfter = noteUpdates.map { it.second }

                                    if (noteUpdates.isNotEmpty()) {
                                        UndoManager.addAction(
                                            UndoableAction.PianoRollNoteResize(
                                                trackIndex = trackIndex,
                                                entryStartMs = entryStartMs,
                                                notesBefore = notesBefore,
                                                notesAfter = notesAfter,
                                                onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                                                currentEntryGetter = { entry },
                                                currentEntrySetter = { /* Managed by parent */ }
                                            )
                                        )
                                    }

                                    noteUpdates.forEach { (old, new) -> onNoteUpdate?.invoke(old, new) }
                                    val updatedNotes = notesState.map { n ->
                                        noteUpdates.find { it.first == n }?.second ?: n
                                    }
                                    notesState = updatedNotes
                                    SelectionManager.clear()
                                    noteUpdates.forEach { (_, new) ->
                                        SelectionManager.select(
                                            Selectable.PianoRollNote(trackIndex, entryStartMs, new),
                                            single = false
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
                                        .map { it.note }
                                        .ifEmpty { activeDragNote?.let { listOf(it) } ?: emptyList() }

                                    if (selectedNotes.isEmpty()) {
                                        resizeRightDelta = 0f
                                        activeDragNote = null
                                        return@NoteBox
                                    }

                                    val notesBefore = selectedNotes.toList()
                                    val noteUpdates = selectedNotes.mapNotNull { noteToResize ->
                                        val baseWidthPx = metrics.durationMsToWidthPx(noteToResize.durationMs)
                                        val newWidthPx = baseWidthPx + resizeRightDelta
                                        val newEndX = metrics.timeMsToXPx(noteToResize.startTimeMs) + newWidthPx
                                        var newEndTimeMs = metrics.xPxToTimeMs(newEndX)
                                        val minDur = MS_PER_BEAT / 4
                                        if (newEndTimeMs > entry.durationMs) newEndTimeMs = entry.durationMs
                                        val newDurationMs = (newEndTimeMs - noteToResize.startTimeMs).coerceAtLeast(minDur)

                                        if (newDurationMs < minDur) return@mapNotNull null

                                        val updatedNote = noteToResize.copy(durationMs = newDurationMs)
                                        noteToResize to updatedNote
                                    }

                                    val notesAfter = noteUpdates.map { it.second }

                                    if (noteUpdates.isNotEmpty()) {
                                        UndoManager.addAction(
                                            UndoableAction.PianoRollNoteResize(
                                                trackIndex = trackIndex,
                                                entryStartMs = entryStartMs,
                                                notesBefore = notesBefore,
                                                notesAfter = notesAfter,
                                                onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                                                currentEntryGetter = { entry },
                                                currentEntrySetter = { /* Managed by parent */ }
                                            )
                                        )
                                    }

                                    noteUpdates.forEach { (old, new) -> onNoteUpdate?.invoke(old, new) }
                                    val updatedNotes = notesState.map { n ->
                                        noteUpdates.find { it.first == n }?.second ?: n
                                    }
                                    notesState = updatedNotes
                                    SelectionManager.clear()
                                    noteUpdates.forEach { (_, new) ->
                                        SelectionManager.select(
                                            Selectable.PianoRollNote(trackIndex, entryStartMs, new),
                                            single = false
                                        )
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

                    val s = marqueeStart; val c = marqueeCurrent
                    if (s != null && c != null) {
                        val left = min(s.x, c.x); val top = min(s.y, c.y)
                        val w = kotlin.math.abs(c.x - s.x); val h = kotlin.math.abs(c.y - s.y)
                        with(LocalDensity.current) {
                            Box(
                                modifier = Modifier
                                    .offset(x = left.toDp(), y = top.toDp())
                                    .size(w.toDp(), h.toDp())
                                    .border(1.dp, MaterialTheme.colorScheme.primary)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteBox(
    note: MidiNote,
    metrics: PianoRollMetrics,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeLeft: (resizeDelta: Float) -> Unit,
    onResizeLeftEnd: () -> Unit,
    onResizeRight: (resizeDelta: Float) -> Unit,
    onResizeRightEnd: () -> Unit,
    dragOffset: Offset,
    resizeLeftDelta: Float,
    resizeRightDelta: Float
) {
    val density = LocalDensity.current

    val baseY = metrics.pitchToYPx(note.pitch)
    val baseX = metrics.timeMsToXPx(note.startTimeMs)
    val baseWidthPx = metrics.durationMsToWidthPx(note.durationMs)

    val snappedDragOffsetY = if (dragOffset.y != 0f) {
        val pitchSteps = kotlin.math.round(dragOffset.y / metrics.noteHeightPx).toInt()
        pitchSteps * metrics.noteHeightPx
    } else {
        0f
    }

    val currentX = baseX + dragOffset.x + resizeLeftDelta
    val currentY = baseY + snappedDragOffsetY
    val currentWidthPx = (baseWidthPx - resizeLeftDelta + resizeRightDelta).coerceAtLeast(20f)

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { currentX.toDp() },
                y = with(density) { currentY.toDp() }
            )
            .size(
                width = with(density) { currentWidthPx.toDp() },
                height = 22.dp
            )
            .background(Color(note.led.red, note.led.green, note.led.blue))
            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(if (isSelected) 1f else 0.4f))
            .padding(1.dp)
            .border(1.dp, MaterialTheme.colorScheme.surfaceDim)
            .clickable { onSelect() }
            .pointerInput(note) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDragEnd = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(6.dp)
                .fillMaxHeight()
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(note) {
                    detectDragGestures(
                        onDragStart = { onSelect() },
                        onDragEnd = { onResizeLeftEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onResizeLeft(dragAmount.x)
                        }
                    )
                }
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(6.dp)
                .fillMaxHeight()
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(note) {
                    detectDragGestures(
                        onDragStart = { onSelect() },
                        onDragEnd = { onResizeRightEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onResizeRight(dragAmount.x)
                        }
                    )
                }
        )
    }
}

@Composable
private fun PianoKeysColumn(
    totalPitches: Int,
    noteHeight: Dp,
    verticalScroll: ScrollState
) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .fillMaxHeight()
            .background(Color(0xFF2A2A2A))
            .verticalScroll(verticalScroll, enabled = false)
    ) {
        Column(
            modifier = Modifier
                .width(120.dp)
                .height(noteHeight * totalPitches)
        ) {
            for (pitch in (totalPitches - 1) downTo 0) {
                val noteInOctave = pitch % 12
                val isBlackKey = noteInOctave in listOf(1, 3, 6, 8, 10)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(noteHeight)
                        .background(if (isBlackKey) MaterialTheme.colorScheme.surfaceDim else MaterialTheme.colorScheme.onSurface)
                        .drawBehind {
                            drawLine(
                                color = Color(0xFF0A0A0A),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1f
                            )
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {}
            }
        }
    }
}