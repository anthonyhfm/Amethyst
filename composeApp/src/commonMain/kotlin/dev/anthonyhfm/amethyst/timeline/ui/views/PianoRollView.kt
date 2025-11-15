package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import kotlin.math.roundToInt

/**
 * Piano Roll view component for visualizing and editing MIDI notes.
 * 
 * This component displays MIDI notes on a piano roll grid where:
 * - X-axis represents time
 * - Y-axis represents pitch (MIDI note number)
 * 
 * @param entry The MIDI entry containing notes to display
 * @param widthPx Width of the piano roll in pixels
 * @param heightPx Height of the piano roll in pixels
 * @param zoomLevel Zoom level for time axis (pixels per millisecond)
 * @param onNoteClick Callback when a note is clicked
 * @param onNoteMove Callback when a note is moved
 * @param onNoteResize Callback when a note is resized
 * @param onNoteCreate Callback when a new note is created
 * @param onNoteDelete Callback when a note is deleted
 */
@Composable
fun PianoRollView(
    entry: MidiEntry,
    widthPx: Float,
    heightPx: Float,
    zoomLevel: Float,
    onNoteClick: (MidiNote) -> Unit = {},
    onNoteMove: (MidiNote, Long, Int) -> Unit = { _, _, _ -> },
    onNoteResize: (MidiNote, Long) -> Unit = { _, _ -> },
    onNoteCreate: (pitch: Int, startTimeMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
    onNoteDelete: (MidiNote) -> Unit = {}
) {
    val density = LocalDensity.current
    val width = with(density) { widthPx.toDp() }
    val height = with(density) { heightPx.toDp() }
    
    // Piano roll spans MIDI notes 0-127
    val minPitch = 0
    val maxPitch = 127
    val pitchRange = maxPitch - minPitch + 1
    val noteHeight = heightPx / pitchRange
    
    var selectedNote by remember { mutableStateOf<MidiNote?>(null) }
    
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                // Draw piano roll grid
                val blackKeys = setOf(1, 3, 6, 8, 10) // C#, D#, F#, G#, A# in octave
                
                // Draw horizontal lines for each pitch
                for (pitch in minPitch..maxPitch) {
                    val y = (maxPitch - pitch) * noteHeight
                    val octave = pitch / 12
                    val noteInOctave = pitch % 12
                    val isBlackKey = blackKeys.contains(noteInOctave)
                    
                    // Alternate background for black keys
                    if (isBlackKey) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.05f),
                            topLeft = Offset(0f, y),
                            size = Size(size.width, noteHeight)
                        )
                    }
                    
                    // Draw grid line
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.2f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }
                
                // Draw vertical lines for time grid
                val gridIntervalMs = 1000L // 1 second intervals
                val gridIntervalPx = gridIntervalMs * zoomLevel
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.15f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += gridIntervalPx
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Calculate pitch and time from tap position
                    val pitch = maxPitch - (offset.y / noteHeight).toInt()
                    val timeMs = (offset.x / zoomLevel).toLong()
                    
                    // Check if tapped on an existing note
                    val clickedNote = entry.notes.firstOrNull { note ->
                        val noteY = (maxPitch - note.pitch) * noteHeight
                        val noteX = note.startTimeMs * zoomLevel
                        val noteWidth = note.durationMs * zoomLevel
                        
                        offset.x >= noteX && offset.x <= noteX + noteWidth &&
                        offset.y >= noteY && offset.y <= noteY + noteHeight
                    }
                    
                    if (clickedNote != null) {
                        onNoteClick(clickedNote)
                        selectedNote = clickedNote
                    } else {
                        // Create new note at tap position
                        val defaultDuration = 500L // 500ms default duration
                        onNoteCreate(pitch, timeMs, defaultDuration)
                    }
                }
            }
    ) {
        // Render MIDI notes
        entry.notes.forEach { note ->
            MidiNoteView(
                note = note,
                noteHeight = noteHeight,
                maxPitch = maxPitch,
                zoomLevel = zoomLevel,
                isSelected = note == selectedNote,
                onMove = { newStartMs, newPitch ->
                    onNoteMove(note, newStartMs, newPitch)
                },
                onResize = { newDurationMs ->
                    onNoteResize(note, newDurationMs)
                },
                onDelete = {
                    onNoteDelete(note)
                }
            )
        }
    }
}

/**
 * Individual MIDI note view component
 */
@Composable
private fun MidiNoteView(
    note: MidiNote,
    noteHeight: Float,
    maxPitch: Int,
    zoomLevel: Float,
    isSelected: Boolean,
    onMove: (Long, Int) -> Unit,
    onResize: (Long) -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    
    val y = (maxPitch - note.pitch) * noteHeight
    val x = note.startTimeMs * zoomLevel
    val noteWidth = note.durationMs * zoomLevel
    
    val offsetX = with(density) { x.toDp() }
    val offsetY = with(density) { y.toDp() }
    val width = with(density) { noteWidth.toDp() }
    val height = with(density) { noteHeight.toDp() }
    
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToPx() + dragOffsetX.roundToInt(), offsetY.roundToPx() + dragOffsetY.roundToInt()) }
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(
                if (isSelected) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                else 
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(2.dp)
            )
            .pointerInput(note) {
                detectDragGestures(
                    onDragEnd = {
                        // Calculate new position
                        val newX = x + dragOffsetX
                        val newY = y + dragOffsetY
                        val newStartMs = (newX / zoomLevel).toLong().coerceAtLeast(0)
                        val newPitch = (maxPitch - (newY / noteHeight).toInt()).coerceIn(0, 127)
                        
                        onMove(newStartMs, newPitch)
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Display note velocity as text if note is wide enough
        if (noteWidth > 30f) {
            Text(
                text = "v${note.velocity}",
                fontSize = 8.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = 4.dp)
            )
        }
    }
}
