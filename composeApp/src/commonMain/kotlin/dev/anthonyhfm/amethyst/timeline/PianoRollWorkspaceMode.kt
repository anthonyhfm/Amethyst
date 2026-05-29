package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.ColorControls
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.KeyframesPinchControl
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import dev.anthonyhfm.amethyst.timeline.contract.TimelineActiveEditorContext
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipContext
import dev.anthonyhfm.amethyst.timeline.contract.TimelineEditorSurface
import dev.anthonyhfm.amethyst.timeline.contract.TimelineEditorTool
import dev.anthonyhfm.amethyst.timeline.contract.TimelineTimingContext
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import dev.anthonyhfm.amethyst.timeline.migration.LegacyPianoRollPath
import dev.anthonyhfm.amethyst.timeline.migration.PianoRollCutoverSupport
import dev.anthonyhfm.amethyst.timeline.transforms.PianoRollTransforms
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Tabs
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsContent
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsList
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsTrigger
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.modifier.ResizeLeft
import dev.anthonyhfm.amethyst.ui.modifier.ResizeRight
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.h3
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import com.composables.icons.lucide.Music
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.isGradient
import dev.anthonyhfm.amethyst.timeline.ui.NoteGradientEditorBar
import dev.anthonyhfm.amethyst.ui.theme.timelineColorTokens
import dev.anthonyhfm.amethyst.ui.theme.timelineSelectionCursor
import androidx.compose.material3.Icon
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Blend
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Expand
import com.composables.icons.lucide.FlipHorizontal2
import com.composables.icons.lucide.FlipVertical2
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minimize2
import com.composables.icons.lucide.Rabbit
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Snail
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.PinchGraph

private fun currentCellDurationMs(currentResolution: GridResolution, bpm: Double): Long =
    ((60000.0 / bpm).toLong() / currentResolution.snapDivisionsPerBeat).coerceAtLeast(1L)

class PianoRollWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Piano Roll"
    override val selectable: Boolean = false
    override val claimInputs: Boolean = true

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

    /**
     * State flow tracking pressed keys: Map<Pair<DeviceIndex, Pitch>, IsPressed>
     */
    val pressedKeysState = MutableStateFlow<Map<Pair<Int, Int>, Boolean>>(emptyMap())

    var selectedColor by mutableStateOf(Color(0xFFFF6B35))
    var gradientMode by mutableStateOf(false)
    var workingGradient by mutableStateOf<List<NoteGradientStop>?>(null)
    var selectedTimeMs by mutableStateOf<Long?>(null)
    var gridResolution by mutableStateOf(GridResolution.Quarter)
    /** When true, zoom changes no longer override [gridResolution]. */
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
            // Timeline-backed: use the timeline engine so all clips play in background
            if (TimelineRepository.isPlaying.value) {
                TimelineRepository.pause()
            } else {
                TimelineRepository.setPlayheadPosition(entryStartMs)
                TimelineRepository.play()
            }
        } else {
            // Standalone (chain-backed): delegate to the chain device callback
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
            sel.note.copy(
                startTimeMs = sel.note.startTimeMs + offset
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
                notes = currentEntry?.notes.orEmpty().filter { note -> note !in notesToDelete }
            )
            TimelineCommandResult(didChange = true)
        }

        if (!result.didChange) return false

        SelectionManager.clear()
        return true
    }

    @Composable
    fun ModeContent(paddingValues: PaddingValues) {
        val entry = currentEntry ?: return
        val launchpads = Heaven.devices
        val selections by SelectionManager.selections.collectAsState()
        val playheadPositionMs by dev.anthonyhfm.amethyst.timeline.TimelineRepository.playheadPositionMs.collectAsState()

        var selectedGradientStopUUID by remember { mutableStateOf<String?>(null) }
        // Snapshot of gradient state at drag start; used to build a single undo action on drag end.
        var gradientBeforeDrag by remember { mutableStateOf<List<NoteGradientStop>?>(null) }

        // Key on note *identity* (time+pitch) only, so gradient state isn't reset when
        // the same notes are re-selected with updated content (e.g. after gradient edit).
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

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 12.dp, bottom = 12.dp)
                    .width(300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Theme[colors][background].copy(alpha = 0.95f))
                    .border(1.dp, Theme[colors][border], RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),

                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Theme[colors][muted])
                        .border(1.dp, Theme[colors][border], RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val solidOnSelected: () -> Unit = {
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
                    }
                    val gradientOnSelected: () -> Unit = {
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
                                    ?: SelectionManager.selections.value
                                        .filterIsInstance<Selectable.PianoRollNote>()
                                        .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                                        .firstOrNull()
                                workingGradient = firstGradientNote?.note?.led?.gradient
                            }
                            gradientMode = true
                        }
                    }

                    Tabs(
                        selectedTab = if (gradientMode) "gradient" else "solid",
                        tabs = listOf("solid", "gradient"),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TabsList(modifier = Modifier.fillMaxWidth()) {
                            TabsTrigger(
                                key = "solid",
                                selected = !gradientMode,
                                onSelected = solidOnSelected,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Lucide.Droplet, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("Solid")
                                }
                            }
                            TabsTrigger(
                                key = "gradient",
                                selected = gradientMode,
                                onSelected = gradientOnSelected,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Lucide.Blend, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("Gradient")
                                }
                            }
                        }
                        TabsContent("solid") {
                            ColorControls(
                                color = selectedColor,
                                onColorChange = applyColorToSelection
                            )
                        }
                        TabsContent("gradient") {
                            val currentGradient = workingGradient
                            if (currentGradient != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NoteGradientEditorBar(
                                        selectedStopUUID = selectedGradientStopUUID,
                                        onSelectionChange = { uuid ->
                                            selectedGradientStopUUID = uuid
                                            val stop = currentGradient.find { it.selectionUUID == uuid }
                                            if (stop != null) selectedColor = Color(stop.r, stop.g, stop.b)
                                        },
                                        stops = currentGradient,
                                        onStopMoved = { uuid, newPos ->
                                            val updatedGradient = currentGradient.map { s ->
                                                if (s.selectionUUID == uuid) s.copy(position = newPos) else s
                                            }
                                            workingGradient = updatedGradient
                                            applyGradientToNotes(updatedGradient, false)
                                        },
                                        onAddStop = { position ->
                                            val (r, g, b) = GradientInterpolator.interpolate(currentGradient, position)
                                            val newStop = NoteGradientStop(position, r, g, b)
                                            val updatedGradient = (currentGradient + newStop).sortedBy { it.position }
                                            workingGradient = updatedGradient
                                            selectedGradientStopUUID = newStop.selectionUUID
                                            selectedColor = Color(r, g, b)
                                            applyGradientToNotes(updatedGradient, true)
                                        },
                                        onDeleteStop = { uuid ->
                                            if (currentGradient.size > 2) {
                                                val updatedGradient = currentGradient.filter { it.selectionUUID != uuid }
                                                workingGradient = updatedGradient
                                                applyGradientToNotes(updatedGradient, true)
                                            }
                                        },
                                        onSmoothnessChange = { uuid, smoothness ->
                                            val updatedGradient = currentGradient.map { s ->
                                                if (s.selectionUUID == uuid) s.copy(smoothness = smoothness) else s
                                            }
                                            workingGradient = updatedGradient
                                            applyGradientToNotes(updatedGradient, true)
                                        },
                                        onDragStart = {
                                            gradientBeforeDrag = workingGradient
                                        },
                                        onDragFinish = {
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
                                        }
                                    )
                                    if (selectedGradientStopUUID != null) {
                                        ColorControls(
                                            color = selectedColor,
                                            onColorChange = { newColor ->
                                                selectedColor = newColor
                                                val updatedGradient = currentGradient.map { s ->
                                                    if (s.selectionUUID == selectedGradientStopUUID)
                                                        s.copy(r = newColor.red, g = newColor.green, b = newColor.blue)
                                                    else s
                                                }
                                                workingGradient = updatedGradient
                                                applyGradientToNotes(updatedGradient, false)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                var pinchValue by remember { mutableStateOf(0f) }
                var pinchBilateral by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Transform",
                        style = Theme[typography][small].copy(color = Theme[colors][foreground])
                    )

                    val enabled = selectedPianoNotes.isNotEmpty()
                    val contentColor = Theme[colors][foreground]

                    @Composable
                    fun SectionLabel(label: String) {
                        Text(
                            label,
                            style = Theme[typography][small].copy(
                                color = Theme[colors][mutedForeground],
                                fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)
                            )
                        )
                    }

                    @Composable
                    fun RowScope.LabeledIconButton(
                        icon: androidx.compose.ui.graphics.vector.ImageVector,
                        label: String? = null,
                        description: String,
                        onClick: () -> Unit
                    ) {
                        Button(
                            onClick = onClick,
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Ghost,
                            size = if (label != null) ButtonSize.Small else ButtonSize.Icon,
                            enabled = enabled
                        ) {
                            if (label != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(icon, contentDescription = description, modifier = Modifier.size(13.dp), tint = contentColor)
                                    Text(label, style = Theme[typography][small].copy(color = contentColor))
                                }
                            } else {
                                Icon(icon, contentDescription = description, modifier = Modifier.size(14.dp), tint = contentColor)
                            }
                        }
                    }

                    @Composable
                    fun Separator() {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Theme[colors][border]))
                    }

                    @Composable
                    fun ButtonRow(content: @Composable RowScope.() -> Unit) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clip(SmallShape)
                                .border(1.dp, Theme[colors][border], SmallShape),
                            content = content
                        )
                    }

                    SectionLabel("Shift in XY Grid")

                    ButtonRow {
                        LabeledIconButton(Lucide.ArrowUp, description = "Shift Up") {
                            applyTransform { PianoRollTransforms.shiftUp(it) }
                        }
                        Separator()
                        LabeledIconButton(Lucide.ArrowDown, description = "Shift Down") {
                            applyTransform { PianoRollTransforms.shiftDown(it) }
                        }
                        Separator()
                        LabeledIconButton(Lucide.ArrowLeft, description = "Shift Left") {
                            applyTransform { PianoRollTransforms.shiftLeft(it) }
                        }
                        Separator()
                        LabeledIconButton(Lucide.ArrowRight, description = "Shift Right") {
                            applyTransform { PianoRollTransforms.shiftRight(it) }
                        }
                    }

                    // Speed
                    SectionLabel("Speed")
                    ButtonRow {
                        LabeledIconButton(Lucide.Rabbit, "×2", "Double Speed") {
                            applyTransform { PianoRollTransforms.doubleSpeed(it) }
                        }
                        Separator()
                        LabeledIconButton(Lucide.Snail, "÷2", "Halve Speed") {
                            applyTransform { PianoRollTransforms.halveSpeed(it) }
                        }
                    }

                    // Note Length
                    SectionLabel("Note Length")
                    ButtonRow {
                        LabeledIconButton(Lucide.Expand, "×2", "Double Length") {
                            applyTransform { PianoRollTransforms.doubleLength(it) }
                        }
                        Separator()
                        LabeledIconButton(Lucide.Minimize2, "÷2", "Halve Length") {
                            applyTransform { PianoRollTransforms.halveLength(it) }
                        }
                    }

                    // Pinch — uses the same PinchGraph visual as the Keyframes device
                    SectionLabel("Pinch")
                    Row(
                        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PinchGraph(
                            pinch = pinchValue,
                            onPinchChange = { pinchValue = it },
                            bilateral = pinchBilateral,
                            onToggleBilateral = { pinchBilateral = !pinchBilateral },
                            modifier = Modifier.size(56.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${(pinchValue * 100f).toInt() / 100f}",
                                style = Theme[typography][small].copy(color = contentColor)
                            )
                            Button(
                                onClick = {
                                    if (pinchValue != 0f) {
                                        applyTransform { notes -> PianoRollTransforms.pinch(notes, pinchValue, pinchBilateral) }
                                        pinchValue = 0f
                                    }
                                },
                                variant = ButtonVariant.Secondary,
                                size = ButtonSize.Small,
                                enabled = enabled && pinchValue != 0f
                            ) { Text("Apply", style = Theme[typography][small].copy(color = contentColor)) }
                        }
                    }

                    // Rotate
                    SectionLabel("Rotate")
                    ButtonRow {
                        LabeledIconButton(Lucide.RotateCcw, "−90°", "Rotate Counter-Clockwise") {
                            applyTransform { PianoRollTransforms.rotateCCW(it) }
                        }
                        Separator()
                        LabeledIconButton(Lucide.RefreshCw, "180°", "Rotate 180°") {
                            applyTransform { PianoRollTransforms.rotate180(it) }
                        }
                        Separator()
                        LabeledIconButton(Lucide.RotateCw, "+90°", "Rotate Clockwise") {
                            applyTransform { PianoRollTransforms.rotateCW(it) }
                        }
                    }

                    // Mirror
                    SectionLabel("Mirror")
                    ButtonRow {
                        LabeledIconButton(Lucide.FlipHorizontal2, "Horiz", "Mirror Horizontal") {
                            applyTransform { PianoRollTransforms.mirrorHorizontal(it) }
                        }
                        Separator()
                        LabeledIconButton(Lucide.FlipVertical2, "Vert", "Mirror Vertical") {
                            applyTransform { PianoRollTransforms.mirrorVertical(it) }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Theme[colors][border])
                    )

                    // Gradient Spread: apply gradient across notes sorted by time
                    Button(
                        onClick = {
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
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Small,
                        enabled = selectedPianoNotes.size >= 2
                    ) { Text("Gradient Spread", style = Theme[typography][small].copy(color = contentColor)) }

                    Button(
                        onClick = {
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
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Small,
                        enabled = selectedPianoNotes.isNotEmpty()
                    ) { Text("Randomize Colors", style = Theme[typography][small].copy(color = contentColor)) }
                }
            }

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
                                .background(Theme[colors][mutedForeground].copy(alpha = 0.5f))
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        PianoRollEditor(
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

    override fun onKeyEvent(event: KeyEvent): Boolean {
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
            when (event.key) {
                Key.Spacebar -> {
                    if (!event.isCtrlPressed && !event.isMetaPressed) {
                        handleTogglePlayPause()
                        return true
                    }
                }

                Key.Escape -> { modeClose?.invoke(); return true }

                Key.S -> {
                    if (!event.isCtrlPressed && !event.isMetaPressed) {
                        activeTool = TimelineEditorTool.SELECT; return true
                    }
                }
                Key.D -> {
                    if (!event.isCtrlPressed && !event.isMetaPressed) {
                        activeTool = TimelineEditorTool.DRAW; return true
                    }
                }
                Key.E -> {
                    if (!event.isCtrlPressed && !event.isMetaPressed) {
                        activeTool = TimelineEditorTool.ERASE; return true
                    }
                }

                Key.W -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        modeClose?.invoke()
                        return true
                    }
                }

                Key.A -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        return selectAllNotes()
                    }
                }

                Key.MoveHome -> {
                    val entry = currentEntry
                    if (entry != null && entry.notes.isNotEmpty()) {
                        val firstNote = entry.notes.minByOrNull { it.startTimeMs }
                        if (firstNote != null) {
                            SelectionManager.clear()
                            SelectionManager.select(
                                Selectable.PianoRollNote(
                                    trackIndex = trackIndex,
                                    entryStartMs = entryStartMs,
                                    note = firstNote
                                )
                            )
                            return true
                        }
                    }
                }

                Key.MoveEnd -> {
                    val entry = currentEntry
                    if (entry != null && entry.notes.isNotEmpty()) {
                        val lastNote = entry.notes.maxByOrNull { it.startTimeMs }
                        if (lastNote != null) {
                            SelectionManager.clear()
                            SelectionManager.select(
                                Selectable.PianoRollNote(
                                    trackIndex = trackIndex,
                                    entryStartMs = entryStartMs,
                                    note = lastNote
                                )
                            )
                            return true
                        }
                    }
                }

                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                    val selected = SelectionManager.selections.value.filterIsInstance<Selectable.PianoRollNote>()
                        .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }

                    if (selected.isNotEmpty()) {
                        // Existing logic: Move selected notes
                        val currentEntry = currentEntry ?: return false

                        val launchpadCount = Heaven.devices.size.coerceAtLeast(1)
                        val totalPitches = launchpadCount * 100

                        val bpm = currentBpm()
                        val msPerBeat = (60000.0 / bpm).toLong()
                        val cellDurationMs = msPerBeat / 4

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
                            val newStartTime = sel.note.startTimeMs + timeDelta

                            val updatedNote = sel.note.copy(
                                pitch = newPitch,
                                startTimeMs = newStartTime,
                                led = sel.note.led.copy(index = newPitch)
                            )

                            noteUpdatesBefore.add(sel.note)
                            noteUpdatesAfter.add(updatedNote)
                        }

                        val result = if (isTimelineBackedEditing) {
                            TimelineCommandSurface.moveNotes(
                                trackIndex = trackIndex,
                                entryStartMs = entryStartMs,
                                changes = noteUpdatesBefore.zip(noteUpdatesAfter).map { (before, after) ->
                                    TimelineEditedNote(before = before, after = after)
                                }
                            ).also { commandResult ->
                                if (commandResult.didChange) {
                                    syncCurrentEntry(timelineEntrySnapshot())
                                }
                            }
                        } else {
                            noteUpdatesBefore.zip(noteUpdatesAfter).forEach { (before, after) ->
                                onNoteUpdate?.invoke(before, after)
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
                            TimelineCommandResult(didChange = true)
                        }

                        if (result.didChange) {
                            SelectionManager.clear()
                            noteUpdatesAfter.forEach { updatedNote ->
                                SelectionManager.select(
                                    Selectable.PianoRollNote(trackIndex, entryStartMs, updatedNote),
                                    single = false
                                )
                            }
                        }

                        return true
                    } else {
                        // New logic: Move time selection and draw notes with arrow keys
                        // Works when no notes are selected

                        // Only left/right for time selection and note drawing
                        if (event.key != Key.DirectionLeft && event.key != Key.DirectionRight) {
                            return false
                        }

                        val currentEntry = currentEntry ?: return false
                        val bpm = currentBpm()
                        val msPerBeat = (60000.0 / bpm).toLong()

                        // Calculate cell duration based on current grid resolution
                        val cellDurationMs = ((60000.0 / bpm).toLong() /
                            this@PianoRollWorkspaceMode.gridResolution.snapDivisionsPerBeat).coerceAtLeast(1L)

                        // Get current time or default to 0
                        val currentTimeMs = this@PianoRollWorkspaceMode.selectedTimeMs ?: 0L

                        val pressedKeys = pressedKeysState.value.filter { it.value }.keys

                        if (event.key == Key.DirectionRight) {
                            // Move forward: Add/extend notes if buttons pressed, otherwise just move selection
                            val newTimeMs = currentTimeMs + cellDurationMs

                            if (pressedKeys.isNotEmpty()) {
                                // Add or extend notes for each pressed button
                                val notesAdded = mutableListOf<MidiNote>()
                                val notesExtended = mutableListOf<Pair<MidiNote, MidiNote>>() // before, after

                                pressedKeys.forEach { (deviceIndex, pitch) ->
                                    // Check if there's a note ending at currentTimeMs (to extend)
                                    val noteToExtend = currentEntry.notes.find {
                                        it.device == deviceIndex &&
                                        it.pitch == pitch &&
                                        it.endTimeMs == currentTimeMs
                                    }

                                    if (noteToExtend != null) {
                                        // Extend existing note
                                        val extendedNote = noteToExtend.copy(
                                            durationMs = noteToExtend.durationMs + cellDurationMs
                                        )
                                        notesExtended.add(noteToExtend to extendedNote)
                                    } else {
                                        // Check if note already exists at this position
                                        val existingNote = currentEntry.notes.find {
                                            it.device == deviceIndex &&
                                            it.pitch == pitch &&
                                            it.startTimeMs == currentTimeMs
                                        }

                                        if (existingNote == null) {
                                            // Create new note
                                            val newNote = MidiNote.withPaint(
                                                device = deviceIndex,
                                                pitch = pitch,
                                                color = selectedColor,
                                                startTimeMs = currentTimeMs,
                                                durationMs = cellDurationMs,
                                                gradient = workingGradient.takeIf { gradientMode }
                                            )
                                            notesAdded.add(newNote)
                                        }
                                    }
                                }

                                // Apply changes
                                if (notesAdded.isNotEmpty() || notesExtended.isNotEmpty()) {
                                    if (isTimelineBackedEditing) {
                                        val createResult = if (notesAdded.isNotEmpty()) {
                                            TimelineCommandSurface.createNotes(
                                                trackIndex = trackIndex,
                                                entryStartMs = entryStartMs,
                                                notes = notesAdded
                                            )
                                        } else {
                                            TimelineCommandResult()
                                        }
                                        val resizeResult = if (notesExtended.isNotEmpty()) {
                                            TimelineCommandSurface.resizeNotes(
                                                trackIndex = trackIndex,
                                                entryStartMs = entryStartMs,
                                                changes = notesExtended.map { (before, after) ->
                                                    TimelineEditedNote(before = before, after = after)
                                                }
                                            )
                                        } else {
                                            TimelineCommandResult()
                                        }
                                        if ((createResult + resizeResult).didChange) {
                                            syncCurrentEntry(timelineEntrySnapshot())
                                        }
                                    } else {
                                        var updatedNotes = currentEntry.notes

                                        updatedNotes = updatedNotes + notesAdded
                                        notesExtended.forEach { (old, new) ->
                                            updatedNotes = updatedNotes.map { if (it == old) new else it }
                                            onNoteUpdate?.invoke(old, new)
                                        }

                                        notesAdded.forEach { note ->
                                            onNoteAdd?.invoke(note)
                                            UndoManager.addAction(
                                                UndoableAction.PianoRollNoteCreation(
                                                    trackIndex = trackIndex,
                                                    entryStartMs = entryStartMs,
                                                    note = note,
                                                    onNoteAdd = { n: MidiNote -> onNoteAdd?.invoke(n) },
                                                    onNoteDelete = { n: MidiNote -> onNoteDelete?.invoke(n) },
                                                    currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                                                    currentEntrySetter = { e: MidiEntry -> this@PianoRollWorkspaceMode.currentEntry = e }
                                                )
                                            )
                                        }

                                        if (notesExtended.isNotEmpty()) {
                                            UndoManager.addAction(
                                                UndoableAction.PianoRollNoteResize(
                                                    trackIndex = trackIndex,
                                                    entryStartMs = entryStartMs,
                                                    notesBefore = notesExtended.map { it.first },
                                                    notesAfter = notesExtended.map { it.second },
                                                    onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                                                    currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                                                    currentEntrySetter = { e: MidiEntry -> this@PianoRollWorkspaceMode.currentEntry = e }
                                                )
                                            )
                                        }

                                        this.currentEntry = currentEntry.copy(notes = updatedNotes)
                                    }
                                }
                            }

                            // Move time selection forward
                            this@PianoRollWorkspaceMode.selectedTimeMs = newTimeMs

                        } else if (event.key == Key.DirectionLeft) {
                            // Move backward: Remove/shorten notes if buttons pressed, otherwise just move selection
                            val newTimeMs = currentTimeMs - cellDurationMs

                            if (pressedKeys.isNotEmpty()) {
                                // Remove or shorten notes for each pressed button
                                val notesDeleted = mutableListOf<MidiNote>()
                                val notesShortened = mutableListOf<Pair<MidiNote, MidiNote>>() // before, after

                                pressedKeys.forEach { (deviceIndex, pitch) ->
                                    // Find note ending at currentTimeMs (to shorten or delete)
                                    val noteToModify = currentEntry.notes.find {
                                        it.device == deviceIndex &&
                                        it.pitch == pitch &&
                                        it.endTimeMs == currentTimeMs
                                    }

                                    if (noteToModify != null) {
                                        if (noteToModify.durationMs <= cellDurationMs) {
                                            // Delete note if it would become too short
                                            notesDeleted.add(noteToModify)
                                        } else {
                                            // Shorten note
                                            val shortenedNote = noteToModify.copy(
                                                durationMs = noteToModify.durationMs - cellDurationMs
                                            )
                                            notesShortened.add(noteToModify to shortenedNote)
                                        }
                                    }
                                }

                                // Apply changes
                                if (notesDeleted.isNotEmpty() || notesShortened.isNotEmpty()) {
                                    if (isTimelineBackedEditing) {
                                        val deleteResult = if (notesDeleted.isNotEmpty()) {
                                            TimelineCommandSurface.deleteNotes(
                                                trackIndex = trackIndex,
                                                entryStartMs = entryStartMs,
                                                notes = notesDeleted
                                            )
                                        } else {
                                            TimelineCommandResult()
                                        }
                                        val resizeResult = if (notesShortened.isNotEmpty()) {
                                            TimelineCommandSurface.resizeNotes(
                                                trackIndex = trackIndex,
                                                entryStartMs = entryStartMs,
                                                changes = notesShortened.map { (before, after) ->
                                                    TimelineEditedNote(before = before, after = after)
                                                }
                                            )
                                        } else {
                                            TimelineCommandResult()
                                        }
                                        if ((deleteResult + resizeResult).didChange) {
                                            syncCurrentEntry(timelineEntrySnapshot())
                                        }
                                    } else {
                                        var updatedNotes = currentEntry.notes

                                        updatedNotes = updatedNotes.filter { it !in notesDeleted }
                                        notesDeleted.forEach { noteToDelete ->
                                            onNoteDelete?.invoke(noteToDelete)
                                        }

                                        notesShortened.forEach { (old, new) ->
                                            updatedNotes = updatedNotes.map { if (it == old) new else it }
                                            onNoteUpdate?.invoke(old, new)
                                        }

                                        if (notesDeleted.isNotEmpty()) {
                                            UndoManager.addAction(
                                                UndoableAction.PianoRollNoteDeletion(
                                                    trackIndex = trackIndex,
                                                    entryStartMs = entryStartMs,
                                                    notes = notesDeleted,
                                                    onNoteAdd = { n: MidiNote -> onNoteAdd?.invoke(n) },
                                                    onNoteDelete = { n: MidiNote -> onNoteDelete?.invoke(n) },
                                                    currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                                                    currentEntrySetter = { e: MidiEntry -> this@PianoRollWorkspaceMode.currentEntry = e }
                                                )
                                            )
                                        }

                                        if (notesShortened.isNotEmpty()) {
                                            UndoManager.addAction(
                                                UndoableAction.PianoRollNoteResize(
                                                    trackIndex = trackIndex,
                                                    entryStartMs = entryStartMs,
                                                    notesBefore = notesShortened.map { it.first },
                                                    notesAfter = notesShortened.map { it.second },
                                                    onNoteUpdate = { old, new -> onNoteUpdate?.invoke(old, new) },
                                                    currentEntryGetter = { this@PianoRollWorkspaceMode.currentEntry },
                                                    currentEntrySetter = { e: MidiEntry -> this@PianoRollWorkspaceMode.currentEntry = e }
                                                )
                                            )
                                        }

                                        this.currentEntry = currentEntry.copy(notes = updatedNotes)
                                    }
                                }
                            }

                            // Move time selection backward
                            this@PianoRollWorkspaceMode.selectedTimeMs = newTimeMs
                        }

                        return true
                    }
                }

                Key.D -> {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        return duplicateSelectedNotes()
                    }
                }

                Key.Delete, Key.Backspace -> {
                    return deleteSelectedNotes()
                }
            }
        }
        return false
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset): () -> Unit = {
        val deviceIndex = Heaven.devices.indexOfFirst { 
            val expectedOffset = it.position.value.copy(
                x = it.position.value.x - it.layout.offsetX,
                y = it.position.value.y - it.layout.offsetY
            )
            expectedOffset == offset 
        }

        val isPressed = data.velocity > 0

        val key = Pair(deviceIndex, data.pitch)

        pressedKeysState.update { current ->
            current.toMutableMap().apply {
                if (isPressed) {
                    this[key] = true
                } else {
                    this.remove(key)
                }
            }
        }
    }
}

