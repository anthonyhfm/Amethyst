package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import dev.anthonyhfm.amethyst.timeline.contract.TimelineActiveEditorContext
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipContext
import dev.anthonyhfm.amethyst.timeline.contract.TimelineEditorSurface
import dev.anthonyhfm.amethyst.timeline.contract.TimelineEditorTool
import dev.anthonyhfm.amethyst.timeline.contract.TimelineTimingContext
import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import dev.anthonyhfm.amethyst.timeline.data.isGradient
import dev.anthonyhfm.amethyst.timeline.data.resolvedDeviceIndex
import dev.anthonyhfm.amethyst.timeline.data.resolvedPadIndex
import dev.anthonyhfm.amethyst.timeline.migration.LegacyPianoRollPath
import dev.anthonyhfm.amethyst.timeline.migration.PianoRollCutoverSupport
import dev.anthonyhfm.amethyst.timeline.ui.pianoroll.PianoRollEditorCanvas
import dev.anthonyhfm.amethyst.timeline.ui.pianoroll.PianoRollInspectorSidebar
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.globalPadForMidiIndex
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportConfig
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportPanBoundsPolicy
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class PianoRollWorkspaceMode : WorkspaceMode() {
    override val displayName: String = "Piano Roll"
    override val selectableMode: Boolean = false
    override val claimMidiInputs: Boolean = true

    var activeTool by mutableStateOf(TimelineEditorTool.NORMAL)
    var clipContext by mutableStateOf<TimelineClipContext?>(null)
        private set
    var timingContextProvider: (() -> TimelineTimingContext)? = null

    var currentEntry by mutableStateOf<MidiEntry?>(null)
    val trackIndex: Int
        get() = clipContext?.trackIndex ?: -1
    val entryStartMs: Long
        get() = clipContext?.entryStartMs ?: currentEntry?.startTimeMs ?: 0L
    val cutoverMarker
        get() = PianoRollCutoverSupport.marker(
            clipContext = clipContext,
            legacySource = if (clipContext == null) {
                "PianoRollWorkspaceMode callback bridge"
            } else {
                null
            }
        )
    private val isTimelineBackedEditing: Boolean
        get() = cutoverMarker.usesTimelineCommandSurface

    var onNoteAdd: ((MidiNote) -> Unit)? = null
    var onNoteUpdate: ((MidiNote, MidiNote) -> Unit)? = null
    var onNoteDelete: ((MidiNote) -> Unit)? = null
    var modeClose: (() -> Unit)? = null
    var onPlaybackToggle: (() -> Unit)? = null

    var standalonePlaybackPositionMs: (() -> Long?)? = null
    var foldPads by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
        private set
    var canZoom by mutableStateOf(false)
        private set
    private var zoomInHandler: (() -> Unit)? = null
    private var zoomOutHandler: (() -> Unit)? = null
    private var zoomFitHandler: (() -> Unit)? = null
    private var zoomSelectionHandler: (() -> Unit)? = null
    private var zoomNotesHandler: ((Float) -> Unit)? = null
    private val padPreview = PianoRollPadPreview()
    private val notePreview = PianoRollPadPreview(layer = Int.MAX_VALUE - 1)
    private val framePreview = PianoRollPadPreview(layer = Int.MAX_VALUE - 2)

    override fun onDeactivate() {
        padPreview.clear()
        notePreview.clear()
        framePreview.clear()
        pressedKeysState.value = emptyMap()
        modeClose?.invoke()
    }

    val pressedKeysState = MutableStateFlow<Map<Pair<Int, Int>, Boolean>>(emptyMap())

    var selectedColor by mutableStateOf(Color(0xFFFF6B35))
    var gradientMode by mutableStateOf(false)
    var workingGradient by mutableStateOf<List<NoteGradientStop>?>(null)
    var selectedGradientStopUUID by mutableStateOf<String?>(null)
    var selectedTimeMs by mutableStateOf<Long?>(null)
    var gridResolution by mutableStateOf(GridResolution.Quarter)
    var gridResolutionLocked by mutableStateOf(false)
    var snapEnabled by mutableStateOf(true)

    private var deleteSelectedGradientStopHandler: (() -> Unit)? = null

    val activeEditorContext: TimelineActiveEditorContext?
        get() = clipContext?.let { context ->
            TimelineActiveEditorContext(
                clipContext = context,
                surface = TimelineEditorSurface(
                    activeTool = activeTool,
                    timingContext = timingContextProvider?.invoke(),
                    gridResolution = gridResolution
                )
            )
        }

    fun bindClipContext(context: TimelineClipContext, entry: MidiEntry) {
        clipContext = context
        syncCurrentEntry(entry)
    }

    @LegacyPianoRollPath(
        replacement = "bindClipContext",
        cutover = "Provide a TimelineClipContext-backed entry whenever this mode edits a persisted piano roll clip."
    )
    fun bindLegacyEntry(entry: MidiEntry) {
        clipContext = null
        syncCurrentEntry(entry)
    }

    fun syncCurrentEntry(entry: MidiEntry?) {
        currentEntry = entry
        if (entry != null && clipContext?.entryStartMs != entry.startTimeMs) {
            clipContext = clipContext?.withEntryStart(entry.startTimeMs)
        }
    }

    fun syncClipEntryStart(newEntryStartMs: Long) {
        clipContext = clipContext?.withEntryStart(newEntryStartMs)
    }

    fun isEditingClip(trackIndex: Int, entryStartMs: Long): Boolean {
        return clipContext?.trackIndex == trackIndex && clipContext?.entryStartMs == entryStartMs
    }

    private fun currentBpm(): Double {
        return timingContextProvider?.invoke()?.bpm ?: WorkspaceRepository.bpm.value
    }

    fun togglePlayback() {
        if (clipContext != null) {
            if (TimelineRepository.isPlaying.value) {
                TimelineRepository.pause()
            } else {
                TimelineRepository.setPlayheadPosition(positionMs = entryStartMs + (selectedTimeMs ?: 0L))
                TimelineRepository.play()
            }
        } else {
            onPlaybackToggle?.invoke()
        }
    }

    fun zoomIn() {
        zoomInHandler?.invoke()
    }

    fun zoomOut() {
        zoomOutHandler?.invoke()
    }

    fun zoomNotesIn() {
        zoomNotesHandler?.invoke(1.25f)
    }

    fun zoomNotesOut() {
        zoomNotesHandler?.invoke(0.8f)
    }

    fun zoomToFit() {
        zoomFitHandler?.invoke()
    }

    private fun timelineEntrySnapshot(): MidiEntry? {
        val context = clipContext ?: return null
        val track = TimelineRepository.tracks.value.getOrNull(context.trackIndex) as? MidiTimelineTrack ?: return null
        return track.entries[context.entryStartMs]
    }

    private fun selectedNotes(
        selections: List<Selectable> = SelectionManager.selections.value
    ): List<Selectable.PianoRollNote> {
        return selections
            .filterIsInstance<Selectable.PianoRollNote>()
            .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
    }

    fun selectAllNotes(): Boolean {
        val entry = currentEntry ?: return false
        if (entry.notes.isEmpty()) return false

        SelectionManager.clear()
        entry.notes.forEach { note ->
            SelectionManager.select(
                Selectable.PianoRollNote(
                    trackIndex = trackIndex,
                    entryStartMs = entryStartMs,
                    note = note
                ),
                single = false
            )
        }

        return true
    }

    fun duplicateSelectedNotes(): Boolean {
        val selected = selectedNotes()
        if (selected.isEmpty()) return false

        val currentEntry = currentEntry ?: return false
        val latestEndTime = selected.maxOf { it.note.endTimeMs }
        val earliestStartTime = selected.minOf { it.note.startTimeMs }
        val offset = latestEndTime - earliestStartTime
        val duplicates = selected.map { sel ->
            sel.note.copy(
                startTimeMs = sel.note.startTimeMs + offset,
                noteId = UUID.randomUUID()
            )
        }

        val result = if (isTimelineBackedEditing) {
            TimelineCommandSurface.createNotes(
                trackIndex = trackIndex,
                entryStartMs = entryStartMs,
                notes = duplicates
            ).also { commandResult ->
                if (commandResult.didChange) {
                    syncCurrentEntry(timelineEntrySnapshot())
                }
            }
        } else {
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
            TimelineCommandResult(didChange = true)
        }

        if (!result.didChange) return false

        SelectionManager.clear()
        duplicates.forEach { duplicate ->
            SelectionManager.select(
                Selectable.PianoRollNote(trackIndex, entryStartMs, duplicate),
                single = false
            )
        }

        return true
    }

    fun deleteSelectedNotes(): Boolean {
        val selected = selectedNotes()
        if (selected.isEmpty()) return false

        val notesToDelete = selected.map { it.note }
        val result = if (isTimelineBackedEditing) {
            TimelineCommandSurface.deleteNotes(
                trackIndex = trackIndex,
                entryStartMs = entryStartMs,
                notes = notesToDelete
            ).also { commandResult ->
                if (commandResult.didChange) {
                    syncCurrentEntry(timelineEntrySnapshot())
                }
            }
        } else {
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

            selected.forEach { selection ->
                onNoteDelete?.invoke(selection.note)
            }
            currentEntry = currentEntry?.copy(
                notes = currentEntry?.notes.orEmpty().filterNot { note ->
                    notesToDelete.any { it.noteId == note.noteId }
                }
            )
            TimelineCommandResult(didChange = true)
        }

        if (!result.didChange) return false

        SelectionManager.clear()
        return true
    }

    private fun nudgeSelectedNotes(
        timeDirection: Int = 0,
        pitchDirection: Int = 0,
        resizeDirection: Int = 0,
        quantize: Boolean = false,
    ): Boolean {
        val selected = selectedNotes()
        if (selected.isEmpty()) return false
        val notesBefore = selected.map { it.note }
        val beatDurationMs = millisecondsPerBeat(currentBpm())
        val timeDelta = if (timeDirection == 0) {
            0L
        } else {
            val anchor = notesBefore.minOf { it.startTimeMs }
            val requestedDelta = stepClipTimeOnGrid(
                clipTimeMs = anchor,
                resolution = pianoRollNoteEditResolution(resolution = gridResolution),
                direction = timeDirection,
                beatDurationMs = beatDurationMs,
            ) - anchor
            requestedDelta.takeIf { anchor + it >= 0L } ?: 0L
        }
        val pitches = if (foldPads) {
            currentEntry?.notes.orEmpty().map { it.resolvedPadIndex }.distinct().sorted()
                .ifEmpty { (0..99).toList() }
        } else {
            (0..99).toList()
        }
        val movedNotes = movePianoRollNotes(
            notes = notesBefore,
            timeDeltaMs = timeDelta,
            padDelta = pitchDirection,
            pads = pitches,
        ).associate { it.before.noteId to it.after }
        val notesAfter = notesBefore.map { note ->
            val movedNote = movedNotes[note.noteId] ?: note
            movedNote.copy(
                startTimeMs = if (quantize) {
                    snapClipTimeToGrid(
                        clipTimeMs = note.startTimeMs.toDouble(),
                        resolution = gridResolution,
                        beatDurationMs = beatDurationMs,
                    ).coerceAtLeast(0L)
                } else {
                    movedNote.startTimeMs
                },
                durationMs = if (resizeDirection != 0) {
                    (stepClipTimeOnGrid(
                        clipTimeMs = note.endTimeMs,
                        resolution = pianoRollNoteEditResolution(resolution = gridResolution),
                        direction = resizeDirection,
                        beatDurationMs = beatDurationMs,
                    ) - note.startTimeMs).coerceAtLeast(1L)
                } else {
                    note.durationMs
                },
            )
        }
        val changes = notesBefore.zip(notesAfter) { before, after -> TimelineEditedNote(before, after) }
            .filter { it.before != it.after }
        if (changes.isEmpty()) return false

        val result = if (isTimelineBackedEditing) {
            TimelineCommandSurface.updateNotes(trackIndex, entryStartMs, changes).also {
                if (it.didChange) {
                    syncCurrentEntry(timelineEntrySnapshot())
                }
            }
        } else {
            changes.forEach { onNoteUpdate?.invoke(it.before, it.after) }
            UndoManager.addAction(
                UndoableAction.PianoRollNoteTransform(
                    trackIndex = trackIndex,
                    entryStartMs = entryStartMs,
                    notesBefore = changes.map(TimelineEditedNote::before),
                    notesAfter = changes.map(TimelineEditedNote::after),
                    onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                    currentEntryGetter = { currentEntry },
                    currentEntrySetter = { currentEntry = it },
                )
            )
            val replacements = changes.associate { it.before.noteId to it.after }
            currentEntry = currentEntry?.copy(
                notes = currentEntry?.notes.orEmpty().map { replacements[it.noteId] ?: it }
            )
            TimelineCommandResult(didChange = true)
        }

        if (result.didChange) {
            val replacements = changes.associate { it.before.noteId to it.after }
            SelectionManager.replaceSelections(
                SelectionManager.selections.value.map { selection ->
                    if (selection is Selectable.PianoRollNote) {
                        replacements[selection.note.noteId]?.let { selection.copy(note = it) } ?: selection
                    } else selection
                }
            )
        }
        return result.didChange
    }

    fun pasteNotes(pastedNotes: List<MidiNote>) {
        if (pastedNotes.isEmpty()) return

        val anchorTimeMs = selectedTimeMs
            ?: (timingContextProvider?.invoke()?.let {
                (TimelineRepository.playheadPositionMs.value - entryStartMs).coerceAtLeast(0L)
            } ?: 0L)

        val earliestStartTime = pastedNotes.minOf { it.startTimeMs }

        val newNotes = pastedNotes.map { note ->
            val offset = note.startTimeMs - earliestStartTime
            note.copy(
                startTimeMs = anchorTimeMs + offset,
                led = note.led.copy(index = note.pitch),
                noteId = UUID.randomUUID()
            )
        }

        val localEntry = currentEntry
        if (isTimelineBackedEditing) {
            TimelineCommandSurface.createNotes(
                trackIndex = trackIndex,
                entryStartMs = entryStartMs,
                notes = newNotes
            ).also { result ->
                if (result.didChange) {
                    syncCurrentEntry(timelineEntrySnapshot())
                }
            }
        } else if (localEntry != null) {
            newNotes.forEach { note ->
                onNoteAdd?.invoke(note)
            }
            UndoManager.addAction(
                UndoableAction.PianoRollNoteMultiCreation(
                    trackIndex = trackIndex,
                    entryStartMs = entryStartMs,
                    notes = newNotes,
                    onNoteAdd = { note -> onNoteAdd?.invoke(note) },
                    onNoteDelete = { note -> onNoteDelete?.invoke(note) },
                    currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                    currentEntrySetter = { entry -> this@PianoRollWorkspaceMode.currentEntry = entry }
                )
            )
            currentEntry = localEntry.copy(notes = localEntry.notes + newNotes)
        }

        SelectionManager.clear()
        newNotes.forEach { note ->
            SelectionManager.select(
                Selectable.PianoRollNote(trackIndex, entryStartMs, note),
                single = false
            )
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val entry = currentEntry ?: return
        val windowInfo = LocalWindowInfo.current
        val launchpads = Heaven.devices
        val selections by SelectionManager.selections.collectAsState()
        val timelinePlayheadMs by TimelineRepository.playheadPositionMs.collectAsState()
        val timelineIsPlaying by TimelineRepository.isPlaying.collectAsState()
        var standalonePositionMs by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(entry, clipContext) {
            if (clipContext == null) {
                while (true) {
                    withFrameNanos { standalonePositionMs = standalonePlaybackPositionMs?.invoke() }
                }
            }
        }
        val playheadPositionMs = if (clipContext != null) {
            timelinePlayheadMs - entryStartMs
        } else {
            standalonePositionMs
        }
        val isPlaying = if (clipContext != null) timelineIsPlaying else standalonePositionMs != null
        val workspaceBpm by WorkspaceRepository.bpm.collectAsState()
        val editorBpm = timingContextProvider?.invoke()?.bpm ?: workspaceBpm
        val beatDurationMs = millisecondsPerBeat(editorBpm)

        var gradientBeforeDrag by remember { mutableStateOf<List<NoteGradientStop>?>(null) }
        var paintBeforeInteraction by remember { mutableStateOf<List<MidiNote>?>(null) }

        val selectedPaint = selections.filterIsInstance<Selectable.PianoRollNote>()
            .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
            .map { it.note.noteId to it.note.led }

        LaunchedEffect(selectedPaint) {
            val selectedNotes = SelectionManager.selections.value
                .filterIsInstance<Selectable.PianoRollNote>()
                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

            if (selectedNotes.size == 1) {
                val note = selectedNotes.first().note
                val selectedStop = note.led.gradient?.firstOrNull {
                    it.selectionUUID == selectedGradientStopUUID
                }
                selectedColor = if (selectedStop != null) {
                    Color(red = selectedStop.r, green = selectedStop.g, blue = selectedStop.b)
                } else {
                    Color(red = note.led.red, green = note.led.green, blue = note.led.blue)
                }
                gradientMode = note.isGradient
                workingGradient = note.led.gradient
                if (selectedGradientStopUUID != null &&
                    note.led.gradient?.none { it.selectionUUID == selectedGradientStopUUID } != false
                ) {
                    selectedGradientStopUUID = null
                }
            } else if (selectedNotes.size > 1) {
                val allAreGradient = selectedNotes.all { it.note.isGradient }

                if (allAreGradient) {
                    val referenceGradient = selectedNotes.first().note.led.gradient
                    gradientMode = true
                    workingGradient = referenceGradient.takeIf { candidate ->
                        selectedNotes.all { it.note.led.gradient == candidate }
                    }

                    if (selectedGradientStopUUID != null &&
                        referenceGradient != null &&
                        referenceGradient.none { it.selectionUUID == selectedGradientStopUUID }
                    ) {
                        selectedGradientStopUUID = null
                    }
                } else {
                    gradientMode = false
                    workingGradient = null
                    selectedGradientStopUUID = null
                }
            }
        }

        var zoomFactor by remember { mutableStateOf(1f) }
        val density = LocalDensity.current
        val basePixelsPerBeatPx = remember(density) { with(density) { 80.dp.toPx() } }
        var viewport by remember {
            val initialZoomX = basePixelsPerBeatPx / beatDurationMs.toFloat()
            mutableStateOf(
                EditorViewportState(
                    zoomX = initialZoomX,
                    minZoomX = 0.75f * initialZoomX,
                    maxZoomX = 12f * initialZoomX,
                )
            )
        }

        LaunchedEffect(beatDurationMs, basePixelsPerBeatPx) {
            val targetZoomX = (basePixelsPerBeatPx * zoomFactor / beatDurationMs.toFloat())
            val minZoomX = 0.75f * basePixelsPerBeatPx / beatDurationMs.toFloat()
            val maxZoomX = 12f * basePixelsPerBeatPx / beatDurationMs.toFloat()
            val tempoAdjustedViewport = viewport.copy(
                minZoomX = minZoomX,
                maxZoomX = maxZoomX,
            )
            viewport = tempoAdjustedViewport.withConstrainedViewport(
                zoomX = targetZoomX.coerceIn(minZoomX, maxZoomX),
                contentWidth = targetZoomX * (entry.durationMs + (entry.durationMs * 0.25).toLong().coerceAtLeast(2000L)),
            )
        }

        val applyViewportChange: (EditorViewportState) -> Unit = { newViewport ->
            viewport = newViewport
            val newZoomFactor = newViewport.zoomX * beatDurationMs.toFloat() / basePixelsPerBeatPx
            zoomFactor = newZoomFactor
            if (!this@PianoRollWorkspaceMode.gridResolutionLocked) {
                val targetRes = GridResolution.fromZoomFactor(newZoomFactor)
                if (targetRes != this@PianoRollWorkspaceMode.gridResolution) {
                    this@PianoRollWorkspaceMode.gridResolution = targetRes
                }
            }
        }

        SideEffect {
            this@PianoRollWorkspaceMode.isPlaying = isPlaying
            if (isPlaying) {
                framePreview.clear()
            }
            zoomNotesHandler = { scaleDelta ->
                applyViewportChange(
                    viewport.copy(
                        zoomY = (viewport.zoomY * scaleDelta).coerceIn(
                            minimumValue = viewport.minZoomY,
                            maximumValue = viewport.maxZoomY,
                        )
                    )
                )
            }
            zoomInHandler = {
                applyViewportChange(
                    zoomPianoRollViewport(
                        viewport = viewport,
                        scaleDelta = 1.25f,
                        anchorPx = viewport.viewportWidth / 2f,
                        contentDurationMs = entry.durationMs + maxOf(2000L, entry.durationMs / 4L),
                    )
                )
            }
            zoomOutHandler = {
                applyViewportChange(
                    zoomPianoRollViewport(
                        viewport = viewport,
                        scaleDelta = 0.8f,
                        anchorPx = viewport.viewportWidth / 2f,
                        contentDurationMs = entry.durationMs + maxOf(2000L, entry.durationMs / 4L),
                    )
                )
            }
            zoomSelectionHandler = {
                val notes = selectedNotes().map { it.note }
                if (notes.isNotEmpty() && viewport.viewportWidth > 0f) {
                    val startMs = notes.minOf { it.startTimeMs }
                    val endMs = notes.maxOf { it.endTimeMs }
                    val paddingPx = with(density) { 24.dp.toPx() }
                    val fittedZoom = ((viewport.viewportWidth - paddingPx * 2f).coerceAtLeast(1f) /
                        (endMs - startMs).coerceAtLeast(1L)).coerceAtMost(viewport.maxZoomX)
                    val fittedViewport = viewport.copy(minZoomX = minOf(viewport.minZoomX, fittedZoom))
                    applyViewportChange(
                        fittedViewport.withConstrainedViewport(
                            zoomX = fittedZoom,
                            scrollX = (startMs * fittedZoom - paddingPx).coerceAtLeast(0f),
                            contentWidth = maxOf(
                                fittedZoom * (entry.durationMs + 2000L),
                                endMs * fittedZoom + viewport.viewportWidth,
                            ),
                        )
                    )
                }
            }
            zoomFitHandler = {
                if (viewport.viewportWidth > 0f) {
                    val fittedZoom = (viewport.viewportWidth / entry.durationMs.coerceAtLeast(1L))
                        .coerceAtLeast(minimumValue = 0.0025f)
                    val fittedViewport = viewport.copy(minZoomX = minOf(viewport.minZoomX, fittedZoom))
                    applyViewportChange(
                        fittedViewport.withConstrainedViewport(
                            zoomX = fittedZoom,
                            scrollX = 0f,
                            contentWidth = fittedZoom * (entry.durationMs + 2000L),
                        )
                    )
                }
            }
        }

        DisposableEffect(Unit) {
            canZoom = true
            onDispose {
                canZoom = false
                this@PianoRollWorkspaceMode.isPlaying = false
                zoomInHandler = null
                zoomOutHandler = null
                zoomFitHandler = null
                zoomSelectionHandler = null
                zoomNotesHandler = null
            }
        }

        LaunchedEffect(this@PianoRollWorkspaceMode.gridResolutionLocked) {
            if (!this@PianoRollWorkspaceMode.gridResolutionLocked) {
                val currentZoomFactor = viewport.zoomX * beatDurationMs.toFloat() / basePixelsPerBeatPx
                val targetRes = GridResolution.fromZoomFactor(currentZoomFactor)
                if (targetRes != this@PianoRollWorkspaceMode.gridResolution) {
                    this@PianoRollWorkspaceMode.gridResolution = targetRes
                }
            }
        }

        val createNotes: (List<MidiNote>) -> TimelineCommandResult = { notes ->
            when {
                notes.isEmpty() -> TimelineCommandResult()
                isTimelineBackedEditing -> {
                    TimelineCommandSurface.createNotes(
                        trackIndex = trackIndex,
                        entryStartMs = entryStartMs,
                        notes = notes
                    ).also { result ->
                        if (result.didChange) {
                            syncCurrentEntry(timelineEntrySnapshot())
                        }
                    }
                }

                else -> {
                    notes.forEach { note ->
                        onNoteAdd?.invoke(note)
                        UndoManager.addAction(
                            UndoableAction.PianoRollNoteCreation(
                                trackIndex = trackIndex,
                                entryStartMs = entryStartMs,
                                note = note,
                                onNoteAdd = { createdNote: MidiNote -> onNoteAdd?.invoke(createdNote) },
                                onNoteDelete = { deletedNote: MidiNote -> onNoteDelete?.invoke(deletedNote) },
                                currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                                currentEntrySetter = { updatedEntry: MidiEntry -> this@PianoRollWorkspaceMode.currentEntry = updatedEntry }
                            )
                        )
                    }
                    currentEntry = currentEntry?.copy(notes = currentEntry?.notes.orEmpty() + notes)
                    TimelineCommandResult(didChange = true)
                }
            }
        }

        val moveNotes: (List<TimelineEditedNote>) -> TimelineCommandResult = { changes ->
            val effectiveChanges = changes.filter { it.before != it.after }
            when {
                effectiveChanges.isEmpty() -> TimelineCommandResult()
                isTimelineBackedEditing -> {
                    TimelineCommandSurface.moveNotes(
                        trackIndex = trackIndex,
                        entryStartMs = entryStartMs,
                        changes = effectiveChanges
                    ).also { result ->
                        if (result.didChange) {
                            syncCurrentEntry(timelineEntrySnapshot())
                        }
                    }
                }

                else -> {
                    UndoManager.addAction(
                        UndoableAction.PianoRollNoteMove(
                            trackIndex = trackIndex,
                            entryStartMs = entryStartMs,
                            notesBefore = effectiveChanges.map(TimelineEditedNote::before),
                            notesAfter = effectiveChanges.map(TimelineEditedNote::after),
                            onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                            currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                            currentEntrySetter = { updatedEntry -> this@PianoRollWorkspaceMode.currentEntry = updatedEntry }
                        )
                    )
                    effectiveChanges.forEach { change ->
                        onNoteUpdate?.invoke(change.before, change.after)
                    }
                    currentEntry = currentEntry?.copy(
                        notes = currentEntry?.notes.orEmpty().map { note ->
                            effectiveChanges.find { it.before.noteId == note.noteId }?.after ?: note
                        }
                    )
                    TimelineCommandResult(didChange = true)
                }
            }
        }

        val resizeNotes: (List<TimelineEditedNote>) -> TimelineCommandResult = { changes ->
            val effectiveChanges = changes.filter { it.before != it.after }
            when {
                effectiveChanges.isEmpty() -> TimelineCommandResult()
                isTimelineBackedEditing -> {
                    TimelineCommandSurface.resizeNotes(
                        trackIndex = trackIndex,
                        entryStartMs = entryStartMs,
                        changes = effectiveChanges
                    ).also { result ->
                        if (result.didChange) {
                            syncCurrentEntry(timelineEntrySnapshot())
                        }
                    }
                }

                else -> {
                    UndoManager.addAction(
                        UndoableAction.PianoRollNoteResize(
                            trackIndex = trackIndex,
                            entryStartMs = entryStartMs,
                            notesBefore = effectiveChanges.map(TimelineEditedNote::before),
                            notesAfter = effectiveChanges.map(TimelineEditedNote::after),
                            onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                            currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                            currentEntrySetter = { updatedEntry -> this@PianoRollWorkspaceMode.currentEntry = updatedEntry }
                        )
                    )
                    effectiveChanges.forEach { change ->
                        onNoteUpdate?.invoke(change.before, change.after)
                    }
                    currentEntry = currentEntry?.copy(
                        notes = currentEntry?.notes.orEmpty().map { note ->
                            effectiveChanges.find { it.before.noteId == note.noteId }?.after ?: note
                        }
                    )
                    TimelineCommandResult(didChange = true)
                }
            }
        }

        val deleteNotes: (List<MidiNote>) -> TimelineCommandResult = { notes ->
            val notesToDelete = notes.distinct()
            when {
                notesToDelete.isEmpty() -> TimelineCommandResult()
                isTimelineBackedEditing -> {
                    TimelineCommandSurface.deleteNotes(
                        trackIndex = trackIndex,
                        entryStartMs = entryStartMs,
                        notes = notesToDelete
                    ).also { result ->
                        if (result.didChange) {
                            syncCurrentEntry(timelineEntrySnapshot())
                        }
                    }
                }

                else -> {
                    UndoManager.addAction(
                        UndoableAction.PianoRollNoteDeletion(
                            trackIndex = trackIndex,
                            entryStartMs = entryStartMs,
                            notes = notesToDelete,
                            onNoteAdd = { note -> onNoteAdd?.invoke(note) },
                            onNoteDelete = { note -> onNoteDelete?.invoke(note) },
                            currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                            currentEntrySetter = { updatedEntry -> this@PianoRollWorkspaceMode.currentEntry = updatedEntry }
                        )
                    )
                    notesToDelete.forEach { note ->
                        onNoteDelete?.invoke(note)
                    }
                    currentEntry = currentEntry?.copy(
                        notes = currentEntry?.notes.orEmpty().filterNot { note ->
                            notesToDelete.any { it.noteId == note.noteId }
                        }
                    )
                    TimelineCommandResult(didChange = true)
                }
            }
        }

        val updateNoteSelections: (List<TimelineEditedNote>) -> Unit = { changes ->
            val beforeToAfter = changes.associate { it.before.noteId to it.after }
            SelectionManager.replaceSelections(
                SelectionManager.selections.value.map { sel ->
                    if (sel is Selectable.PianoRollNote &&
                        sel.entryStartMs == entryStartMs &&
                        sel.trackIndex == trackIndex) {
                        beforeToAfter[sel.note.noteId]?.let { updated -> sel.copy(note = updated) } ?: sel
                    } else sel
                }
            )
        }

        val applyColorToSelection: (Color, Boolean) -> Unit = { newColor, withUndo ->
            selectedColor = newColor
            WorkspaceRepository.addRecentColor(Triple(newColor.red, newColor.green, newColor.blue))

            val selected = SelectionManager.selections.value.filterIsInstance<Selectable.PianoRollNote>()
                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

            if (selected.isNotEmpty()) {
                val noteChanges = selected.map { sel ->
                    TimelineEditedNote(
                        before = sel.note,
                        after = sel.note.copy(
                            led = sel.note.led.copy(
                                red = newColor.red,
                                green = newColor.green,
                                blue = newColor.blue
                            )
                        )
                    )
                }
                val updatedNotes = noteChanges.map(TimelineEditedNote::after)

                updateNoteSelections(noteChanges)
                if (!withUndo) {
                    if (!isTimelineBackedEditing) {
                        noteChanges.forEach { change -> onNoteUpdate?.invoke(change.before, change.after) }
                    }
                    val replacements = noteChanges.associate { it.before.noteId to it.after }
                    currentEntry = currentEntry?.copy(
                        notes = currentEntry?.notes.orEmpty().map { note -> replacements[note.noteId] ?: note }
                    )
                } else if (isTimelineBackedEditing) {
                    TimelineCommandSurface.updateNotes(
                        trackIndex = trackIndex,
                        entryStartMs = entryStartMs,
                        changes = noteChanges
                    ).also { commandResult ->
                        if (commandResult.didChange) {
                            syncCurrentEntry(timelineEntrySnapshot())
                        }
                    }
                } else {
                    noteChanges.forEach { change ->
                        onNoteUpdate?.invoke(change.before, change.after)
                    }
                    UndoManager.addAction(
                        UndoableAction.PianoRollNoteColorChange(
                            trackIndex = trackIndex,
                            entryStartMs = entryStartMs,
                            notesBefore = noteChanges.map(TimelineEditedNote::before),
                            notesAfter = updatedNotes,
                            onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                            currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                            currentEntrySetter = { entry -> this@PianoRollWorkspaceMode.currentEntry = entry }
                        )
                    )
                    val replacements = noteChanges.associate { it.before.noteId to it.after }
                    currentEntry = currentEntry?.copy(
                        notes = currentEntry?.notes?.map { note ->
                            replacements[note.noteId] ?: note
                        } ?: emptyList()
                    )
                }
            }
        }

        val applyNoteChanges: (List<TimelineEditedNote>) -> Unit = { changes ->
            val effectiveChanges = changes.filter { it.before != it.after }
            if (effectiveChanges.isNotEmpty()) {
                updateNoteSelections(effectiveChanges)
                if (isTimelineBackedEditing) {
                    TimelineCommandSurface.updateNotes(
                        trackIndex = trackIndex,
                        entryStartMs = entryStartMs,
                        changes = effectiveChanges
                    ).also { result ->
                        if (result.didChange) {
                            syncCurrentEntry(timelineEntrySnapshot())
                        }
                    }
                } else {
                    val notesBefore = effectiveChanges.map(TimelineEditedNote::before)
                    val notesAfter = effectiveChanges.map(TimelineEditedNote::after)

                    notesBefore.zip(notesAfter).forEach { (before, after) ->
                        onNoteUpdate?.invoke(before, after)
                    }

                    UndoManager.addAction(
                        UndoableAction.PianoRollNoteGradientChange(
                            trackIndex = trackIndex,
                            entryStartMs = entryStartMs,
                            notesBefore = notesBefore,
                            notesAfter = notesAfter,
                            onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                            currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                            currentEntrySetter = { entry -> this@PianoRollWorkspaceMode.currentEntry = entry }
                        )
                    )

                    currentEntry = currentEntry?.copy(
                        notes = currentEntry?.notes?.map { note ->
                            effectiveChanges.find { it.before.noteId == note.noteId }?.after ?: note
                        } ?: emptyList()
                    )
                }
            }
        }

        val applyGradientToNotes: (List<NoteGradientStop>, Boolean) -> Unit = { gradient, withUndo ->
            val normalizedGradient = GradientInterpolator.normalize(gradient)
            val selectedNotes = SelectionManager.selections.value
                .filterIsInstance<Selectable.PianoRollNote>()
                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
            val changes = selectedNotes.map { sel ->
                TimelineEditedNote(
                    before = sel.note,
                    after = sel.note.copy(led = sel.note.led.copy(gradient = normalizedGradient))
                )
            }
            if (withUndo) {
                applyNoteChanges(changes)
            } else {
                val effectiveChanges = changes.filter { it.before != it.after }
                if (effectiveChanges.isNotEmpty()) {
                    updateNoteSelections(effectiveChanges)

                    effectiveChanges.forEach { change ->
                        onNoteUpdate?.invoke(change.before, change.after)
                    }

                    currentEntry = currentEntry?.copy(
                        notes = currentEntry?.notes?.map { note ->
                            effectiveChanges.find { it.before.noteId == note.noteId }?.after ?: note
                        } ?: emptyList()
                    )
                }
            }
        }

        val beginPaintInteraction: () -> Unit = {
            if (paintBeforeInteraction == null) {
                paintBeforeInteraction = selectedNotes().map { it.note }
            }
        }

        val finishPaintInteraction: () -> Unit = {
            val before = paintBeforeInteraction
            if (before != null) {
                val currentById = selectedNotes().associate { it.note.noteId to it.note }
                val changes = before.mapNotNull { old ->
                    currentById[old.noteId]?.let { new -> TimelineEditedNote(old, new) }
                }.filter { it.before != it.after }
                if (changes.isNotEmpty()) applyNoteChanges(changes)
            }
            paintBeforeInteraction = null
        }

        val removeGradientStop: (String) -> Unit = { uuid ->
            val currentGradient = workingGradient
            if (currentGradient != null && currentGradient.size > 2) {
                val deletedPosition = currentGradient.firstOrNull { it.selectionUUID == uuid }?.position ?: 0f
                val updatedGradient = currentGradient.filter { it.selectionUUID != uuid }
                workingGradient = updatedGradient
                if (selectedGradientStopUUID == uuid) {
                    val next = updatedGradient.minByOrNull { kotlin.math.abs(it.position - deletedPosition) }
                    selectedGradientStopUUID = next?.selectionUUID
                    next?.let { selectedColor = Color(it.r, it.g, it.b) }
                }
                applyGradientToNotes(updatedGradient, true)
            }
        }
        deleteSelectedGradientStopHandler = {
            selectedGradientStopUUID?.let(removeGradientStop)
        }
        DisposableEffect(Unit) {
            onDispose { deleteSelectedGradientStopHandler = null }
        }

        val selectedPianoNotes = selections.filterIsInstance<Selectable.PianoRollNote>()
            .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

        LaunchedEffect(selectedPianoNotes, isPlaying) {
            notePreview.clear()
            if (!isPlaying) {
                selectedPianoNotes.map { it.note }
                    .sortedBy { it.startTimeMs }
                    .distinctBy { it.resolvedDeviceIndex to it.resolvedPadIndex }
                    .forEach { previewNote(note = it) }
            }
        }

        LaunchedEffect(selectedColor, workingGradient, gradientMode) {
            pressedKeysState.value.filterValues { it }.keys.forEach { (deviceIndex, padIndex) ->
                previewPad(
                    deviceIndex = deviceIndex,
                    padIndex = padIndex,
                    color = selectedColor,
                    gradient = workingGradient.takeIf { gradientMode },
                    durationMs = selectedPianoNotes.firstOrNull()?.note?.durationMs
                        ?: currentCellDurationMs(gridResolution, editorBpm),
                    repeat = true,
                )
            }
        }

        val applyTransform: ((List<MidiNote>) -> List<MidiNote>) -> Unit = { transformFn ->
            val selected = SelectionManager.selections.value
                .filterIsInstance<Selectable.PianoRollNote>()
                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
            if (selected.isNotEmpty()) {
                val notesBefore = selected.map { it.note }
                val notesAfter = transformFn(notesBefore)
                val noteChanges = notesBefore.zip(notesAfter).map { (before, after) ->
                    TimelineEditedNote(before = before, after = after)
                }
                val effectiveChanges = noteChanges.filter { it.before != it.after }
                if (effectiveChanges.isNotEmpty()) {
                    updateNoteSelections(noteChanges)
                    if (isTimelineBackedEditing) {
                        TimelineCommandSurface.updateNotes(
                            trackIndex = trackIndex,
                            entryStartMs = entryStartMs,
                            changes = effectiveChanges
                        ).also { commandResult ->
                            if (commandResult.didChange) syncCurrentEntry(timelineEntrySnapshot())
                        }
                    } else {
                        effectiveChanges.forEach { change ->
                            onNoteUpdate?.invoke(change.before, change.after)
                        }

                        UndoManager.addAction(
                            UndoableAction.PianoRollNoteTransform(
                                trackIndex = trackIndex,
                                entryStartMs = entryStartMs,
                                notesBefore = effectiveChanges.map(TimelineEditedNote::before),
                                notesAfter = effectiveChanges.map(TimelineEditedNote::after),
                                onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                                currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                                currentEntrySetter = { entry -> this@PianoRollWorkspaceMode.currentEntry = entry }
                            )
                        )

                        val replacements = effectiveChanges.associate { it.before.noteId to it.after }
                        currentEntry = currentEntry?.copy(
                            notes = currentEntry?.notes?.map { note -> replacements[note.noteId] ?: note } ?: emptyList()
                        )
                    }
                }
            }
        }

        Column(modifier = modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                PianoRollInspectorSidebar(
                    gradientMode = gradientMode,
                    selectedColor = selectedColor,
                    onSolidColorChange = { color ->
                        applyColorToSelection(color, paintBeforeInteraction == null)
                    },
                    onGradientStopColorChange = { color ->
                        val stopId = selectedGradientStopUUID
                        val currentGradient = workingGradient
                        if (stopId != null && currentGradient != null) {
                            selectedColor = color
                            val updatedGradient = currentGradient.map { stop ->
                                if (stop.selectionUUID == stopId) {
                                    stop.copy(r = color.red, g = color.green, b = color.blue)
                                } else {
                                    stop
                                }
                            }
                            workingGradient = updatedGradient
                            applyGradientToNotes(updatedGradient, paintBeforeInteraction == null)
                        }
                    },
                    onColorInteractionStart = beginPaintInteraction,
                    onColorInteractionFinish = finishPaintInteraction,
                    workingGradient = workingGradient,
                    selectedGradientStopUUID = selectedGradientStopUUID,
                    onSelectGradientStop = { uuid ->
                        selectedGradientStopUUID = uuid
                        workingGradient?.firstOrNull { it.selectionUUID == uuid }?.let { stop ->
                            selectedColor = Color(stop.r, stop.g, stop.b)
                        }
                    },
                    onStopMoved = { uuid, newPos ->
                        val currentGrad = workingGradient ?: emptyList()
                        val updatedGradient = currentGrad.map { s ->
                            if (s.selectionUUID == uuid) s.copy(position = newPos) else s
                        }
                        workingGradient = updatedGradient
                        applyGradientToNotes(updatedGradient, false)
                    },
                    onAddStop = { position ->
                        val currentGrad = workingGradient ?: emptyList()
                        val (r, g, b) = GradientInterpolator.interpolate(currentGrad, position)
                        val newStop = NoteGradientStop(position, r, g, b)
                        val updatedGradient = (currentGrad + newStop).sortedBy { it.position }
                        workingGradient = updatedGradient
                        selectedGradientStopUUID = newStop.selectionUUID
                        selectedColor = Color(r, g, b)
                        applyGradientToNotes(updatedGradient, true)
                    },
                    onDeleteStop = { uuid ->
                        removeGradientStop(uuid)
                    },
                    onSmoothnessChange = { uuid, smoothness ->
                        val currentGrad = workingGradient ?: emptyList()
                        val updatedGradient = currentGrad.map { s ->
                            if (s.selectionUUID == uuid) s.copy(smoothness = smoothness) else s
                        }
                        workingGradient = updatedGradient
                        applyGradientToNotes(updatedGradient, true)
                    },
                    onGradientDragStart = {
                        gradientBeforeDrag = workingGradient
                    },
                    onGradientDragFinish = {
                        val before = gradientBeforeDrag
                        val after = workingGradient
                        if (before != null && after != null && before != after) {
                            val selectedNotes = SelectionManager.selections.value
                                .filterIsInstance<Selectable.PianoRollNote>()
                                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                            val changes = selectedNotes.map { sel ->
                                TimelineEditedNote(
                                    before = sel.note.copy(led = sel.note.led.copy(gradient = before)),
                                    after = sel.note
                                )
                            }
                            applyNoteChanges(changes)
                        }
                        gradientBeforeDrag = null
                    },
                    onSolidTabSelected = {
                        if (gradientMode) {
                            val selectedNotes = SelectionManager.selections.value
                                .filterIsInstance<Selectable.PianoRollNote>()
                                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                            if (selectedNotes.isNotEmpty()) {
                                val changes = selectedNotes.map { sel ->
                                    val solidColor = if (sel.note.isGradient) {
                                        val (r, g, b) = GradientInterpolator.interpolate(sel.note.led.gradient!!, 0f)
                                        Triple(r, g, b)
                                    } else Triple(sel.note.led.red, sel.note.led.green, sel.note.led.blue)
                                    TimelineEditedNote(
                                        before = sel.note,
                                        after = sel.note.copy(led = sel.note.led.copy(
                                            red = solidColor.first,
                                            green = solidColor.second,
                                            blue = solidColor.third,
                                            gradient = null
                                        ))
                                    )
                                }
                                applyNoteChanges(changes)
                            }
                            gradientMode = false
                            workingGradient = null
                            selectedGradientStopUUID = null
                        }
                    },
                    onGradientTabSelected = {
                        val selectedNotes = SelectionManager.selections.value
                            .filterIsInstance<Selectable.PianoRollNote>()
                            .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                        run {
                            val referenceGradient = selectedNotes
                                .firstNotNullOfOrNull { it.note.led.gradient?.takeIf { stops -> stops.size >= 2 } }
                                ?: workingGradient?.takeIf { it.size >= 2 }
                                ?: listOf(
                                    NoteGradientStop(
                                        position = 0f,
                                        r = selectedColor.red,
                                        g = selectedColor.green,
                                        b = selectedColor.blue,
                                    ),
                                    NoteGradientStop(position = 1f, r = 0f, g = 0f, b = 0f),
                                )
                            val normalizedGradient = GradientInterpolator.normalize(referenceGradient)
                            workingGradient = normalizedGradient
                            selectedGradientStopUUID = normalizedGradient.firstOrNull()?.selectionUUID
                            normalizedGradient.firstOrNull()?.let { stop ->
                                selectedColor = Color(stop.r, stop.g, stop.b)
                            }
                            gradientMode = true
                            applyGradientToNotes(normalizedGradient, true)
                        }
                    },
                    enabled = selectedPianoNotes.isNotEmpty(),
                    selectionCount = selectedPianoNotes.size,
                    hasMultipleSelection = selectedPianoNotes.size >= 2,
                    onApplyTransform = applyTransform,
                    onGradientSpread = {
                        val sel = SelectionManager.selections.value
                            .filterIsInstance<Selectable.PianoRollNote>()
                            .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                        if (sel.size >= 2) {
                            val sorted = sel.sortedBy { it.note.startTimeMs }
                            val first = sorted.first()
                            val last = sorted.last()
                            val stops = listOf(
                                NoteGradientStop(0f, first.note.led.red, first.note.led.green, first.note.led.blue),
                                NoteGradientStop(1f, last.note.led.red, last.note.led.green, last.note.led.blue)
                            )
                            val changes = sorted.mapIndexed { i, selectable ->
                                val t = i.toFloat() / (sorted.size - 1).toFloat()
                                val (r, g, b) = GradientInterpolator.interpolate(stops, t)
                                TimelineEditedNote(
                                    before = selectable.note,
                                    after = selectable.note.copy(led = selectable.note.led.copy(red = r, green = g, blue = b, gradient = null))
                                )
                            }
                            applyNoteChanges(changes)
                        }
                    },
                    onRandomizeColors = {
                        val sel = SelectionManager.selections.value
                            .filterIsInstance<Selectable.PianoRollNote>()
                            .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                        val colorPool = WorkspaceRepository.recentColors.value.ifEmpty {
                            listOf(
                                Triple(1f, 0f, 0f), Triple(0f, 1f, 0f), Triple(0f, 0f, 1f),
                                Triple(1f, 1f, 0f), Triple(0f, 1f, 1f), Triple(1f, 0f, 1f)
                            )
                        }
                        val changes = sel.map { selectable ->
                            val (r, g, b) = colorPool.random()
                            TimelineEditedNote(
                                before = selectable.note,
                                after = selectable.note.copy(led = selectable.note.led.copy(red = r, green = g, blue = b, gradient = null))
                            )
                        }
                        if (changes.isNotEmpty()) applyNoteChanges(changes)
                    }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    WorkspaceViewport(
                        modifier = Modifier.weight(1f),
                        viewportKey = "workspace-pianoroll",
                        config = ViewportConfig(
                            minZoom = 0.5f,
                            maxZoom = 2f,
                            enablePanning = true,
                            enableZoom = true,
                            draggableObjects = false,
                            panBoundsPolicy = ViewportPanBoundsPolicy.ClampToContent(
                                allowedOutOfBoundsFraction = 0.5f,
                            ),
                            showGrid = false,
                            showOrigin = false,
                            showActions = false,
                            showRemoteCursors = true,
                            contentPadding = 24.dp
                        ),
                    )

                    var notesPanelHeight by remember { mutableStateOf(350.dp) }
                    val minHeight = 250.dp

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(notesPanelHeight)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dragAmountDp = with(density) { dragAmount.y.toDp() }
                                        notesPanelHeight = (notesPanelHeight - dragAmountDp).coerceAtLeast(minHeight)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Theme[colors][border])
                            )
                        }

                        Separator()

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            PianoRollEditorCanvas(
                                entry = entry,
                                launchpads = launchpads,
                                trackIndex = trackIndex,
                                entryStartMs = entryStartMs,
                                multiSelectModifierDown = windowInfo.keyboardModifiers.isMetaPressed ||
                                    windowInfo.keyboardModifiers.isCtrlPressed,
                                shiftModifierDown = windowInfo.keyboardModifiers.isShiftPressed,
                                selectedColor = selectedColor,
                                gradientMode = gradientMode,
                                workingGradient = workingGradient,
                                activeTool = this@PianoRollWorkspaceMode.activeTool,
                                onCreateNotes = createNotes,
                                onMoveNotes = moveNotes,
                                onResizeNotes = resizeNotes,
                                onDeleteNotes = deleteNotes,
                                viewport = viewport,
                                onViewportChange = applyViewportChange,
                                gridResolution = this@PianoRollWorkspaceMode.gridResolution,
                                snapEnabled = snapEnabled,
                                onPreviewNote = { previewNote(note = it) },
                                bpm = editorBpm,
                                pressedKeysState = this@PianoRollWorkspaceMode.pressedKeysState,
                                selectedTimeMs = this@PianoRollWorkspaceMode.selectedTimeMs,
                                playheadPositionMs = playheadPositionMs,
                                isPlaying = isPlaying,
                                foldPads = foldPads,
                                onSelectedTimeMsChange = { selectedTimeMs = it }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (WorkspaceRepository.isInputFocused) {
            return false
        }

        if (event.type == KeyEventType.KeyDown) {
            val isMetaOrCtrl = event.isMetaPressed || event.isCtrlPressed
            if (event.key == Key.Tab && event.isShiftPressed && !isMetaOrCtrl && !event.isAltPressed) {
                requestClose()
                return true
            }

            if (isMetaOrCtrl) {
                when (event.key) {
                    Key.A -> return selectAllNotes()
                    Key.D -> return duplicateSelectedNotes()
                    Key.U -> return nudgeSelectedNotes(quantize = true)
                    Key.One -> return stepGrid(narrower = true)
                    Key.Two -> return stepGrid(narrower = false)
                    Key.Four -> {
                        snapEnabled = !snapEnabled
                        return true
                    }
                    Key.Five -> {
                        gridResolutionLocked = !gridResolutionLocked
                        return true
                    }
                    Key.Plus, Key.Equals, Key.NumPadAdd -> {
                        zoomIn()
                        return canZoom
                    }
                    Key.Minus, Key.NumPadSubtract -> {
                        zoomOut()
                        return canZoom
                    }
                    Key.Z -> {
                        if (event.isShiftPressed) {
                            UndoManager.redo()
                        } else {
                            UndoManager.undo()
                        }
                        return true
                    }
                    Key.Y -> {
                        UndoManager.redo()
                        return true
                    }
                }
            } else {
                if (event.isAltPressed) {
                    return false
                }
                when (event.key) {
                    Key.Z -> {
                        zoomSelectionHandler?.invoke()
                        return canZoom
                    }
                    Key.X -> {
                        zoomToFit()
                        return canZoom
                    }
                    Key.Plus, Key.Equals, Key.NumPadAdd -> {
                        zoomIn()
                        return canZoom
                    }
                    Key.Minus, Key.NumPadSubtract -> {
                        zoomOut()
                        return canZoom
                    }
                    Key.Delete, Key.Backspace -> {
                        if (selectedGradientStopUUID != null) {
                            deleteSelectedGradientStopHandler?.invoke()
                            return true
                        }
                        return deleteSelectedNotes()
                    }
                    Key.Spacebar -> {
                        togglePlayback()
                        return true
                    }
                    Key.B -> {
                        activeTool = if (activeTool == TimelineEditorTool.DRAW) {
                            TimelineEditorTool.NORMAL
                        } else {
                            TimelineEditorTool.DRAW
                        }
                        return true
                    }
                    Key.DirectionLeft -> {
                        return stepHorizontalCursor(direction = -1, resizeSelection = event.isShiftPressed)
                    }
                    Key.DirectionRight -> {
                        return stepHorizontalCursor(direction = 1, resizeSelection = event.isShiftPressed)
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (selectedNotes().isEmpty()) {
                            return false
                        }
                        nudgeSelectedNotes(pitchDirection = if (event.key == Key.DirectionUp) 1 else -1)
                        return true
                    }
                    Key.Escape -> {
                        if (activeTool == TimelineEditorTool.DRAW) {
                            activeTool = TimelineEditorTool.NORMAL
                            return true
                        }
                        if (selectedNotes().isNotEmpty()) {
                            SelectionManager.clear()
                            return true
                        }
                        requestClose()
                        return true
                    }
                }
            }
        }
        return false
    }

    internal fun stepGrid(narrower: Boolean): Boolean {
        val resolutions = GridResolution.entries
        val direction = if (narrower) 1 else -1
        gridResolution = resolutions.getOrNull(resolutions.indexOf(gridResolution) + direction) ?: gridResolution
        gridResolutionLocked = true
        return true
    }

    private fun previewNote(note: MidiNote) {
        if (isPlaying) {
            return
        }
        framePreview.clear()
        previewPad(
            deviceIndex = note.resolvedDeviceIndex,
            padIndex = note.resolvedPadIndex,
            color = Color(red = note.led.red, green = note.led.green, blue = note.led.blue),
            gradient = note.led.gradient,
            durationMs = note.durationMs.coerceAtLeast(120L),
            repeat = false,
        )
    }

    private fun previewPad(
        deviceIndex: Int,
        padIndex: Int,
        color: Color,
        gradient: List<NoteGradientStop>?,
        durationMs: Long,
        repeat: Boolean,
    ) {
        val device = Heaven.devices.getOrNull(deviceIndex) ?: return
        val (x, y) = device.globalPadForMidiIndex(index = padIndex) ?: return
        val preview = if (repeat) padPreview else notePreview
        preview.press(
            key = deviceIndex to padIndex,
            signal = Signal.LED(
                origin = this,
                x = x,
                y = y,
                color = color,
            ),
            gradient = gradient,
            durationMs = durationMs,
            repeat = repeat,
        )
    }

    private fun previewFrame(timeMs: Long) {
        framePreview.clear()
        notePreview.clear()
        if (isPlaying) {
            return
        }
        val entry = currentEntry ?: return
        pianoRollFrameColors(notes = entry.notes, timeMs = timeMs, clipDurationMs = entry.durationMs)
            .forEach { (key, color) ->
                val device = Heaven.devices.getOrNull(index = key.first) ?: return@forEach
                val position = device.globalPadForMidiIndex(index = key.second) ?: return@forEach
                framePreview.showSnapshot(
                    key = key,
                    signal = Signal.LED(
                        origin = this,
                        x = position.first,
                        y = position.second,
                        color = color,
                    ),
                )
            }
    }

    private fun requestClose() {
        WorkspaceRepository.switchToPreviousMode()
    }

    internal fun stepHorizontalCursor(direction: Int, resizeSelection: Boolean = false): Boolean {
        if (resizeSelection) {
            return nudgeSelectedNotes(resizeDirection = direction)
        }
        if (pressedKeysState.value.any { it.value }) {
            return nudgePlayhead(direction = direction, createHeldNotes = true)
        }
        if (selectedNotes().isNotEmpty()) {
            nudgeSelectedNotes(timeDirection = direction)
            return true
        }
        return nudgePlayhead(direction = direction)
    }

    private fun nudgePlayhead(direction: Int, createHeldNotes: Boolean = false): Boolean {
        val entry = currentEntry ?: return false
        val currentMs = (selectedTimeMs ?: 0L).coerceIn(0L, entry.durationMs)
        val nextMs = stepClipTimeOnGrid(
            currentMs,
            gridResolution,
            direction,
            millisecondsPerBeat(currentBpm()),
        )
            .coerceIn(0L, entry.durationMs)
        selectedTimeMs = nextMs
        if (!createHeldNotes) {
            previewFrame(timeMs = nextMs)
        }
        if (createHeldNotes && nextMs != currentMs) {
            stepHeldPadNotes(currentMs = currentMs, nextMs = nextMs)
        }
        return true
    }

    private fun stepHeldPadNotes(currentMs: Long, nextMs: Long) {
        val entry = (if (isTimelineBackedEditing) timelineEntrySnapshot() else currentEntry) ?: return
        val notesAfter = stepPianoRollPadNotes(
            notes = entry.notes,
            pads = pressedKeysState.value.filterValues { it }.keys.toList(),
            currentMs = currentMs,
            nextMs = nextMs,
            color = selectedColor,
            gradient = workingGradient.takeIf { gradientMode },
        )
        if (notesAfter == entry.notes) {
            return
        }
        if (isTimelineBackedEditing) {
            val result = TimelineCommandSurface.replaceNotes(
                trackIndex = trackIndex,
                entryStartMs = entryStartMs,
                notes = notesAfter,
            )
            if (result.didChange) {
                syncCurrentEntry(entry = timelineEntrySnapshot())
            }
        } else {
            applyLegacyPadStepNotes(notes = notesAfter)
            UndoManager.addAction(
                UndoableAction.PianoRollNoteStep(
                    notesBefore = entry.notes,
                    notesAfter = notesAfter,
                    applyNotes = { applyLegacyPadStepNotes(notes = it) },
                )
            )
        }
    }

    private fun applyLegacyPadStepNotes(notes: List<MidiNote>) {
        val entry = currentEntry ?: return
        val before = entry.notes.associateBy { it.noteId }
        val after = notes.associateBy { it.noteId }
        entry.notes.filter { it.noteId !in after }.forEach { onNoteDelete?.invoke(it) }
        notes.forEach { note ->
            val original = before[note.noteId]
            if (original == null) {
                onNoteAdd?.invoke(note)
            } else if (original != note) {
                onNoteUpdate?.invoke(original, note)
            }
        }
        currentEntry = entry.copy(notes = notes)
    }

    private fun createPadNotes(pads: List<Pair<Int, Int>>, startTimeMs: Long, durationMs: Long) {
        val entry = currentEntry ?: return
        val newNotes = pads.distinct().filter { (deviceIndex, pitch) ->
            entry.notes.none {
                it.resolvedDeviceIndex == deviceIndex && it.resolvedPadIndex == pitch &&
                    it.startTimeMs == startTimeMs && it.durationMs == durationMs
            }
        }.map { (deviceIndex, pitch) ->
            MidiNote.withPaint(
                device = deviceIndex,
                pitch = pitch,
                color = selectedColor,
                startTimeMs = startTimeMs,
                durationMs = durationMs,
                gradient = workingGradient.takeIf { gradientMode },
            )
        }
        if (newNotes.isEmpty()) {
            return
        }
        if (isTimelineBackedEditing) {
            val result = TimelineCommandSurface.createNotes(
                trackIndex = trackIndex,
                entryStartMs = entryStartMs,
                notes = newNotes,
            )
            if (result.didChange) {
                syncCurrentEntry(entry = timelineEntrySnapshot())
            }
        } else {
            newNotes.forEach { onNoteAdd?.invoke(it) }
            UndoManager.addAction(
                UndoableAction.PianoRollNoteMultiCreation(
                    trackIndex = trackIndex,
                    entryStartMs = entryStartMs,
                    notes = newNotes,
                    onNoteAdd = { onNoteAdd?.invoke(it) },
                    onNoteDelete = { onNoteDelete?.invoke(it) },
                    currentEntryGetter = { currentEntry },
                    currentEntrySetter = { currentEntry = it },
                )
            )
            currentEntry = entry.copy(notes = entry.notes + newNotes)
        }
    }

    override fun onMidiInput(data: MidiInputData, offset: androidx.compose.ui.geometry.Offset) {
        if (currentEntry == null) {
            return
        }
        val isPressed = data.velocity > 0
        val deviceIndex = Heaven.devices.indexOfFirst { device ->
            device.position.value.x - device.layout.offsetX == offset.x &&
                (device.position.value.y == offset.y || device.position.value.y - device.layout.offsetY == offset.y)
        }
        if (deviceIndex !in Heaven.devices.indices) {
            return
        }
        val pitch = data.pitch
        if (pitch !in 0..99) {
            return
        }
        val key = deviceIndex to pitch
        val wasPressed = pressedKeysState.value[key] == true
        if (isPressed && !wasPressed) {
            val gradient = workingGradient.takeIf { gradientMode }
            val durationMs = selectedNotes().firstOrNull()?.note?.durationMs
                ?: currentCellDurationMs(gridResolution, currentBpm())
            previewPad(
                deviceIndex = deviceIndex,
                padIndex = pitch,
                color = selectedColor,
                gradient = gradient,
                durationMs = durationMs,
                repeat = true,
            )
        } else if (!isPressed) {
            padPreview.release(key = key)
        }

        pressedKeysState.update { current ->
            if (isPressed) {
                current + (key to true)
            } else {
                current - key
            }
        }

        if (isPressed && !wasPressed && activeTool == TimelineEditorTool.DRAW) {
            createPadNotes(
                pads = listOf(key),
                startTimeMs = selectedTimeMs ?: 0L,
                durationMs = currentCellDurationMs(gridResolution, currentBpm()),
            )
        }
    }
}

private fun currentCellDurationMs(currentResolution: GridResolution, bpm: Double): Long =
    (millisecondsPerBeat(bpm) / currentResolution.snapDivisionsPerBeat).toLong().coerceAtLeast(1L)
