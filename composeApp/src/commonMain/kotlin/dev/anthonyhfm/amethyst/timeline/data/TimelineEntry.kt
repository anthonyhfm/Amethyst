package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.core.engine.echo.AudioOutput
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
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

        // Calculate trimmed audio data if we're starting at a different position
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
    private val activeNotes = mutableMapOf<Int, Long>() // pitch to note-on timestamp

    override fun start(startAt: Long?) {
        val actualStartTime = startAt ?: startTimeMs
        val offsetMs = if (startAt != null && startAt > startTimeMs) {
            startAt - startTimeMs
        } else {
            0L
        }

        // Find notes that should be playing at the start time
        notes.forEach { note ->
            val noteStartInClip = note.startTimeMs
            val noteEndInClip = note.startTimeMs + note.durationMs
            
            if (offsetMs >= noteStartInClip && offsetMs < noteEndInClip) {
                // Note should already be playing
                // sendNoteOn(note.pitch, note.velocity)
                println("MIDI Note ON at ${actualStartTime} ms (offset ${offsetMs} ms): pitch=${note.pitch}")
                activeNotes[note.pitch] = actualStartTime
            }
        }
        
        println("Started MIDI entry: $name at ${actualStartTime}ms with offset ${offsetMs}ms - ${notes.size} notes")
    }

    override fun stop() {
        // Send note-off for all active notes
        activeNotes.keys.forEach { pitch ->
            sendNoteOff(pitch)
        }
        activeNotes.clear()
        println("Stopped MIDI entry: $name")
    }

    /**
     * Process MIDI notes at a given playback position
     */
    fun processAtTime(currentTimeMs: Long) {
        val clipOffsetMs = currentTimeMs - startTimeMs
        if (clipOffsetMs < 0 || clipOffsetMs >= durationMs) return

        notes.forEach { note ->
            val noteStart = note.startTimeMs
            val noteEnd = note.startTimeMs + note.durationMs

            if (clipOffsetMs >= noteStart && clipOffsetMs < noteEnd) {
                // Note should be on
                if (!activeNotes.containsKey(note.pitch)) {
                    // sendNoteOn(note.pitch, note.velocity)

                    println("MIDI Note ON at $currentTimeMs ms: pitch=${note.pitch}")
                    activeNotes[note.pitch] = currentTimeMs
                }
            } else if (activeNotes.containsKey(note.pitch)) {
                // Note should be off
                sendNoteOff(note.pitch)
                activeNotes.remove(note.pitch)
            }
        }
    }

    private fun sendNoteOn(pitch: Int, velocity: Int) {
        // TODO: Integrate with MIDI output system
        println("MIDI Note ON: pitch=$pitch, velocity=$velocity")
    }

    private fun sendNoteOff(pitch: Int) {
        // TODO: Integrate with MIDI output system
        println("MIDI Note OFF: pitch=$pitch")
    }
}

@Serializable
data class LightEntry(
    override val startTimeMs: Long,
    override val durationMs: Long,
) : TimelineEntry {
    override fun start(startAt: Long?) {
        // Light playback start logic
    }

    override fun stop() {
        // Light playback stop logic
    }
}
