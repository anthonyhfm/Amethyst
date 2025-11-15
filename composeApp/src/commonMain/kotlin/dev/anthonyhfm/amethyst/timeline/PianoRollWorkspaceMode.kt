package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import kotlin.math.roundToInt

class PianoRollWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Piano Roll"
    override val selectable: Boolean = false
    override val claimInputs: Boolean = true

    var currentEntry: MidiEntry? = null
    var trackIndex: Int = -1
    var entryStartMs: Long = 0L
    
    var onNoteAdd: ((MidiNote) -> Unit)? = null
    var onNoteUpdate: ((MidiNote, MidiNote) -> Unit)? = null
    var onNoteDelete: ((MidiNote) -> Unit)? = null
    var modeClose: (() -> Unit)? = null

    @Composable
    fun ModeContent(paddingValues: PaddingValues) {
        val entry = currentEntry ?: return
        val launchpads = Heaven.devices

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // Left panel (300dp wide) - Styled like KeyframesWorkspaceMode panels
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Panel content placeholder - user will implement
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    // Content will be added by user
                }
            }

            // Main Piano Roll Area
            PianoRollEditor(
                entry = entry,
                launchpads = launchpads,
                onNoteAdd = onNoteAdd,
                onNoteUpdate = onNoteUpdate,
                onNoteDelete = onNoteDelete
            )
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.Escape -> {
                    modeClose?.invoke()
                    return true
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
    onNoteAdd: ((MidiNote) -> Unit)?,
    onNoteUpdate: ((MidiNote, MidiNote) -> Unit)?,
    onNoteDelete: ((MidiNote) -> Unit)?
) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    
    // Calculate total pitch range based on launchpad count
    val launchpadCount = launchpads.size.coerceAtLeast(1)
    val totalPitches = launchpadCount * 100 // 100 pitches per launchpad
    
    // Piano roll settings - doubled from before to make grid twice as wide
    val noteHeight = 40f // Doubled from 20dp - height of each note row in pixels
    val pixelsPerBeat = 80f // Width of one beat in pixels
    val beatsPerBar = 4
    val totalBars = 50 // Number of bars to show
    val canvasHeight = totalPitches * noteHeight
    val canvasWidth = totalBars * beatsPerBar * pixelsPerBeat // Total width
    
    var selectedNote by remember { mutableStateOf<MidiNote?>(null) }
    var notesState by remember { mutableStateOf(entry.notes) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Piano Keys Column (left side) - wider to match grid
            PianoKeysColumn(
                totalPitches = totalPitches,
                noteHeight = noteHeight,
                verticalScroll = verticalScroll
            )
            
            // Main Grid Area with Notes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF1A1A1A))
                    .horizontalScroll(horizontalScroll)
                    .verticalScroll(verticalScroll)
            ) {
                // Background grid
                Box(
                    modifier = Modifier
                        .width(canvasWidth.dp)
                        .height(canvasHeight.dp)
                        .drawBehind {
                            val darkBackground = Color(0xFF1A1A1A)
                            val lightRow = Color(0xFF242424)
                            val gridLine = Color(0xFF333333)
                            val beatLine = Color(0xFF444444)
                            val barLine = Color(0xFF555555)
                            
                            val widthPx = canvasWidth
                            val heightPx = canvasHeight
                            
                            // Draw row backgrounds for all pitches
                            for (pitch in 0 until totalPitches) {
                                val y = pitch * noteHeight
                                val isWhiteKey = pitch % 12 in listOf(0, 2, 4, 5, 7, 9, 11)
                                drawRect(
                                    color = if (isWhiteKey) darkBackground else lightRow,
                                    topLeft = Offset(0f, y),
                                    size = Size(widthPx, noteHeight)
                                )
                            }
                            
                            // Draw horizontal grid lines (for each pitch)
                            for (pitch in 0..totalPitches) {
                                val y = pitch * noteHeight
                                drawLine(
                                    color = gridLine,
                                    start = Offset(0f, y),
                                    end = Offset(widthPx, y),
                                    strokeWidth = 1f
                                )
                            }
                            
                            // Draw vertical grid lines (beats and bars)
                            for (bar in 0..totalBars) {
                                for (beat in 0 until beatsPerBar) {
                                    val x = (bar * beatsPerBar + beat) * pixelsPerBeat
                                    val isBarLine = beat == 0
                                    
                                    drawLine(
                                        color = if (isBarLine) barLine else beatLine,
                                        start = Offset(x, 0f),
                                        end = Offset(x, heightPx),
                                        strokeWidth = if (isBarLine) 2f else 1f
                                    )
                                }
                            }
                        }
                        .pointerInput(entry, onNoteAdd) {
                            detectTapGestures { offset ->
                                val pitch = totalPitches - 1 - (offset.y / noteHeight).toInt()
                                val beatTime = offset.x / pixelsPerBeat
                                
                                // Snap to grid - round to nearest 1/4 beat
                                val snappedBeatTime = (beatTime * 4).roundToInt() / 4f
                                val timeMs = (snappedBeatTime * 500).toLong() // 500ms per beat
                                
                                // Check if clicked on existing note
                                val clickedNote = notesState.firstOrNull { note ->
                                    val noteY = (totalPitches - 1 - note.pitch) * noteHeight
                                    val noteX = (note.startTimeMs / 500f) * pixelsPerBeat
                                    val noteWidth = (note.durationMs / 500f) * pixelsPerBeat
                                    
                                    offset.x >= noteX && offset.x <= noteX + noteWidth &&
                                    offset.y >= noteY && offset.y <= noteY + noteHeight
                                }
                                
                                if (clickedNote != null) {
                                    selectedNote = clickedNote
                                } else {
                                    // Create new note with grid snapping
                                    val defaultDuration = 500L // One beat
                                    val newNote = MidiNote.withColor(
                                        pitch = pitch.coerceIn(0, totalPitches - 1),
                                        color = Color(0xFFFF6B35), // Orange color
                                        startTimeMs = timeMs.coerceAtLeast(0),
                                        durationMs = defaultDuration,
                                        x = pitch % 10,
                                        y = pitch / 10
                                    )
                                    onNoteAdd?.invoke(newNote)
                                    notesState = notesState + newNote
                                    selectedNote = newNote
                                }
                            }
                        }
                ) {
                    // Draw notes
                    notesState.forEach { note ->
                        if (note.pitch in 0 until totalPitches) {
                            NoteBox(
                                note = note,
                                totalPitches = totalPitches,
                                noteHeight = noteHeight,
                                pixelsPerBeat = pixelsPerBeat,
                                isSelected = note == selectedNote,
                                onSelect = { selectedNote = note },
                                onUpdate = { oldNote, newNote ->
                                    onNoteUpdate?.invoke(oldNote, newNote)
                                    notesState = notesState.map { if (it == oldNote) newNote else it }
                                }
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
    totalPitches: Int,
    noteHeight: Float,
    pixelsPerBeat: Float,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (MidiNote, MidiNote) -> Unit
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    
    val noteY = (totalPitches - 1 - note.pitch) * noteHeight
    val noteX = (note.startTimeMs / 500f) * pixelsPerBeat
    val noteWidth = (note.durationMs / 500f) * pixelsPerBeat
    
    Box(
        modifier = Modifier
            .offset(x = (noteX + dragOffset.x).dp, y = (noteY + dragOffset.y).dp)
            .size(width = noteWidth.coerceAtLeast(10f).dp, height = (noteHeight - 4f).dp)
            .background(
                if (isSelected) 
                    Color(0xFFFFAA00)
                else 
                    note.led.color // Use the LED color from Signal.LED
            )
            .pointerInput(note) {
                detectDragGestures(
                    onDragStart = {
                        onSelect()
                    },
                    onDragEnd = {
                        // Apply the drag offset to create updated note with grid snapping
                        if (dragOffset != Offset.Zero) {
                            val newX = noteX + dragOffset.x
                            val newY = noteY + dragOffset.y
                            
                            // Snap to grid - 1/4 beat precision
                            val beatTime = newX / pixelsPerBeat
                            val snappedBeatTime = (beatTime * 4).roundToInt() / 4f
                            val newTimeMs = (snappedBeatTime * 500).toLong().coerceAtLeast(0)
                            
                            val newPitch = (totalPitches - 1 - (newY / noteHeight).toInt()).coerceIn(0, totalPitches - 1)
                            
                            val updatedNote = note.copy(
                                startTimeMs = newTimeMs,
                                pitch = newPitch,
                                led = note.led.copy(
                                    x = newPitch % 10,
                                    y = newPitch / 10
                                )
                            )
                            onUpdate(note, updatedNote)
                        }
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    }
                )
            }
    )
}

@Composable
private fun PianoKeysColumn(
    totalPitches: Int,
    noteHeight: Float,
    verticalScroll: ScrollState
) {
    Box(
        modifier = Modifier
            .width(160.dp) // Doubled from 80dp to match wider grid
            .fillMaxHeight()
            .background(Color(0xFF2A2A2A))
            .verticalScroll(verticalScroll, enabled = false) // Sync with main scroll
    ) {
        Column(
            modifier = Modifier
                .width(160.dp)
                .height((totalPitches * noteHeight).dp)
        ) {
            for (pitch in (totalPitches - 1) downTo 0) {
                val noteInOctave = pitch % 12
                val isBlackKey = noteInOctave in listOf(1, 3, 6, 8, 10)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(noteHeight.dp)
                        .background(
                            if (isBlackKey) 
                                Color(0xFF1A1A1A) 
                            else 
                                Color(0xFF2A2A2A)
                        )
                        .drawBehind {
                            // Draw border
                            drawLine(
                                color = Color(0xFF0A0A0A),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1f
                            )
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // Note labels could go here if needed
                }
            }
        }
    }
}
