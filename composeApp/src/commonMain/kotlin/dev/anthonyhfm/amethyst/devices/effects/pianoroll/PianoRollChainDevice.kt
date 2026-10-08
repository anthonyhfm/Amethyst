package dev.anthonyhfm.amethyst.devices.effects.pianoroll

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.composeunstyled.Icon
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
import dev.anthonyhfm.amethyst.core.engine.elements.isSilentReplay
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
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
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.devices.TimelineTriggerable

private const val DEFAULT_DURATION_MS = 4000L
private const val NO_TRACK_INDEX = -1

class PianoRollChainDevice : LEDChainDevice<PianoRollChainDeviceState>(), TimelineTriggerable {
    override val state = MutableStateFlow(PianoRollChainDeviceState())
    override val helpRef = "PianoRoll"

    private val customMode: PianoRollWorkspaceMode = PianoRollWorkspaceMode()
    @kotlin.concurrent.Volatile
    private var isStandalonePlaying = false
    @kotlin.concurrent.Volatile
    private var standaloneStartedAtMs = 0.0
    @kotlin.concurrent.Volatile
    private var standaloneStartOffsetMs = 0L
    private var standaloneNotes: List<MidiNote> = emptyList()

    override fun timelineDuration(context: TimelineDurationContext): TimelineDuration {
        val entry = state.value.midiEntry
        return TimelineDuration.Finite(
            maxOf(entry.durationMs, entry.notes.maxOfOrNull { it.endTimeMs } ?: 0L)
        )
    }

    override fun startTimelineTrigger() = playStandaloneEntry()

    override fun stopTimelineTrigger() {
        Heaven.cancelJobsForOwner(this)
        isStandalonePlaying = false
        standaloneNotes.forEach { note ->
            emitNote(note = note, color = Color.Black)
        }
        standaloneNotes = emptyList()
    }

    init {
        customMode.onNoteAdd = { note ->
            updateStateFromUser { currentState ->
                val updatedNotes = currentState.midiEntry.notes + note
                currentState.copy(
                    midiEntry = currentState.midiEntry.copy(notes = updatedNotes)
                )
            }
        }

        customMode.onNoteUpdate = { oldNote, newNote ->
            updateStateFromUser { currentState ->
                val updatedNotes = currentState.midiEntry.notes.map { 
                    if (it.noteId == oldNote.noteId) newNote else it
                }
                currentState.copy(
                    midiEntry = currentState.midiEntry.copy(notes = updatedNotes)
                )
            }
        }

        customMode.onNoteDelete = { note ->
            updateStateFromUser { currentState ->
                val updatedNotes = currentState.midiEntry.notes.filterNot { it.noteId == note.noteId }
                currentState.copy(
                    midiEntry = currentState.midiEntry.copy(notes = updatedNotes)
                )
            }
        }

        customMode.modeClose = {
            if (isStandalonePlaying) {
                stopTimelineTrigger()
            }
        }
        customMode.standalonePlaybackPositionMs = {
            if (isStandalonePlaying) {
                standaloneStartOffsetMs + (Heaven.time - standaloneStartedAtMs).toLong().coerceAtLeast(0L)
            } else {
                null
            }
        }

        customMode.onPlaybackToggle = {
            if (isStandalonePlaying) {
                stopTimelineTrigger()
            } else {
                playStandaloneEntry(startAtMs = customMode.selectedTimeMs ?: 0L)
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

    private fun playStandaloneEntry(startAtMs: Long = 0L) {
        val entry = state.value.midiEntry
        stopTimelineTrigger()
        val startOffsetMs = startAtMs.coerceIn(0L, entry.durationMs)
        standaloneStartOffsetMs = startOffsetMs
        standaloneStartedAtMs = Heaven.time
        isStandalonePlaying = true

        val maxEndTimeMs = maxOf(entry.durationMs, entry.notes.maxOfOrNull { it.endTimeMs } ?: 0L)
        standaloneNotes = entry.notes.filter { it.endTimeMs > startOffsetMs }

        standaloneNotes.forEach { note ->
            if (note.isGradient) {
                val gradient = note.led.gradient!!
                val frameIntervalMs = 1000.0 / Heaven.fps
                var t = maxOf(note.startTimeMs, startOffsetMs).toDouble()
                while (t < note.endTimeMs.toDouble()) {
                    val fraction = ((t - note.startTimeMs) / note.durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
                    val capturedFraction = fraction
                    Heaven.schedule(delayInMs = t - startOffsetMs, owner = this) {
                        val (r, g, b) = GradientInterpolator.interpolate(gradient, capturedFraction)
                        emitNote(note = note, color = Color(r, g, b))
                    }
                    t += frameIntervalMs
                }
            } else {
                Heaven.schedule(delayInMs = (note.startTimeMs - startOffsetMs).coerceAtLeast(0L).toDouble(), owner = this) {
                    emitNote(
                        note = note,
                        color = Color(note.led.red, note.led.green, note.led.blue),
                    )
                }
            }
            Heaven.schedule(delayInMs = (note.endTimeMs - startOffsetMs).toDouble(), owner = this) {
                emitNote(note = note, color = Color.Black)
            }
        }

        Heaven.schedule(delayInMs = (maxEndTimeMs - startOffsetMs).toDouble(), owner = this) {
            isStandalonePlaying = false
        }
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

    override fun signalEnter(n: List<Signal>) {
        if (n.isSilentReplay()) {
            signalExit?.invoke(n)
            return
        }
        super.signalEnter(n)
        
        n.filterIsInstance<Signal.Midi>().forEach { signal ->
            if (signal.velocity != 0) {
                playStandaloneEntry()
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        if (n.isSilentReplay()) return
        // When an LED signal enters, play the MIDI entry
        n.forEach { signal ->
            if (signal.color != Color.Black) {
                playStandaloneEntry()
            }
        }
    }

    private fun emitNote(note: MidiNote, color: Color) {
        val (x, y) = state.value.midiEntry.pitchToXY(note = note) ?: return
        signalExit?.invoke(
            listOf(
                Signal.LED(
                    origin = this,
                    x = x,
                    y = y,
                    color = color,
                    layer = note.led.layer,
                    blendingMode = note.led.blendingMode,
                ),
            ),
        )
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