@Composable
private fun PianoRollEditor(
    entry: MidiEntry,
    launchpads: List<*>,
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
    onSelectedTimeMsChange: (Long?) -> Unit
) {
    // Keep stable references for long-lived pointer coroutines (these lambdas are
    // stable across recompositions caused by scroll/zoom updates).
    val latestViewport by rememberUpdatedState(viewport)
    val latestOnViewportChange by rememberUpdatedState(onViewportChange)

    val density = LocalDensity.current
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
    val totalPitches = launchpadCount * 100

    val noteHeightDp: Dp = 22.dp
    // Derive effective pixels-per-beat from the viewport's horizontal zoom.
    // zoomX is pixels-per-ms; one beat = MS_PER_BEAT ms.
    val effectivePixelsPerBeatDp = with(density) { (viewport.zoomX * MS_PER_BEAT).toDp() }
    val beatsPerBar = 4

    val clipBeats = entry.durationMs.toFloat() / MS_PER_BEAT.toFloat()

    // Extend the visible time range 25% (or at least 2 s) on each side of the clip
    val oobOverhangMs = (entry.durationMs * 0.25).toLong().coerceAtLeast(2000L)
    val totalBeatsWithOverhang = (entry.durationMs + 2 * oobOverhangMs).toFloat() / MS_PER_BEAT.toFloat()

    val metrics = remember(totalPitches, density, gridResolution, effectivePixelsPerBeatDp, oobOverhangMs) {
        PianoRollMetrics(totalPitches, noteHeightDp, effectivePixelsPerBeatDp, density, gridResolution, oobOffsetMs = oobOverhangMs)
    }
    val latestMetrics by rememberUpdatedState(metrics)
    // Keep oobOverhangMs and totalBeatsWithOverhang fresh inside long-lived pointerInput(Unit)
    // closures that use stable keys and therefore never restart on recomposition.
    val latestOobOverhangMs by rememberUpdatedState(oobOverhangMs)
    val latestTotalBeatsWithOverhang by rememberUpdatedState(totalBeatsWithOverhang)

    val canvasHeightDp = noteHeightDp * totalPitches
    val canvasWidthDp = effectivePixelsPerBeatDp * totalBeatsWithOverhang

    // Position the viewport at t=0 on first open so the OOB lead-in region is off-screen.
    // Also record the initial content extent so pan/zoom clamping works from the first frame.
    LaunchedEffect(Unit) {
        val initialScrollX = metrics.timeMsToXPx(0L).coerceAtLeast(0f)
        val contentWidthPx = with(density) { canvasWidthDp.toPx() }
        latestOnViewportChange(
            viewport.copy(scrollX = initialScrollX, contentWidth = contentWidthPx)
        )
    }

    var notesState by remember { mutableStateOf(entry.notes) }
    LaunchedEffect(entry) { notesState = entry.notes }

    // Collect pressed keys state
    val pressedKeys by pressedKeysState.collectAsState()

    // Extract pressed pitches per device from the state
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

    fun snapSelectedTimeMs(timeMs: Long, currentResolution: GridResolution, bpm: Double): Long {
        val gridIntervalMs = currentCellDurationMs(
            currentResolution = currentResolution,
            bpm = bpm,
        )
        return ((timeMs + gridIntervalMs / 2) / gridIntervalMs) * gridIntervalMs
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

    Column(modifier = Modifier.fillMaxSize().background(Theme[colors][background])) {
        PianoRollHeader(
            clipBeats = clipBeats,
            metrics = metrics,
            beatsPerBar = beatsPerBar,
            viewport = viewport,
            onTap = { offset ->
                // offset.x is screen-space since we tap the viewport directly
                val contentX = viewport.screenToContentX(offset.x)
                val timeMs = snapClipTimeToGrid(
                    viewport.contentXToClipTimeMs(contentX, oobOverhangMs),
                    gridResolution,
                )
                val snappedTimeMs = snapSelectedTimeMs(
                    timeMs = timeMs,
                    currentResolution = gridResolution,
                    bpm = currentBpm(),
                )
                onSelectedTimeMsChange(snappedTimeMs.coerceAtLeast(0L).coerceAtMost(entry.durationMs))
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
                        text = "No Virtual Devices",
                        style = Theme[typography][h3].copy(color = Theme[colors][foreground]),
                    )

                    Text(
                        text = "To start editing MIDI patterns, please add one or more virtual devices inside the Layout View first. Once a device is added, the piano roll editor will activate automatically.",
                        style = Theme[typography][p].copy(color = Theme[colors][mutedForeground]),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(Heaven.devices) { index, device ->
                    val devicePitchRange = 0 until 100
                    val deviceNotes = notesState.filter { it.device == index && it.pitch in devicePitchRange }
                    val rowHeight = noteHeightDp * 100

                    val zoomCoroutineScope = rememberCoroutineScope()
                    val zoomEndJobHolder = remember { arrayOfNulls<Job>(1) }

                    // Keep fresh references for use inside long-lived pointer coroutines so
                    // they always see the latest values without restarting on every state change.
                    val latestOnCreateNotes by rememberUpdatedState(onCreateNotes)
                    val latestOnDeleteNotes by rememberUpdatedState(onDeleteNotes)
                    val latestSelectedColor by rememberUpdatedState(selectedColor)
                    val latestGradientMode by rememberUpdatedState(gradientMode)
                    val latestWorkingGradient by rememberUpdatedState(workingGradient)
                    val latestGridResolution by rememberUpdatedState(gridResolution)
                    val latestMultiSelectDown by rememberUpdatedState(multiSelectModifierDown)
                    val latestShiftDown by rememberUpdatedState(shiftModifierDown)
                    val latestCurrentBpm by rememberUpdatedState(currentBpm)

                    Column {
                        Text(
                            text = "${device.name} (pos. X: ${device.position.value.x}, Y: ${device.position.value.y})",
                            style = Theme[typography][p].copy(color = Theme[colors][foreground]),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Row(modifier = Modifier.height(rowHeight)) {
                            val pressedForDevice = pressedKeysPerDevice[index] ?: emptySet()
                            PianoKeysColumn(
                                totalPitches = 100,
                                noteHeight = noteHeightDp,
                                verticalScroll = null,
                                deviceIndex = index,
                                pressedPitches = pressedForDevice
                            )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(rowHeight)
                                .onSizeChanged { viewportWidthPx = it.width }
                                .clipToBounds()
                                .background(timelinePalette.canvas)
                                .pointerInput(Unit) {
                                    // Track the pointer position so the zoom handler can use it
                                    // as the anchor even when the scroll event doesn't carry
                                    // accurate position data.
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pointerX = event.changes.firstOrNull()?.position?.x
                                            if (pointerX != null && event.type != PointerEventType.Exit) {
                                                lastPointerX = pointerX
                                            }
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    // Handles zoom (Ctrl/Cmd + scroll-Y) and pan (plain scroll).
                                    // Both are synchronous — no coroutines, no withFrameNanos.
                                    // Pointer events here are in VIEWPORT-space coordinates because
                                    // there is no horizontalScroll modifier in the chain.
                                    // latestViewport / latestOnViewportChange stay fresh across
                                    // recompositions without restarting this coroutine.
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == PointerEventType.Scroll) {
                                                val isZoomModifier = event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed
                                                val change = event.changes.firstOrNull()
                                                val deltaY = change?.scrollDelta?.y ?: 0f
                                                val deltaX = change?.scrollDelta?.x ?: 0f
                                                if (isZoomModifier && deltaY != 0f) {
                                                    val direction = if (deltaY > 0f) -1f else 1f
                                                    val factor = 1f + 0.1f * direction
                                                    // cursorX is viewport-relative (no scroll subtraction needed)
                                                    val cursorX = resolveViewportRelativeCursorX(
                                                        trackedPointerX = lastPointerX,
                                                        eventPointerX = change?.position?.x,
                                                    )
                                                    // Delegate zoom-with-anchor to the viewport projection.
                                                    // Supply current viewport dimensions so clamp() works correctly.
                                                    val contentWidthPx = latestViewport.zoomX * MS_PER_BEAT.toFloat() * latestTotalBeatsWithOverhang
                                                    val primed = latestViewport.copy(
                                                        viewportWidth = viewportWidthPx.toFloat(),
                                                        contentWidth = contentWidthPx,
                                                    )
                                                    val zoomed = primed.zoomAtX(factor, cursorX)
                                                    // After zoom, recompute content width for the new zoomX
                                                    val newContentWidthPx = zoomed.zoomX * MS_PER_BEAT.toFloat() * latestTotalBeatsWithOverhang
                                                    val clampedZoomed = zoomed.copy(contentWidth = newContentWidthPx).clamp()
                                                    latestOnViewportChange(clampedZoomed)
                                                    // Debounce: emit one log when zoom input stops for ~150 ms.
                                                    zoomEndJobHolder[0]?.cancel()
                                                    zoomEndJobHolder[0] = zoomCoroutineScope.launch {
                                                        delay(150)
                                                        println("[Amethyst/Zoom] PianoRollWorkspaceMode — zoom end, zoomX=${clampedZoomed.zoomX}")
                                                    }
                                                    change?.consume()
                                                } else if (!isZoomModifier) {
                                                    // Allow vertical scroll to pass through unless Shift is pressed for horizontal panning
                                                    val isHorizontalModifier = event.keyboardModifiers.isShiftPressed
                                                    val panDelta = if (isHorizontalModifier && deltaX == 0f) deltaY else deltaX
                                                    
                                                    if (panDelta != 0f) {
                                                        val contentWidthPx = latestViewport.zoomX * MS_PER_BEAT.toFloat() * latestTotalBeatsWithOverhang
                                                        latestOnViewportChange(
                                                            latestViewport.copy(
                                                                viewportWidth = viewportWidthPx.toFloat(),
                                                                contentWidth = contentWidthPx,
                                                            ).panBy(panDelta * 40f)
                                                        )
                                                        change?.consume()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                .pointerInput(activeTool, index, trackIndex, entryStartMs) {
                                        when (activeTool) {
                                            TimelineEditorTool.SELECT -> {
                                                detectTapGestures(
                                                    onTap = { offset ->
                                                        val freshNotes = notesState.filter {
                                                            it.device == index && it.pitch in devicePitchRange
                                                        }
                                                        val clickedNote = when (val hitTarget = findPianoRollHitTarget(
                                                            point = offset,
                                                            noteRects = buildNoteRectsScreenSpace(freshNotes, latestMetrics, latestViewport),
                                                        )) {
                                                            is PianoRollHitTarget.NoteBody -> hitTarget.note
                                                            is PianoRollHitTarget.ResizeLeft -> hitTarget.note
                                                            is PianoRollHitTarget.ResizeRight -> hitTarget.note
                                                            PianoRollHitTarget.Empty -> null
                                                        }

                                                        if (clickedNote != null) {
                                                            onSelectedTimeMsChange(null)
                                                            if (latestShiftDown) {
                                                                val selectable = Selectable.PianoRollNote(trackIndex, entryStartMs, clickedNote)
                                                                val currentSelections = SelectionManager.selections.value
                                                                val isSelected = currentSelections.any {
                                                                    it is Selectable.PianoRollNote &&
                                                                        it.selectionUUID == selectable.selectionUUID
                                                                }
                                                                if (isSelected) {
                                                                    SelectionManager.replaceSelections(
                                                                        currentSelections.filterNot {
                                                                            it is Selectable.PianoRollNote &&
                                                                                it.selectionUUID == selectable.selectionUUID
                                                                        }
                                                                    )
                                                                } else {
                                                                    SelectionManager.select(selectable, single = false)
                                                                }
                                                            } else {
                                                                SelectionManager.select(
                                                                    Selectable.PianoRollNote(trackIndex, entryStartMs, clickedNote),
                                                                    single = !latestMultiSelectDown
                                                                )
                                                            }
                                                        } else if (!latestMultiSelectDown) {
                                                            SelectionManager.clear()
                                                            val timeMs = snapClipTimeToGrid(
                                                                 latestViewport.screenXToClipTimeMs(offset.x, latestOobOverhangMs),
                                                                 latestGridResolution,
                                                             )
                                                            val snappedTimeMs = snapSelectedTimeMs(
                                                                timeMs = timeMs,
                                                                currentResolution = latestGridResolution,
                                                                bpm = latestCurrentBpm(),
                                                            )
                                                            onSelectedTimeMsChange(snappedTimeMs.coerceAtLeast(0L).coerceAtMost(entry.durationMs))
                                                        }
                                                    }
                                                )
                                            }

                                            TimelineEditorTool.DRAW -> {
                                                awaitEachGesture {
                                                    draftNote = null

                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    val freshNotes = notesState.filter {
                                                        it.device == index && it.pitch in devicePitchRange
                                                    }
                                                    val hitTarget = findPianoRollHitTarget(
                                                        point = down.position,
                                                        noteRects = buildNoteRectsScreenSpace(freshNotes, latestMetrics, latestViewport),
                                                    )

                                                    if (hitTarget != PianoRollHitTarget.Empty) {
                                                        return@awaitEachGesture
                                                    }

                                                    val lockedPitch = latestMetrics.yPxToPitch(down.position.y)
                                                    if (lockedPitch !in devicePitchRange) {
                                                        return@awaitEachGesture
                                                    }

                                                    val cellDurationMs = currentCellDurationMs(
                                                        currentResolution = latestGridResolution,
                                                        bpm = latestCurrentBpm(),
                                                    )
                                                    val anchorCellStartMs = floorClipTimeToGrid(
                                                         latestViewport.screenXToClipTimeMs(down.position.x, latestOobOverhangMs),
                                                         latestGridResolution,
                                                     )

                                                    fun updateDraft(position: Offset) {
                                                        val currentCellStartMs = floorClipTimeToGrid(
                                                             latestViewport.screenXToClipTimeMs(position.x, latestOobOverhangMs),
                                                             latestGridResolution,
                                                         )
                                                        draftNote = buildDraftNote(
                                                            device = index,
                                                            pitch = lockedPitch,
                                                            color = latestSelectedColor,
                                                            gradient = latestWorkingGradient.takeIf { latestGradientMode },
                                                            anchorCellStartMs = anchorCellStartMs,
                                                            currentCellStartMs = currentCellStartMs,
                                                            cellDurationMs = cellDurationMs,
                                                        )
                                                    }

                                                    updateDraft(down.position)
                                                    down.consume()

                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                                        if (!change.pressed) break
                                                        updateDraft(change.position)
                                                        change.consume()
                                                    }

                                                    val createdNote = draftNote
                                                    draftNote = null

                                                    if (createdNote != null) {
                                                        val latestDeviceNotes = notesState.filter {
                                                            it.device == index && it.pitch in devicePitchRange
                                                        }
                                                        val hasOverlap = latestDeviceNotes.any { note ->
                                                            note.pitch == createdNote.pitch &&
                                                                note.startTimeMs < createdNote.endTimeMs &&
                                                                note.endTimeMs > createdNote.startTimeMs
                                                        }

                                                        if (!hasOverlap) {
                                                            val result = latestOnCreateNotes(listOf(createdNote))
                                                            if (result.didChange) {
                                                                notesState = notesState + createdNote
                                                                onSelectedTimeMsChange(null)
                                                                SelectionManager.select(
                                                                    Selectable.PianoRollNote(trackIndex, entryStartMs, createdNote),
                                                                    single = !latestMultiSelectDown
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            TimelineEditorTool.ERASE -> {
                                                awaitEachGesture {
                                                    val erasedNoteKeys = mutableSetOf<Triple<Int, Int, Long>>()

                                                    fun eraseAt(offset: Offset): Boolean {
                                                        val freshNotes = notesState.filter {
                                                            it.device == index && it.pitch in devicePitchRange
                                                        }
                                                        val hitNote = when (val hitTarget = findPianoRollHitTarget(
                                                            point = offset,
                                                            noteRects = buildNoteRectsScreenSpace(freshNotes, latestMetrics, latestViewport),
                                                        )) {
                                                            is PianoRollHitTarget.NoteBody -> hitTarget.note
                                                            is PianoRollHitTarget.ResizeLeft -> hitTarget.note
                                                            is PianoRollHitTarget.ResizeRight -> hitTarget.note
                                                            PianoRollHitTarget.Empty -> null
                                                        } ?: return false

                                                        val noteKey = Triple(hitNote.device, hitNote.pitch, hitNote.startTimeMs)
                                                        if (!erasedNoteKeys.add(noteKey)) {
                                                            return false
                                                        }

                                                        val result = latestOnDeleteNotes(listOf(hitNote))
                                                        if (!result.didChange) {
                                                            return false
                                                        }

                                                        notesState = notesState.filter { it != hitNote }
                                                        SelectionManager.replaceSelections(
                                                            SelectionManager.selections.value.filter { sel ->
                                                                !(sel is Selectable.PianoRollNote &&
                                                                    sel.entryStartMs == entryStartMs &&
                                                                    sel.trackIndex == trackIndex &&
                                                                    sel.note.device == hitNote.device &&
                                                                    sel.note.pitch == hitNote.pitch &&
                                                                    sel.note.startTimeMs == hitNote.startTimeMs)
                                                            }
                                                        )
                                                        return true
                                                    }

                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    if (eraseAt(down.position)) {
                                                        down.consume()
                                                    }

                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                                        if (!change.pressed) break
                                                        if (eraseAt(change.position)) {
                                                            change.consume()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                .pointerInput(activeTool, index, trackIndex, entryStartMs, multiSelectModifierDown) {
                                        if (activeTool != TimelineEditorTool.SELECT) return@pointerInput
                                        awaitEachGesture {
                                            marqueeGestureActive = false

                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val freshNotes = notesState.filter {
                                                it.device == index && it.pitch in devicePitchRange
                                            }
                                            val currentMetrics = latestMetrics
                                            val hitTarget = findPianoRollHitTarget(
                                                point = down.position,
                                                noteRects = buildNoteRectsScreenSpace(freshNotes, currentMetrics, latestViewport),
                                            )

                                            if (hitTarget != PianoRollHitTarget.Empty) {
                                                return@awaitEachGesture
                                            }

                                            marqueeGestureActive = true
                                            marqueeStart = down.position
                                            marqueeCurrent = down.position
                                            if (!multiSelectModifierDown) {
                                                SelectionManager.clear()
                                            }

                                            var didDrag = false
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                                if (!change.pressed) break

                                                if (change.position != change.previousPosition) {
                                                    didDrag = true
                                                    marqueeCurrent = change.position
                                                    change.consume()
                                                }
                                            }

                                            if (didDrag) {
                                                val s = marqueeStart
                                                val c = marqueeCurrent
                                                if (s != null && c != null) {
                                                    val left = min(s.x, c.x)
                                                    val right = max(s.x, c.x)
                                                    val top = min(s.y, c.y)
                                                    val bottom = max(s.y, c.y)
                                                    buildNoteRectsScreenSpace(
                                                        notesState.filter { it.device == index && it.pitch in devicePitchRange },
                                                        currentMetrics,
                                                        latestViewport,
                                                    ).forEach { noteRect ->
                                                        val overlaps =
                                                            noteRect.left < right &&
                                                                noteRect.right > left &&
                                                                noteRect.top < bottom &&
                                                                noteRect.bottom > top
                                                        if (overlaps) {
                                                            SelectionManager.select(
                                                                Selectable.PianoRollNote(trackIndex, entryStartMs, noteRect.note),
                                                                single = false
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            marqueeGestureActive = false
                                            marqueeStart = null
                                            marqueeCurrent = null
                                        }
                                    }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pianoRollGridBackground(
                                        devicePitchRange = devicePitchRange,
                                        clipBeats = clipBeats,
                                        metrics = metrics,
                                        beatsPerBar = beatsPerBar,
                                        gridResolution = gridResolution,
                                        colors = gridColors,
                                        viewport = viewport
                                    )
                            ) {
                                // Clip boundary lines drawn behind notes
                                val boundaryColor = Theme[timelineColorTokens][timelineSelectionCursor]
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val clipStartX = viewport.contentToScreenX(metrics.timeMsToXPx(0))
                                    val clipEndX = viewport.contentToScreenX(metrics.timeMsToXPx(entry.durationMs))
                                    val strokePx = 2.dp.toPx()
                                    if (clipStartX in -10f..size.width + 10f) {
                                        drawLine(
                                            color = boundaryColor,
                                            start = Offset(clipStartX, 0f),
                                            end = Offset(clipStartX, size.height),
                                            strokeWidth = strokePx
                                        )
                                    }
                                    if (clipEndX in -10f..size.width + 10f) {
                                        drawLine(
                                            color = boundaryColor,
                                            start = Offset(clipEndX, 0f),
                                            end = Offset(clipEndX, size.height),
                                            strokeWidth = strokePx
                                        )
                                    }
                                    
                                    // Draw Playhead if timeline-backed
                                    if (playheadPositionMs != null) {
                                        // Only draw if playhead is somewhat nearby
                                        if (playheadPositionMs in -1000..entry.durationMs + 1000) {
                                            val playheadX = viewport.contentToScreenX(metrics.timeMsToXPx(playheadPositionMs))
                                            if (playheadX in -10f..size.width + 10f) {
                                                drawLine(
                                                    color = Color.White,
                                                    start = Offset(playheadX, 0f),
                                                    end = Offset(playheadX, size.height),
                                                    strokeWidth = 2.dp.toPx()
                                                )
                                            }
                                        }
                                    }
                                }

                                draftNote
                                    ?.takeIf { it.device == index && it.pitch in devicePitchRange }
                                    ?.let { previewNote ->
                                        DraftNoteBox(
                                            note = previewNote,
                                            metrics = metrics,
                                            viewport = viewport,
                                        )
                                    }

                                deviceNotes.forEach { note ->
                                    if (note.pitch in devicePitchRange) {
                                        // Use identity-based match (startTimeMs, pitch, device) instead of structural
                                        // equality so that notes remain visually selected even when `notesState` lags
                                        // behind `selections` by one Compose frame after a transform.
                                        val selected = selections.filterIsInstance<Selectable.PianoRollNote>().any {
                                            it.note.startTimeMs == note.startTimeMs &&
                                            it.note.pitch == note.pitch &&
                                            it.note.device == note.device &&
                                            it.entryStartMs == entryStartMs &&
                                            it.trackIndex == trackIndex
                                        }

                                        NoteBox(
                                            note = note,
                                            metrics = metrics,
                                            viewport = viewport,
                                            isSelected = selected,
                                            activeTool = activeTool,
                                            clipDurationMs = entry.durationMs,
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
                                                    .filter { it.entryStartMs == entryStartMs && it.trackIndex == trackIndex }
                                                    .map { it.note }
                                                    .ifEmpty { listOf(note) }

                                                val pitchDelta = if (dragOffset.y != 0f) {
                                                    -kotlin.math.round(dragOffset.y / metrics.noteHeightPx).toInt()
                                                } else {
                                                    0
                                                }

                                                val noteUpdates = selectedNotes.map { noteToDrag ->
                                                    val baseContentX = viewport.clipTimeMsToContentX(
                                                        noteToDrag.startTimeMs.toDouble(),
                                                        oobOverhangMs
                                                    )
                                                    val newContentX = baseContentX + dragOffset.x
                                                    val newTimeMs = snapClipTimeToGrid(
                                                        viewport.contentXToClipTimeMs(newContentX, oobOverhangMs),
                                                        latestGridResolution,
                                                    )
                                                    val newPitch = (noteToDrag.pitch + pitchDelta).coerceIn(devicePitchRange.first, devicePitchRange.last)

                                                    val updatedNote = noteToDrag.copy(
                                                        startTimeMs = newTimeMs,
                                                        pitch = newPitch,
                                                        led = noteToDrag.led.copy(index = newPitch)
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
                                                        latestGridResolution,
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
                                                        latestGridResolution,
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

                                PianoRollSelectedTimeCursor(
                                    selectedTimeMs = selectedTimeMs,
                                    viewport = viewport,
                                    oobOverhangMs = oobOverhangMs,
                                    rowHeight = rowHeight
                                )
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
    }
}
}
