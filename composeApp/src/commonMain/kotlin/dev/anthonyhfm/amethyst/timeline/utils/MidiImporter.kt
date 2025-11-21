package dev.anthonyhfm.amethyst.timeline.utils

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes

/**
 * Utility for importing MIDI files into timeline MIDI entries.
 * 
 * This is a simplified MIDI parser that focuses on extracting note events
 * for use in the Timeline's MIDI tracks.
 */
object MidiImporter {
    
    /**
     * Parse a MIDI file and create a MidiEntry from it
     * 
     * @param file The MIDI file to parse
     * @param startTimeMs The start time in the timeline where this entry should begin
     * @param name Optional name for the MIDI entry
     * @return MidiEntry containing the parsed MIDI notes, or null if parsing fails
     */
    suspend fun importMidiFile(
        file: PlatformFile,
        startTimeMs: Long = 0,
        name: String? = null
    ): MidiEntry? {
        return try {
            val data = file.readBytes()
            parseMidiData(data, startTimeMs, name ?: file.name)
        } catch (e: Exception) {
            println("Failed to import MIDI file: ${e.message}")
            null
        }
    }
    
    /**
     * Parse MIDI data bytes and extract notes
     */
    private fun parseMidiData(data: ByteArray, startTimeMs: Long, name: String): MidiEntry? {
        var offset = 0
        
        fun readByte(): Int {
            if (offset >= data.size) return 0
            return data[offset++].toInt() and 0xFF
        }
        
        fun readInt16(): Int {
            val b1 = readByte()
            val b2 = readByte()
            return (b1 shl 8) or b2
        }
        
        fun readInt32(): Int {
            val b1 = readByte()
            val b2 = readByte()
            val b3 = readByte()
            val b4 = readByte()
            return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
        }
        
        fun readString(len: Int): String {
            val bytes = ByteArray(len)
            for (i in 0 until len) {
                if (offset < data.size) {
                    bytes[i] = data[offset++]
                }
            }
            return bytes.decodeToString()
        }
        
        fun readVariableLength(): Int {
            var value = 0
            var byte: Int
            do {
                byte = readByte()
                value = (value shl 7) or (byte and 0x7F)
            } while ((byte and 0x80) != 0)
            return value
        }
        
        // Parse MIDI header
        val headerChunk = readString(4)
        if (headerChunk != "MThd") {
            println("Invalid MIDI file: missing MThd header")
            return null
        }
        
        val headerLength = readInt32()
        val format = readInt16()
        val trackCount = readInt16()
        val division = readInt16()
        
        // Calculate ticks per millisecond
        val ticksPerBeat = if (division >= 0) division else {
            val framesPerSecond = -(division shr 8)
            val ticksPerFrame = division and 0xFF
            framesPerSecond * ticksPerFrame
        }
        
        // Default tempo: 120 BPM = 500000 microseconds per beat
        var microsecondsPerBeat = 500000.0
        val ticksPerMs = ticksPerBeat / (microsecondsPerBeat / 1000.0)
        
        val notes = mutableListOf<MidiNote>()
        val activeNotes = mutableMapOf<Int, Pair<Long, Int>>() // pitch -> (startTime, velocity)
        
        // Parse tracks
        for (trackNum in 0 until trackCount) {
            if (offset >= data.size) break
            
            val trackChunk = readString(4)
            if (trackChunk != "MTrk") {
                println("Invalid track header: $trackChunk")
                continue
            }
            
            val trackLength = readInt32()
            val trackEnd = offset + trackLength
            
            var currentTime = 0L
            var runningStatus = 0
            
            while (offset < trackEnd && offset < data.size) {
                val deltaTime = readVariableLength()
                currentTime += deltaTime
                
                var statusByte = readByte()
                
                // Handle running status
                if ((statusByte and 0x80) == 0) {
                    offset-- // Put the byte back
                    statusByte = runningStatus
                } else {
                    runningStatus = statusByte
                }
                
                val messageType = statusByte and 0xF0
                val channel = statusByte and 0x0F
                
                when (messageType) {
                    0x80, 0x90 -> { // Note Off or Note On
                        val pitch = readByte()
                        val velocity = readByte()
                        
                        val timeMs = (currentTime / ticksPerMs).toLong()
                        
                        if (messageType == 0x90 && velocity > 0) {
                            // Note On
                            activeNotes[pitch] = Pair(timeMs, velocity)
                        } else {
                            // Note Off
                            val noteStart = activeNotes.remove(pitch)
                            if (noteStart != null) {
                                val (startTime, noteVelocity) = noteStart
                                val duration = timeMs - startTime
                                if (duration > 0) {
                                    // Convert velocity to color intensity
                                    val intensity = (noteVelocity / 127f).coerceIn(0f, 1f)
                                    val color = Color(intensity, intensity * 0.4f, 0f) // Orange-ish
                                    
                                    notes.add(
                                        MidiNote.withColor(
                                            pitch = pitch,
                                            color = color,
                                            startTimeMs = startTime,
                                            durationMs = duration,
                                        )
                                    )
                                }
                            }
                        }
                    }
                    0xA0 -> { // Polyphonic aftertouch
                        readByte() // note
                        readByte() // pressure
                    }
                    0xB0 -> { // Control change
                        readByte() // controller
                        readByte() // value
                    }
                    0xC0 -> { // Program change
                        readByte() // program
                    }
                    0xD0 -> { // Channel aftertouch
                        readByte() // pressure
                    }
                    0xE0 -> { // Pitch bend
                        readByte() // LSB
                        readByte() // MSB
                    }
                    0xF0 -> { // System messages
                        when (statusByte) {
                            0xFF -> { // Meta event
                                val metaType = readByte()
                                val metaLength = readVariableLength()
                                
                                if (metaType == 0x51 && metaLength == 3) {
                                    // Tempo change
                                    val tempo = (readByte() shl 16) or (readByte() shl 8) or readByte()
                                    microsecondsPerBeat = tempo.toDouble()
                                } else {
                                    // Skip other meta events
                                    repeat(metaLength) { readByte() }
                                }
                            }
                            0xF0, 0xF7 -> { // SysEx
                                val length = readVariableLength()
                                repeat(length) { readByte() }
                            }
                        }
                    }
                }
            }
        }
        
        // Close any remaining open notes
        for ((pitch, noteStart) in activeNotes) {
            val (startTime, velocity) = noteStart
            // Use a default duration for unclosed notes
            val intensity = (velocity / 127f).coerceIn(0f, 1f)
            val color = Color(intensity, intensity * 0.4f, 0f)
            
            notes.add(
                MidiNote.withColor(
                    pitch = pitch,
                    color = color,
                    startTimeMs = startTime,
                    durationMs = 500, // Default 500ms
                )
            )
        }
        
        // Calculate total duration
        val maxEndTime = notes.maxOfOrNull { it.endTimeMs } ?: 1000
        
        return MidiEntry(
            startTimeMs = startTimeMs,
            durationMs = maxEndTime,
            notes = notes,
            name = name
        )
    }
}
