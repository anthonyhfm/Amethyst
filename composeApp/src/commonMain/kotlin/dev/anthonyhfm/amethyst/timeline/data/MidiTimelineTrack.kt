package dev.anthonyhfm.amethyst.timeline.data

import kotlinx.serialization.Serializable

/**
 * A timeline track that contains MIDI entries for sequencing MIDI notes.
 * Similar to AudioTimelineTrack but for MIDI data.
 */
class MidiTimelineTrack : TimelineTrack<MidiEntry>() {
    override val entries: MutableMap<Long, MidiEntry> = mutableMapOf()

    /**
     * Add a MIDI entry to the track at a specific time position
     */
    fun addEntry(entry: MidiEntry) {
        entries[entry.startTimeMs] = entry
        println("MidiTimelineTrack: Added MIDI entry '${entry.name}' at ${entry.startTimeMs}ms with ${entry.notes.size} notes")
    }

    /**
     * Create a new MIDI entry with notes at a specific position
     */
    fun createEntry(startTimeMs: Long, durationMs: Long, notes: List<MidiNote>, name: String = "MIDI Clip") {
        val entry = MidiEntry(
            startTimeMs = startTimeMs,
            durationMs = durationMs,
            notes = notes,
            name = name
        )
        addEntry(entry)
    }

    /**
     * Add a single note to an existing entry, or create a new entry if none exists at that position
     */
    fun addNote(trackPositionMs: Long, note: MidiNote) {
        val entry = entries[trackPositionMs]
        if (entry != null) {
            // Add note to existing entry
            val updatedNotes = entry.notes + note
            entries[trackPositionMs] = entry.copy(notes = updatedNotes)
        } else {
            // Create new entry with this note
            createEntry(
                startTimeMs = trackPositionMs,
                durationMs = note.durationMs + note.startTimeMs,
                notes = listOf(note)
            )
        }
    }

    /**
     * Remove a note from an entry
     */
    fun removeNote(entryStartMs: Long, note: MidiNote) {
        val entry = entries[entryStartMs] ?: return
        val updatedNotes = entry.notes.filter { it != note }
        
        if (updatedNotes.isEmpty()) {
            // Remove entry if no notes remain
            entries.remove(entryStartMs)
        } else {
            entries[entryStartMs] = entry.copy(notes = updatedNotes)
        }
    }

    /**
     * Update a note in an entry
     */
    fun updateNote(entryStartMs: Long, oldNote: MidiNote, newNote: MidiNote) {
        val entry = entries[entryStartMs] ?: return
        val updatedNotes = entry.notes.map { if (it == oldNote) newNote else it }
        entries[entryStartMs] = entry.copy(notes = updatedNotes)
    }
}
