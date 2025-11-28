package dev.anthonyhfm.amethyst.timeline.data

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.echo.AudioOutput
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import kotlinx.serialization.Serializable

interface TimelineEntry {
    val startTimeMs: Long
    val durationMs: Long
    val endTimeMs: Long get() = startTimeMs + durationMs

    fun start(startAt: Long? = null)
    fun stop()
}

@Serializable
data class AudioEntry(
    override val startTimeMs: Long,
    override val durationMs: Long,
    val fileName: String,
    val rawData: ByteArray?,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val bitDepth: Int = 16
) : TimelineEntry {
    private var audioSourceId: String? = null

    override fun start(startAt: Long?) {
        val actualStartTime = startAt ?: startTimeMs

        val trimmedData = if (startAt != null && startAt > startTimeMs) {
            val offsetMs = startAt - startTimeMs  // Fix: subtract, not add
            trimAudioData(rawData, offsetMs)
        } else {
            rawData
        }

        if (trimmedData != null && trimmedData.isNotEmpty()) {
            audioSourceId = AudioOutput.play(
                audioSignal = Signal.AudioSignal(
                    rawData = trimmedData,
                    sampleRate = sampleRate,
                    channels = channels,
                    bitDepth = bitDepth,
                    durationMs = if (startAt != null && startAt > startTimeMs) {
                        // Adjust duration if we're starting later
                        maxOf(0, durationMs - (startAt - startTimeMs))
                    } else {
                        durationMs
                    },
                    origin = "AudioEntry_$fileName"
                )
            )

            if (audioSourceId != null) {
                println("Started audio entry: $fileName at ${actualStartTime}ms (source: ${audioSourceId}) - offset: ${if (startAt != null && startAt > startTimeMs) startAt - startTimeMs else 0}ms")
            } else {
                println("Failed to start audio entry: $fileName")
            }
        } else {
            println("No audio data available for: $fileName")
        }
    }

    override fun stop() {
        audioSourceId?.let { sourceId ->
            AudioOutput.stop(sourceId)
            println("Stopped audio entry: $fileName (source: $sourceId)")
            audioSourceId = null
        } ?: run {
            println("No active audio source to stop for: $fileName")
        }
    }

    /**
     * Trim audio data to start at a specific offset in milliseconds with frame-accurate precision
     */
    private fun trimAudioData(data: ByteArray?, offsetMs: Long): ByteArray? {
        if (data == null || offsetMs <= 0) return data

        // Calculate bytes to skip based on offset with frame-accurate precision
        val bytesPerSample = (bitDepth / 8) * channels
        val samplesPerSecond = sampleRate.toDouble()
        val samplesPerMs = samplesPerSecond / 1000.0

        // Calculate exact samples to skip and round to nearest sample boundary
        val exactSamplesToSkip = offsetMs * samplesPerMs
        val samplesToSkip = kotlin.math.round(exactSamplesToSkip).toLong()
        val bytesToSkip = (samplesToSkip * bytesPerSample).toInt()

        // Ensure we're aligned to sample boundaries
        val alignedBytesToSkip = (bytesToSkip / bytesPerSample) * bytesPerSample

        println("DEBUG Audio trim: offsetMs=$offsetMs, samplesPerMs=$samplesPerMs, samplesToSkip=$samplesToSkip, bytesToSkip=$alignedBytesToSkip/${data.size}")

        return if (alignedBytesToSkip >= data.size) {
            println("WARNING: Audio trim offset ($offsetMs ms) exceeds audio duration")
            ByteArray(0) // Audio completely trimmed
        } else {
            data.sliceArray(alignedBytesToSkip until data.size)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioEntry) return false
        if (startTimeMs != other.startTimeMs) return false
        if (durationMs != other.durationMs) return false
        if (fileName != other.fileName) return false
        if (rawData != null) {
            if (other.rawData == null) return false
            if (!rawData.contentEquals(other.rawData)) return false
        } else if (other.rawData != null) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (bitDepth != other.bitDepth) return false
        return true
    }

    override fun hashCode(): Int {
        var result = startTimeMs.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + (rawData?.contentHashCode() ?: 0)
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + bitDepth
        return result
    }
}

@Serializable
data class MidiEntry(
    override val startTimeMs: Long,
    override val durationMs: Long,
    val notes: List<MidiNote> = emptyList(),
    val name: String = "MIDI Clip"
) : TimelineEntry {
    @kotlinx.serialization.Transient
    private val activeJobOwner = Any() // Unique owner for scheduled jobs

    // Convert pitch to XY coordinates for launchpad (same logic as PianoRoll)
    private fun pitchToXY(pitch: Int): Pair<Int, Int> {
        val localPitch = pitch % 100
        val x = localPitch % 10
        val y = localPitch / 10
        return Pair(x, y)
    }

    override fun start(startAt: Long?) {
        // Cancel any existing scheduled jobs for this clip
        Heaven.cancelJobsForOwner(activeJobOwner)

        val offsetMs = if (startAt != null && startAt > startTimeMs) {
            startAt - startTimeMs
        } else {
            0L
        }

        // Schedule all note-on and note-off events
        notes.forEach { note ->
            val noteStartInClip = note.startTimeMs
            val noteEndInClip = note.startTimeMs + note.durationMs
            
            // Schedule note-on
            if (noteStartInClip >= offsetMs) {
                val delayMs = noteStartInClip - offsetMs
                Heaven.schedule(delayMs.toDouble(), owner = activeJobOwner) {
                    sendNoteOn(note)
                }
            } else if (noteEndInClip > offsetMs) {
                // Note should already be playing, turn it on immediately
                sendNoteOn(note)
            }

            // Schedule note-off
            if (noteEndInClip > offsetMs) {
                val delayMs = noteEndInClip - offsetMs
                Heaven.schedule(delayMs.toDouble(), owner = activeJobOwner) {
                    sendNoteOff(note)
                }
            }
        }
    }

    override fun stop() {
        // Cancel all scheduled jobs for this clip
        Heaven.cancelJobsForOwner(activeJobOwner)

        // Turn off all LEDs immediately
        notes.forEach { note ->
            sendNoteOff(note)
        }
    }

    /**
     * Process MIDI notes at a given playback position
     * Note: With Heaven.schedule(), this method may not be needed anymore
     */
    fun processAtTime(currentTimeMs: Long) {
        // This method is called by TimelineRepository but with Heaven.schedule()
        // all note events are already scheduled, so we might not need to do anything here
    }

    private fun sendNoteOn(note: MidiNote) {
        val (x, y) = pitchToXY(note.pitch)
        val signal = Signal.LED(
            origin = activeJobOwner,
            x = x,
            y = y,
            color = Color(note.led.red, note.led.green, note.led.blue),
            layer = note.led.layer,
            blendingMode = note.led.blendingMode
        )
        Heaven.midiEnter(listOf(signal))
    }

    private fun sendNoteOff(note: MidiNote) {
        val (x, y) = pitchToXY(note.pitch)
        val signal = Signal.LED(
            origin = activeJobOwner,
            x = x,
            y = y,
            color = Color.Black,
            layer = note.led.layer,
            blendingMode = note.led.blendingMode
        )
        
        Heaven.midiEnter(listOf(signal))
    }
}