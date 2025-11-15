package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
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
            // Left panel (400dp wide) - User will fill this
            Box(
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                // Panel content placeholder - user will implement
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
    
    // Piano roll settings
    val noteHeight = 16f // Height of each note row in pixels
    val gridWidth = 50f // Width of one beat in pixels
    val beatsPerBar = 4
    val canvasHeight = totalPitches * noteHeight
    val canvasWidth = 10000f // Very wide for scrolling
    
    var selectedNote by remember { mutableStateOf<MidiNote?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Piano Keys Column (left side)
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
                    .horizontalScroll(horizontalScroll)
                    .verticalScroll(verticalScroll)
            ) {
                // Background grid
                Box(
                    modifier = Modifier
                        .size(width = canvasWidth.dp, height = canvasHeight.dp)
                        .drawBehind {
                            val darkBackground = Color(0xFF1A1A1A)
                            val lightRow = Color(0xFF242424)
                            val gridLine = Color(0xFF333333)
                            val beatLine = Color(0xFF444444)
                            val barLine = Color(0xFF555555)
                            
                            // Draw row backgrounds
                            for (pitch in 0 until totalPitches) {
                                val y = pitch * noteHeight
                                val isWhiteKey = pitch % 12 in listOf(0, 2, 4, 5, 7, 9, 11)
                                drawRect(
                                    color = if (isWhiteKey) darkBackground else lightRow,
                                    topLeft = Offset(0f, y),
                                    size = Size(size.width, noteHeight)
                                )
                            }
                            
                            // Draw horizontal grid lines
                            for (pitch in 0..totalPitches) {
                                val y = pitch * noteHeight
                                drawLine(
                                    color = gridLine,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }
                            
                            // Draw vertical grid lines
                            var x = 0f
                            var beatIndex = 0
                            while (x < size.width) {
                                val isBarLine = beatIndex % beatsPerBar == 0
                                val isBeatLine = !isBarLine
                                
                                drawLine(
                                    color = when {
                                        isBarLine -> barLine
                                        isBeatLine -> beatLine
                                        else -> gridLine
                                    },
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = if (isBarLine) 2f else 1f
                                )
                                
                                x += gridWidth
                                beatIndex++
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val pitch = totalPitches - 1 - (offset.y / noteHeight).toInt()
                                val timeMs = (offset.x / gridWidth * 500).toLong() // 500ms per beat
                                
                                // Check if clicked on existing note
                                val clickedNote = entry.notes.firstOrNull { note ->
                                    val noteY = (totalPitches - 1 - note.pitch) * noteHeight
                                    val noteX = (note.startTimeMs / 500f) * gridWidth
                                    val noteWidth = (note.durationMs / 500f) * gridWidth
                                    
                                    offset.x >= noteX && offset.x <= noteX + noteWidth &&
                                    offset.y >= noteY && offset.y <= noteY + noteHeight
                                }
                                
                                if (clickedNote != null) {
                                    selectedNote = clickedNote
                                } else {
                                    // Create new note
                                    val defaultDuration = 500L // One beat
                                    val newNote = MidiNote(
                                        pitch = pitch.coerceIn(0, totalPitches - 1),
                                        velocity = 100,
                                        startTimeMs = timeMs,
                                        durationMs = defaultDuration
                                    )
                                    onNoteAdd?.invoke(newNote)
                                    selectedNote = newNote
                                }
                            }
                        }
                ) {
                    // Draw notes
                    entry.notes.forEach { note ->
                        if (note.pitch in 0 until totalPitches) {
                            val noteY = (totalPitches - 1 - note.pitch) * noteHeight
                            val noteX = (note.startTimeMs / 500f) * gridWidth
                            val noteWidth = (note.durationMs / 500f) * gridWidth
                            val isSelected = note == selectedNote
                            
                            Box(
                                modifier = Modifier
                                    .offset(x = noteX.dp, y = noteY.dp)
                                    .size(width = noteWidth.dp, height = noteHeight.dp)
                                    .background(
                                        if (isSelected) 
                                            Color(0xFFFFAA00)
                                        else 
                                            Color(0xFFFF6B35)
                                    )
                                    .pointerInput(note) {
                                        detectDragGestures(
                                            onDragStart = {
                                                selectedNote = note
                                                isDragging = true
                                            },
                                            onDragEnd = {
                                                isDragging = false
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            
                                            // Calculate new position
                                            val newX = noteX + dragAmount.x
                                            val newY = noteY + dragAmount.y
                                            
                                            val newTimeMs = ((newX / gridWidth) * 500).toLong().coerceAtLeast(0)
                                            val newPitch = (totalPitches - 1 - (newY / noteHeight).toInt()).coerceIn(0, totalPitches - 1)
                                            
                                            val updatedNote = note.copy(
                                                startTimeMs = newTimeMs,
                                                pitch = newPitch
                                            )
                                            onNoteUpdate?.invoke(note, updatedNote)
                                        }
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
private fun PianoKeysColumn(
    totalPitches: Int,
    noteHeight: Float,
    verticalScroll: ScrollState
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(verticalScroll, enabled = false) // Sync with main scroll
    ) {
        for (pitch in (totalPitches - 1) downTo 0) {
            val noteInOctave = pitch % 12
            val isBlackKey = noteInOctave in listOf(1, 3, 6, 8, 10)
            val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            val octave = pitch / 12
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(noteHeight.dp)
                    .background(
                        if (isBlackKey) 
                            Color(0xFF2A2A2A) 
                        else 
                            Color(0xFF3A3A3A)
                    )
                    .drawBehind {
                        // Draw border
                        drawLine(
                            color = Color(0xFF1A1A1A),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1f
                        )
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                // Note label would go here
            }
        }
    }
}
