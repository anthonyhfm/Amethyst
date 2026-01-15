package dev.anthonyhfm.amethyst.devices.effects.pianoroll

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

private const val MAX_PITCH_PER_DEVICE = 100
private const val DEFAULT_DURATION_MS = 4000L
private const val NO_TRACK_INDEX = -1

class PianoRollChainDevice : LEDChainDevice<PianoRollChainDeviceState>() {
    override val state = MutableStateFlow(PianoRollChainDeviceState())

    private val customMode: PianoRollWorkspaceMode = PianoRollWorkspaceMode()

    init {
        customMode.onNoteAdd = { note ->
            state.update { currentState ->
                val updatedNotes = currentState.midiEntry.notes + note
                currentState.copy(
                    midiEntry = currentState.midiEntry.copy(notes = updatedNotes)
                )
            }
        }

        customMode.onNoteUpdate = { oldNote, newNote ->
            state.update { currentState ->
                val updatedNotes = currentState.midiEntry.notes.map { 
                    if (it == oldNote) newNote else it 
                }
                currentState.copy(
                    midiEntry = currentState.midiEntry.copy(notes = updatedNotes)
                )
            }
        }

        customMode.onNoteDelete = { note ->
            state.update { currentState ->
                val updatedNotes = currentState.midiEntry.notes.filter { it != note }
                currentState.copy(
                    midiEntry = currentState.midiEntry.copy(notes = updatedNotes)
                )
            }
        }

        customMode.modeClose = {
            // Clear any preview state when closing the editor
            Heaven.devices.forEach { device ->
                device.previewState.clear()
            }
        }
    }

    @Composable
    override fun Content() {
        val currentState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Piano Roll",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(120.dp)
        ) {
            FilledIconButton(
                onClick = {
                    // Set the current entry for editing
                    customMode.currentEntry = currentState.midiEntry
                    customMode.trackIndex = NO_TRACK_INDEX // Not from timeline
                    customMode.entryStartMs = 0L
                    
                    // Switch to piano roll editing mode
                    WorkspaceRepository.switchMode(mode = customMode)
                },
                modifier = Modifier
                    .size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Piano Roll",
                    modifier = Modifier
                        .size(36.dp)
                )
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        n.forEach { signal ->
            if (signal.color != Color.Black) {
                Heaven.cancelJobsForOwner(this)
                state.value.midiEntry.notes.forEach { note ->
                    val (x, y) = pitchToXY(note.pitch)

                    Heaven.schedule(note.startTimeMs.toDouble(), owner = this) {
                        val noteOnSignal = Signal.LED(
                            origin = this,
                            x = x,
                            y = y,
                            color = Color(note.led.red, note.led.green, note.led.blue),
                            layer = note.led.layer,
                            blendingMode = note.led.blendingMode
                        )
                        signalExit?.invoke(listOf(noteOnSignal))
                    }

                    Heaven.schedule(note.endTimeMs.toDouble(), owner = this) {
                        val noteOffSignal = Signal.LED(
                            origin = this,
                            x = x,
                            y = y,
                            color = Color.Black,
                            layer = note.led.layer,
                            blendingMode = note.led.blendingMode
                        )
                        signalExit?.invoke(listOf(noteOffSignal))
                    }
                }
            }
        }
    }

    /**
     * Converts a MIDI pitch value to X,Y coordinates on a launchpad grid.
     * Note: This logic is shared with MidiEntry.pitchToXY() in TimelineEntry.kt
     */
    private fun pitchToXY(pitch: Int): Pair<Int, Int> {
        val localPitch = pitch % MAX_PITCH_PER_DEVICE
        val x = localPitch % 10
        val y = 9 - (localPitch / 10)
        return Pair(x, y)
    }
}

@Serializable
data class PianoRollChainDeviceState(
    val midiEntry: MidiEntry = MidiEntry(
        startTimeMs = 0,
        durationMs = DEFAULT_DURATION_MS,
        notes = emptyList(),
        name = "Piano Roll"
    )
) : DeviceState()