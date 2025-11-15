package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import kotlin.math.roundToInt

private const val MS_PER_BEAT: Long = 500L
private const val SNAP_DIVISIONS: Int = 4 // Viertel Beats

private class PianoRollMetrics(
    val totalPitches: Int,
    val noteHeightDp: Dp,
    val pixelsPerBeatDp: Dp,
    private val density: Density
) {
    // PX Cache
    val noteHeightPx: Float = with(density) { noteHeightDp.toPx() }
    val pixelsPerBeatPx: Float = with(density) { pixelsPerBeatDp.toPx() }
    val noteRenderHeightPx: Float = noteHeightPx - 4f // visuelle Höhe der Note

    fun pitchToYPx(pitch: Int): Float = (totalPitches - 1 - pitch) * noteHeightPx
    fun yPxToPitch(y: Float): Int = (totalPitches - 1 - (y / noteHeightPx).toInt()).coerceIn(0, totalPitches - 1)

    fun timeMsToXPx(startTimeMs: Long): Float = (startTimeMs / MS_PER_BEAT.toFloat()) * pixelsPerBeatPx
    fun durationMsToWidthPx(durationMs: Long): Float = (durationMs / MS_PER_BEAT.toFloat()) * pixelsPerBeatPx

    fun xPxToTimeMs(x: Float): Long {
        val beatTime = x / pixelsPerBeatPx
        val snappedBeatTime = (beatTime * SNAP_DIVISIONS).roundToInt() / SNAP_DIVISIONS.toFloat()
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
    val density = LocalDensity.current

    val launchpadCount = launchpads.size.coerceAtLeast(1)
    val totalPitches = launchpadCount * 100

    val noteHeightDp: Dp = 40.dp
    val pixelsPerBeatDp: Dp = 80.dp
    val beatsPerBar = 4
    val totalBars = 50

    val metrics = remember(totalPitches, density) {
        PianoRollMetrics(totalPitches, noteHeightDp, pixelsPerBeatDp, density)
    }

    val canvasHeightDp = noteHeightDp * totalPitches
    val canvasWidthDp = pixelsPerBeatDp * beatsPerBar * totalBars

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
                noteHeight = noteHeightDp,
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
                Box(
                    modifier = Modifier
                        .width(canvasWidthDp)
                        .height(canvasHeightDp)
                        .drawBehind {
                            val widthPx = size.width
                            val heightPx = size.height

                            val darkBackground = Color(0xFF1A1A1A)
                            val lightRow = Color(0xFF242424)
                            val gridLine = Color(0xFF333333)
                            val beatLine = Color(0xFF444444)
                            val barLine = Color(0xFF555555)

                            for (pitch in 0 until totalPitches) {
                                val y = pitch * metrics.noteHeightPx
                                val isWhiteKey = pitch % 12 in listOf(0, 2, 4, 5, 7, 9, 11)
                                drawRect(
                                    color = if (isWhiteKey) darkBackground else lightRow,
                                    topLeft = Offset(0f, y),
                                    size = Size(widthPx, metrics.noteHeightPx)
                                )
                            }

                            // Horizontale Linien
                            for (pitch in 0..totalPitches) {
                                val y = pitch * metrics.noteHeightPx
                                drawLine(
                                    color = gridLine,
                                    start = Offset(0f, y),
                                    end = Offset(widthPx, y),
                                    strokeWidth = 1f
                                )
                            }

                            // Vertikale Beat-/Bar-Linien
                            for (bar in 0..totalBars) {
                                for (beat in 0 until beatsPerBar) {
                                    val x = (bar * beatsPerBar + beat) * metrics.pixelsPerBeatPx
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
                            detectTapGestures(
                                onTap = { offset ->
                                    // Nur Auswahl bei einfachem Klick
                                    val clickedNote = notesState.firstOrNull { note ->
                                        val noteYPx = metrics.pitchToYPx(note.pitch)
                                        val noteXPx = metrics.timeMsToXPx(note.startTimeMs)
                                        val noteWidthPx = metrics.durationMsToWidthPx(note.durationMs)
                                        offset.x >= noteXPx && offset.x <= noteXPx + noteWidthPx &&
                                                offset.y >= noteYPx && offset.y <= noteYPx + metrics.noteRenderHeightPx
                                    }
                                    if (clickedNote != null) {
                                        selectedNote = clickedNote
                                    }
                                },
                                onDoubleTap = { offset ->
                                    // Note nur bei Double Click erstellen, exakt im Grid (Beat + Pitch-Zeile)
                                    val pitch = metrics.yPxToPitch(offset.y)
                                    val timeMs = metrics.xPxToBeatStartTimeMs(offset.x)

                                    // Prüfen ob bestehende Note getroffen wurde -> dann nur auswählen
                                    val clickedNote = notesState.firstOrNull { note ->
                                        val noteYPx = metrics.pitchToYPx(note.pitch)
                                        val noteXPx = metrics.timeMsToXPx(note.startTimeMs)
                                        val noteWidthPx = metrics.durationMsToWidthPx(note.durationMs)
                                        offset.x >= noteXPx && offset.x <= noteXPx + noteWidthPx &&
                                                offset.y >= noteYPx && offset.y <= noteYPx + metrics.noteRenderHeightPx
                                    }
                                    if (clickedNote != null) {
                                        selectedNote = clickedNote
                                    } else {
                                        val newNote = MidiNote.withColor(
                                            pitch = pitch,
                                            color = Color(0xFFFF6B35),
                                            startTimeMs = timeMs,
                                            durationMs = MS_PER_BEAT // exakt eine Beat-Zelle
                                        )
                                        onNoteAdd?.invoke(newNote)
                                        notesState = notesState + newNote
                                        selectedNote = newNote
                                    }
                                }
                            )
                        }
                ) {
                    // Notes zeichnen
                    notesState.forEach { note ->
                        if (note.pitch in 0 until totalPitches) {
                            NoteBox(
                                note = note,
                                metrics = metrics,
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
    metrics: PianoRollMetrics,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (MidiNote, MidiNote) -> Unit
) {
    val density = LocalDensity.current
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val baseY = metrics.pitchToYPx(note.pitch)
    val baseX = metrics.timeMsToXPx(note.startTimeMs)
    val baseWidthPx = metrics.durationMsToWidthPx(note.durationMs)

    val currentX = baseX + dragOffset.x
    val currentY = baseY + dragOffset.y

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { currentX.toDp() },
                y = with(density) { currentY.toDp() }
            )
            .size(
                width = with(density) { baseWidthPx.coerceAtLeast(10f).toDp() },
                height = 40.dp
            )
            .background(
                if (isSelected) Color(0xFFFFAA00) else Color(note.led.red, note.led.green, note.led.blue)
            )
            .pointerInput(note) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDragEnd = {
                        if (dragOffset != Offset.Zero) {
                            val newX = baseX + dragOffset.x
                            val newY = baseY + dragOffset.y

                            val newTimeMs = metrics.xPxToTimeMs(newX)
                            val newPitch = metrics.yPxToPitch(newY)

                            val updatedNote = note.copy(
                                startTimeMs = newTimeMs,
                                pitch = newPitch,
                                led = note.led.copy(index = newPitch)
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
    noteHeight: Dp,
    verticalScroll: ScrollState
) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .fillMaxHeight()
            .background(Color(0xFF2A2A2A))
            .verticalScroll(verticalScroll, enabled = false)
    ) {
        Column(
            modifier = Modifier
                .width(120.dp)
                .height((noteHeight * totalPitches))
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
                ) {

                }
            }
        }
    }
}
