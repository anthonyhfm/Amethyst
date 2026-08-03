package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
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
import dev.anthonyhfm.amethyst.timeline.migration.LegacyPianoRollPath
import dev.anthonyhfm.amethyst.timeline.migration.PianoRollCutoverSupport
import dev.anthonyhfm.amethyst.timeline.ui.pianoroll.PianoRollEditorCanvas
import dev.anthonyhfm.amethyst.timeline.ui.pianoroll.PianoRollInspectorSidebar
import dev.anthonyhfm.amethyst.timeline.ui.pianoroll.PianoRollToolbar
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class PianoRollWorkspaceMode : WorkspaceMode() {
    override val displayName: String = "Piano Roll"
    override val selectableMode: Boolean = false
    override val claimMidiInputs: Boolean = true

    var activeTool by mutableStateOf(TimelineEditorTool.SELECT)
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

    val pressedKeysState = MutableStateFlow<Map<Pair<Int, Int>, Boolean>>(emptyMap())

    var selectedColor by mutableStateOf(Color(0xFFFF6B35))
    var gradientMode by mutableStateOf(false)
    var workingGradient by mutableStateOf<List<NoteGradientStop>?>(null)
    var selectedTimeMs by mutableStateOf<Long?>(null)
    var gridResolution by mutableStateOf(GridResolution.Quarter)
    var gridResolutionLocked by mutableStateOf(false)

    var multiSelectModifierDown by mutableStateOf(false)

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

    private fun handleTogglePlayPause() {
        if (clipContext != null) {
            if (TimelineRepository.isPlaying.value) {
                TimelineRepository.pause()
            } else {
                TimelineRepository.setPlayheadPosition(entryStartMs)
                TimelineRepository.play()
            }
        } else {
            onPlaybackToggle?.invoke()
        }
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
            sel.note.copy(startTimeMs = sel.note.startTimeMs + offset)
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
                notes = currentEntry?.notes.orEmpty().filter { note -> note !in notesToDelete }
            )
            TimelineCommandResult(didChange = true)
        }

        if (!result.didChange) return false

        SelectionManager.clear()
        return true
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
                led = note.led.copy(index = note.pitch)
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
        val launchpads = Heaven.devices
        val selections by SelectionManager.selections.collectAsState()
        val playheadPositionMs by TimelineRepository.playheadPositionMs.collectAsState()

        var selectedGradientStopUUID by remember { mutableStateOf<String?>(null) }
        var gradientBeforeDrag by remember { mutableStateOf<List<NoteGradientStop>?>(null) }

        val selectedNoteIdentities = remember(selections) {
            selections.filterIsInstance<Selectable.PianoRollNote>()
                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                .map { it.note.startTimeMs to it.note.pitch }
                .toSet()
        }

        LaunchedEffect(selectedNoteIdentities) {
            val selectedNotes = SelectionManager.selections.value
                .filterIsInstance<Selectable.PianoRollNote>()
                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

            if (selectedNotes.size == 1) {
                val note = selectedNotes.first().note
                selectedColor = Color(note.led.red, note.led.green, note.led.blue)
                gradientMode = note.isGradient
                workingGradient = note.led.gradient
            } else if (selectedNotes.size > 1) {
                val allAreGradient = selectedNotes.all { it.note.isGradient }

                if (allAreGradient) {
                    val referenceGradient = selectedNotes.first().note.led.gradient
                    gradientMode = true
                    workingGradient = referenceGradient

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
            } else if (selectedNotes.isEmpty()) {
                gradientMode = false
                workingGradient = null
                selectedGradientStopUUID = null
            }
        }

        var zoomFactor by remember { mutableStateOf(1f) }
        val density = LocalDensity.current
        val basePixelsPerBeatPx = remember(density) { with(density) { 80.dp.toPx() } }
        var viewport by remember {
            val initialZoomX = basePixelsPerBeatPx / MS_PER_BEAT.toFloat()
            mutableStateOf(
                EditorViewportState(
                    zoomX = initialZoomX,
                    minZoomX = 0.75f * initialZoomX,
                    maxZoomX = 12f * initialZoomX,
                )
            )
        }

        LaunchedEffect(this@PianoRollWorkspaceMode.gridResolutionLocked) {
            if (!this@PianoRollWorkspaceMode.gridResolutionLocked) {
                val currentZoomFactor = viewport.zoomX * MS_PER_BEAT.toFloat() / basePixelsPerBeatPx
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
                            effectiveChanges.find { it.before.startTimeMs == note.startTimeMs && it.before.pitch == note.pitch }?.after ?: note
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
                            effectiveChanges.find { it.before.startTimeMs == note.startTimeMs && it.before.pitch == note.pitch }?.after ?: note
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
                        notes = currentEntry?.notes.orEmpty().filter { note -> note !in notesToDelete }
                    )
                    TimelineCommandResult(didChange = true)
                }
            }
        }

        val updateNoteSelections: (List<TimelineEditedNote>) -> Unit = { changes ->
            val beforeToAfter = changes.associate { it.before to it.after }
            SelectionManager.replaceSelections(
                SelectionManager.selections.value.map { sel ->
                    if (sel is Selectable.PianoRollNote &&
                        sel.entryStartMs == entryStartMs &&
                        sel.trackIndex == trackIndex) {
                        beforeToAfter[sel.note]?.let { updated -> sel.copy(note = updated) } ?: sel
                    } else sel
                }
            )
        }

        val applyColorToSelection: (Color) -> Unit = { newColor ->
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
                if (isTimelineBackedEditing) {
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
                    val replacements = noteChanges.associate { it.before to it.after }
                    currentEntry = currentEntry?.copy(
                        notes = currentEntry?.notes?.map { note ->
                            replacements[note] ?: note
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
                            effectiveChanges.find { it.before.startTimeMs == note.startTimeMs && it.before.pitch == note.pitch }?.after ?: note
                        } ?: emptyList()
                    )
                }
            }
        }

        val applyGradientToNotes: (List<NoteGradientStop>, Boolean) -> Unit = { gradient, withUndo ->
            val selectedNotes = SelectionManager.selections.value
                .filterIsInstance<Selectable.PianoRollNote>()
                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
            val changes = selectedNotes.map { sel ->
                TimelineEditedNote(
                    before = sel.note,
                    after = sel.note.copy(led = sel.note.led.copy(gradient = gradient))
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
                            effectiveChanges.find { it.before.startTimeMs == note.startTimeMs && it.before.pitch == note.pitch }?.after ?: note
                        } ?: emptyList()
                    )
                }
            }
        }

        val selectedPianoNotes = selections.filterIsInstance<Selectable.PianoRollNote>()
            .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

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

                        val replacements = effectiveChanges.associate { it.before to it.after }
                        currentEntry = currentEntry?.copy(
                            notes = currentEntry?.notes?.map { note -> replacements[note] ?: note } ?: emptyList()
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
                    onColorChange = applyColorToSelection,
                    workingGradient = workingGradient,
                    selectedGradientStopUUID = selectedGradientStopUUID,
                    onSelectGradientStop = { selectedGradientStopUUID = it },
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
                        val currentGrad = workingGradient ?: emptyList()
                        if (currentGrad.size > 2) {
                            val updatedGradient = currentGrad.filter { it.selectionUUID != uuid }
                            workingGradient = updatedGradient
                            applyGradientToNotes(updatedGradient, true)
                        }
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
                        }
                    },
                    onGradientTabSelected = {
                        if (!gradientMode) {
                            val selectedNotes = SelectionManager.selections.value
                                .filterIsInstance<Selectable.PianoRollNote>()
                                .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                            if (selectedNotes.isNotEmpty()) {
                                val solidNotes = selectedNotes.filter { !it.note.isGradient }
                                if (solidNotes.isNotEmpty()) {
                                    val changes = solidNotes.map { sel ->
                                        val twoStopGradient = listOf(
                                            NoteGradientStop(0f, sel.note.led.red, sel.note.led.green, sel.note.led.blue),
                                            NoteGradientStop(1f, 0f, 0f, 0f)
                                        )
                                        TimelineEditedNote(
                                            before = sel.note,
                                            after = sel.note.copy(led = sel.note.led.copy(gradient = twoStopGradient))
                                        )
                                    }
                                    applyNoteChanges(changes)
                                }

                                val firstGradientNote = selectedNotes.firstOrNull { it.note.isGradient }
                                    ?: selectedNotes.firstOrNull()
                                workingGradient = firstGradientNote?.note?.led?.gradient
                            }
                            gradientMode = true
                        }
                    },
                    enabled = selectedPianoNotes.isNotEmpty(),
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
                        val colorPool = listOf(
                            Triple(1f, 0f, 0f), Triple(0f, 1f, 0f), Triple(0f, 0f, 1f),
                            Triple(1f, 1f, 0f), Triple(0f, 1f, 1f), Triple(1f, 0f, 1f)
                        )
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

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(bottom = 12.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    var notesPanelHeight by remember { mutableStateOf(350.dp) }
                    val minHeight = 250.dp

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(notesPanelHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Theme[colors][background].copy(alpha = 0.95f))
                            .border(1.dp, Theme[colors][border], RoundedCornerShape(12.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
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

                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                            PianoRollEditorCanvas(
                                entry = entry,
                                launchpads = launchpads,
                                trackIndex = trackIndex,
                                entryStartMs = entryStartMs,
                                multiSelectModifierDown = multiSelectModifierDown,
                                shiftModifierDown = ModifierKeysState.isShiftPressed,
                                selectedColor = selectedColor,
                                gradientMode = gradientMode,
                                workingGradient = workingGradient,
                                activeTool = this@PianoRollWorkspaceMode.activeTool,
                                onCreateNotes = createNotes,
                                onMoveNotes = moveNotes,
                                onResizeNotes = resizeNotes,
                                onDeleteNotes = deleteNotes,
                                viewport = viewport,
                                onViewportChange = { newViewport ->
                                    viewport = newViewport
                                    val newZoomFactor = newViewport.zoomX * MS_PER_BEAT.toFloat() / basePixelsPerBeatPx
                                    zoomFactor = newZoomFactor
                                    if (!this@PianoRollWorkspaceMode.gridResolutionLocked) {
                                        val targetRes = GridResolution.fromZoomFactor(newZoomFactor)
                                        if (targetRes != this@PianoRollWorkspaceMode.gridResolution) {
                                            this@PianoRollWorkspaceMode.gridResolution = targetRes
                                        }
                                    }
                                },
                                gridResolution = this@PianoRollWorkspaceMode.gridResolution,
                                currentBpm = ::currentBpm,
                                pressedKeysState = this@PianoRollWorkspaceMode.pressedKeysState,
                                selectedTimeMs = this@PianoRollWorkspaceMode.selectedTimeMs,
                                playheadPositionMs = if (this@PianoRollWorkspaceMode.clipContext != null) playheadPositionMs - entryStartMs else null,
                                onSelectedTimeMsChange = { selectedTimeMs = it }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (WorkspaceRepository.isInputFocused) return false

        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.ShiftLeft, Key.ShiftRight -> multiSelectModifierDown = true
            }
        } else if (event.type == KeyEventType.KeyUp) {
            when (event.key) {
                Key.ShiftLeft, Key.ShiftRight -> multiSelectModifierDown = false
            }
        }

        if (event.type == KeyEventType.KeyDown) {
            val isMetaOrCtrl = event.isMetaPressed || event.isCtrlPressed

            if (isMetaOrCtrl) {
                when (event.key) {
                    Key.A -> return selectAllNotes()
                    Key.D -> return duplicateSelectedNotes()
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
                when (event.key) {
                    Key.Delete, Key.Backspace -> return deleteSelectedNotes()
                    Key.Spacebar -> {
                        handleTogglePlayPause()
                        return true
                    }
                    Key.V -> {
                        activeTool = TimelineEditorTool.SELECT
                        return true
                    }
                    Key.B -> {
                        activeTool = TimelineEditorTool.DRAW
                        return true
                    }
                    Key.E -> {
                        activeTool = TimelineEditorTool.ERASE
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onMidiInput(data: MidiInputData, offset: androidx.compose.ui.geometry.Offset) {
        val entry = currentEntry ?: return
        val isPressed = data.velocity > 0

        pressedKeysState.update { current ->
            val key = Pair(0, data.pitch)
            if (isPressed) {
                current + (key to true)
            } else {
                current - key
            }
        }

        if (isPressed && activeTool == TimelineEditorTool.DRAW) {
            val noteHeightPx = 22f
            val pitch = data.pitch
            val color = if (gradientMode && workingGradient != null) Color.White else selectedColor
            val noteDurationMs = currentCellDurationMs(gridResolution)

            val startTimeMs = selectedTimeMs ?: 0L

            val newNote = MidiNote.withPaint(
                device = 0,
                pitch = pitch,
                color = color,
                startTimeMs = startTimeMs,
                durationMs = noteDurationMs,
                gradient = if (gradientMode) workingGradient else null
            )

            if (isTimelineBackedEditing) {
                TimelineCommandSurface.createNotes(
                    trackIndex = trackIndex,
                    entryStartMs = entryStartMs,
                    notes = listOf(newNote)
                ).also { commandResult ->
                    if (commandResult.didChange) {
                        syncCurrentEntry(timelineEntrySnapshot())
                    }
                }
            } else {
                onNoteAdd?.invoke(newNote)
                UndoManager.addAction(
                    UndoableAction.PianoRollNoteCreation(
                        trackIndex = trackIndex,
                        entryStartMs = entryStartMs,
                        note = newNote,
                        onNoteAdd = { createdNote -> onNoteAdd?.invoke(createdNote) },
                        onNoteDelete = { deletedNote -> onNoteDelete?.invoke(deletedNote) },
                        currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                        currentEntrySetter = { updatedEntry -> this@PianoRollWorkspaceMode.currentEntry = updatedEntry }
                    )
                )
                currentEntry = entry.copy(notes = entry.notes + newNote)
            }
        }
    }
}

private fun currentCellDurationMs(currentResolution: GridResolution): Long =
    (MS_PER_BEAT / currentResolution.snapDivisionsPerBeat).coerceAtLeast(1L)
