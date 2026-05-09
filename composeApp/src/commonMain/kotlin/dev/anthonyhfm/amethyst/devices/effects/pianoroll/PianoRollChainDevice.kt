package dev.anthonyhfm.amethyst.devices.effects.pianoroll

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.isGradient
import dev.anthonyhfm.amethyst.timeline.migration.LegacyPianoRollPath
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

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

    @LegacyPianoRollPath(
        replacement = "TimelineViewModel.openPianoRollForEntry",
        cutover = "Provide a TimelineClipContext-backed clip when opening the rebuilt piano roll from timeline data."
    )
    private fun openLegacyPianoRoll(entry: MidiEntry) {
        customMode.bindLegacyEntry(entry)
        WorkspaceRepository.switchMode(mode = customMode)
    }

    @Composable
    override fun Content() {
        val currentState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Piano Roll",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(120.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Button(
                onClick = {
                    openLegacyPianoRoll(currentState.midiEntry)
                },
                variant = ButtonVariant.Default,
                size = ButtonSize.IconLarge,
            ) {
                Icon(
                    imageVector = Lucide.Music,
                    contentDescription = "Piano Roll",
                    modifier = Modifier
                        .size(36.dp),
                    tint = Theme[colors][primaryForeground],
                )
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        // When an LED signal enters, play the MIDI entry
        n.forEach { signal ->
            if (signal.color != Color.Black) {
                // Cancel any previous scheduled jobs
                Heaven.cancelJobsForOwner(this)

                // Schedule all notes to play
                state.value.midiEntry.notes.forEach { note ->
                    val (x, y) = pitchToXY(note.pitch)

                    if (note.isGradient) {
                        // Schedule per-frame color signals across the note duration
                        val gradient = note.led.gradient!!
                        val frameIntervalMs = 1000.0 / Heaven.fps
                        var t = note.startTimeMs.toDouble()
                        while (t < note.endTimeMs.toDouble()) {
                            val fraction = ((t - note.startTimeMs) / note.durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
                            val capturedFraction = fraction
                            Heaven.schedule(t, owner = this) {
                                val (r, g, b) = GradientInterpolator.interpolate(gradient, capturedFraction)
                                signalExit?.invoke(listOf(Signal.LED(
                                    origin = this,
                                    x = x,
                                    y = y,
                                    color = Color(r, g, b),
                                    layer = note.led.layer,
                                    blendingMode = note.led.blendingMode
                                )))
                            }
                            t += frameIntervalMs
                        }
                    } else {
                        // Schedule note on (solid color)
                        Heaven.schedule(note.startTimeMs.toDouble(), owner = this) {
                            signalExit?.invoke(listOf(Signal.LED(
                                origin = this,
                                x = x,
                                y = y,
                                color = Color(note.led.red, note.led.green, note.led.blue),
                                layer = note.led.layer,
                                blendingMode = note.led.blendingMode
                            )))
                        }
                    }

                    // Schedule note off (always)
                    Heaven.schedule(note.endTimeMs.toDouble(), owner = this) {
                        signalExit?.invoke(listOf(Signal.LED(
                            origin = this,
                            x = x,
                            y = y,
                            color = Color.Black,
                            layer = note.led.layer,
                            blendingMode = note.led.blendingMode
                        )))
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

    companion object : ChainDeviceFactory<PianoRollChainDeviceState> {
        override val stateClass = PianoRollChainDeviceState::class
        override val serializer = PianoRollChainDeviceState.serializer()
        override fun create() = PianoRollChainDevice()
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
