package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import dev.anthonyhfm.amethyst.timeline.utils.TimelineClipUtils
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack

object TimelineKeyHandler {
    fun handleKeyInput(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.E) {
            val selection = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineTime>().firstOrNull()

            return selection?.let {
                TimelineClipUtils.cutAtSelection(it)
            } ?: false
        }
        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.C) {
            // Check for range selection first
            val rangeSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
            if (rangeSelections.isNotEmpty()) {
                return handleRangeCopy(rangeSelections)
            }
            
            val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
            if (entrySelections.isNotEmpty()) {
                ClipboardManager.copy(entrySelections)
                return true
            }
        }
        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.X) {
            // Check for range selection first
            val rangeSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
            if (rangeSelections.isNotEmpty()) {
                return handleRangeCut(rangeSelections)
            }
            
            val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
            if (entrySelections.isNotEmpty()) {
                // Copy first
                ClipboardManager.copy(entrySelections)
                
                // Then delete using helper function
                val grouped = entrySelections.groupBy { it.trackIndex }
                grouped.forEach { (trackIndex, selList) ->
                    deleteEntriesFromTrack(trackIndex, selList.map { it.entryStartMs })
                }
                SelectionManager.clear()
                return true
            }
        }
        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.V) {
            ClipboardManager.paste()
            return true
        }
        if (keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace) {
            // Check for range selection first
            val rangeSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
            if (rangeSelections.isNotEmpty()) {
                return handleRangeDelete(rangeSelections)
            }
            
            // Check for track selections
            val trackSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineTrack>()
            if (trackSelections.isNotEmpty()) {
                trackSelections.sortedByDescending { it.trackIndex }.forEach { sel ->
                    val removed = TimelineRepository.tracks.value.getOrNull(sel.trackIndex)
                    if (removed != null) {
                        UndoManager.addAction(
                            UndoableAction.TrackRemoval(
                                trackIndex = sel.trackIndex,
                                track = removed
                            )
                        )
                        TimelineRepository.removeTrack(sel.trackIndex)
                    }
                }
                SelectionManager.clear()
                return true
            }
            
            // Otherwise check for entry selections
            val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
            if (entrySelections.isNotEmpty()) {
                val grouped = entrySelections.groupBy { it.trackIndex }
                grouped.forEach { (trackIndex, selList) ->
                    deleteEntriesFromTrack(trackIndex, selList.map { it.entryStartMs })
                }
                SelectionManager.clear()
                return true
            }
        }
        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.D) {
            // Check for range selection first
            val rangeSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
            if (rangeSelections.isNotEmpty()) {
                return handleRangeDuplicate(rangeSelections)
            }
            
            // Check for track selections
            val trackSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineTrack>()
            if (trackSelections.isNotEmpty()) {
                trackSelections.sortedBy { it.trackIndex }.forEach { sel ->
                    val duplicated = TimelineRepository.duplicateTrack(sel.trackIndex)
                    if (duplicated != null) {
                        UndoManager.addAction(
                            UndoableAction.TrackDuplication(
                                originalIndex = sel.trackIndex,
                                duplicatedIndex = sel.trackIndex + 1,
                                duplicatedTrack = duplicated
                            )
                        )
                    }
                }
                return true
            }
            
            // Otherwise check for entry selections
            val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
            if (entrySelections.isNotEmpty()) {
                val grouped = entrySelections.groupBy { it.trackIndex }
                val newSelections = mutableListOf<Selectable>()
                grouped.forEach { (trackIndex, selList) ->
                    val track = TimelineRepository.tracks.value.getOrNull(trackIndex)
                    
                    if (track is AudioTimelineTrack) {
                        val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }

                        selList.sortedBy { it.entryStartMs }.forEach { sel ->
                            val original = track.entries[sel.entryStartMs] ?: return@forEach
                            val duplicateStart = original.endTimeMs
                            val duplicate = original.copy(startTimeMs = duplicateStart)
                            val resolved = resolveOverlapForDuplicate(track, duplicate, original.startTimeMs)
                            if (resolved != null) {
                                track.entries[resolved.startTimeMs] = resolved
                                newSelections += Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = resolved.startTimeMs)
                            }
                        }
                        val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        val current = TimelineRepository.tracks.value.toMutableList()
                        val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
                        current[trackIndex] = newTrack
                        TimelineRepository.tracks.value = current.toList()
                        UndoManager.addAction(UndoableAction.TimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
                    } else if (track is MidiTimelineTrack) {
                        val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }

                        selList.sortedBy { it.entryStartMs }.forEach { sel ->
                            val original = track.entries[sel.entryStartMs] ?: return@forEach
                            val duplicateStart = original.endTimeMs
                            val duplicate = original.copy(startTimeMs = duplicateStart)
                            track.entries[duplicate.startTimeMs] = duplicate
                            newSelections += Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = duplicate.startTimeMs)
                        }
                        val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        val current = TimelineRepository.tracks.value.toMutableList()
                        val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
                        current[trackIndex] = newTrack
                        TimelineRepository.tracks.value = current.toList()
                        UndoManager.addAction(UndoableAction.MidiTimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
                    }
                }
                if (newSelections.isNotEmpty()) {
                    SelectionManager.clear()
                    newSelections.forEach { SelectionManager.select(it, single = false) }
                }
                return true
            }
        }

        return false
    }

    private fun resolveOverlapForDuplicate(
        track: AudioTimelineTrack,
        newEntry: AudioEntry,
        originStartMs: Long
    ): AudioEntry {
        val direction = newEntry.startTimeMs - originStartMs
        var adjustedNew = newEntry
        val toRemove = mutableListOf<Long>()
        val toReplace = mutableMapOf<Long, AudioEntry>()
        val toAdd = mutableListOf<AudioEntry>()
        val sorted = track.entries.values.sortedBy { it.startTimeMs }

        fun cropEntryEnd(entry: AudioEntry, newEndMs: Long): AudioEntry? {
            if (newEndMs <= entry.startTimeMs) return null
            val newDuration = newEndMs - entry.startTimeMs
            val raw = entry.rawData
            val croppedData = if (raw != null && raw.isNotEmpty()) {
                val bytesPerSample = (entry.bitDepth / 8) * entry.channels
                val samplesPerMs = entry.sampleRate.toDouble() / 1000.0
                val totalSamplesExact = newDuration * samplesPerMs
                val totalSamples = kotlin.math.round(totalSamplesExact).toLong()
                val totalBytes = (totalSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
                raw.sliceArray(0 until totalBytes)
            } else raw
            return entry.copy(durationMs = newDuration, rawData = croppedData)
        }
        fun buildEntrySegment(original: AudioEntry, segStartMs: Long, segEndMs: Long): AudioEntry? {
            if (segEndMs <= segStartMs) return null
            val raw = original.rawData ?: return original.copy(startTimeMs = segStartMs, durationMs = segEndMs - segStartMs)
            val bytesPerSample = (original.bitDepth / 8) * original.channels
            val samplesPerMs = original.sampleRate.toDouble() / 1000.0
            val startSamplesExact = (segStartMs - original.startTimeMs) * samplesPerMs
            val endSamplesExact = (segEndMs - original.startTimeMs) * samplesPerMs
            val startSamples = kotlin.math.round(startSamplesExact).toLong().coerceAtLeast(0)
            val endSamples = kotlin.math.round(endSamplesExact).toLong().coerceAtLeast(startSamples)
            val startByte = (startSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
            val endByte = (endSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
            if (startByte >= endByte) return null
            val slice = raw.sliceArray(startByte until endByte)
            return original.copy(startTimeMs = segStartMs, durationMs = segEndMs - segStartMs, rawData = slice)
        }
        fun splitEntry(original: AudioEntry, splitStartMs: Long, splitEndMs: Long): Pair<AudioEntry?, AudioEntry?> {
            val left = if (original.startTimeMs < splitStartMs && original.endTimeMs > original.startTimeMs) {
                buildEntrySegment(original, original.startTimeMs, splitStartMs)
            } else null
            val right = if (original.endTimeMs > splitEndMs && original.endTimeMs > splitEndMs) {
                buildEntrySegment(original, splitEndMs, original.endTimeMs)
            } else null
            return left to right
        }

        if (direction >= 0) {
            sorted.forEach { existing ->
                val overlaps = existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.startTimeMs
                if (!overlaps) return@forEach
                val existingFullyInsideNew = existing.startTimeMs >= adjustedNew.startTimeMs && existing.endTimeMs <= adjustedNew.endTimeMs
                val newFullyInsideExisting = existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs >= adjustedNew.endTimeMs
                val existingOverlapsAtStart = existing.startTimeMs < adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.startTimeMs && existing.endTimeMs <= adjustedNew.endTimeMs
                val existingOverlapsAtEnd = existing.startTimeMs >= adjustedNew.startTimeMs && existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.endTimeMs
                val existingSpansAcross = existing.startTimeMs < adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.endTimeMs

                when {
                    newFullyInsideExisting -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)
                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[existing.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }

                    existingFullyInsideNew -> toRemove.add(existing.startTimeMs)
                    existingOverlapsAtStart -> cropEntryEnd(existing, adjustedNew.startTimeMs)?.let { toReplace[existing.startTimeMs] = it } ?: toRemove.add(existing.startTimeMs)

                    existingOverlapsAtEnd -> {
                        toRemove.add(existing.startTimeMs)
                        buildEntrySegment(existing, adjustedNew.endTimeMs, existing.endTimeMs)?.let { toAdd.add(it) }
                    }

                    existingSpansAcross -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)
                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[existing.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }
                }
            }
        }

        toRemove.forEach { track.entries.remove(it) }
        toReplace.forEach { (k, v) -> track.entries[k] = v }
        toAdd.forEach { add -> track.entries[add.startTimeMs] = add }
        return adjustedNew
    }

    /**
     * Helper function to delete entries from a track and record undo action
     */
    private fun deleteEntriesFromTrack(trackIndex: Int, entryStartTimes: List<Long>) {
        val track = TimelineRepository.tracks.value.getOrNull(trackIndex) ?: return
        
        when (track) {
            is AudioTimelineTrack -> {
                val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                entryStartTimes.forEach { track.entries.remove(it) }
                val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                val current = TimelineRepository.tracks.value.toMutableList()
                val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
                current[trackIndex] = newTrack
                TimelineRepository.tracks.value = current.toList()
                UndoManager.addAction(UndoableAction.TimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
            }
            is MidiTimelineTrack -> {
                val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                entryStartTimes.forEach { track.entries.remove(it) }
                val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                val current = TimelineRepository.tracks.value.toMutableList()
                val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }

                current[trackIndex] = newTrack
                TimelineRepository.tracks.value = current.toList()
                UndoManager.addAction(UndoableAction.MidiTimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
            }
        }
    }

    /**
     * Handle copy operation for range selections
     * Copies entries or portions of entries within the range
     */
    private fun handleRangeCopy(rangeSelections: List<Selectable.TimelineRange>): Boolean {
        rangeSelections.forEach { range ->
            val track = TimelineRepository.tracks.value.getOrNull(range.trackIndex) ?: return@forEach
            
            when (track) {
                is AudioTimelineTrack -> {
                    val entriesInRange = getAudioEntriesInRange(track, range.startMs, range.endMs)
                    if (entriesInRange.isNotEmpty()) {
                        ClipboardManager.setClipboardData(
                            dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData.TimelineAudioEntries(entriesInRange)
                        )
                    }
                }
                is MidiTimelineTrack -> {
                    val entriesInRange = getMidiEntriesInRange(track, range.startMs, range.endMs)
                    if (entriesInRange.isNotEmpty()) {
                        ClipboardManager.setClipboardData(
                            dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData.TimelineMidiEntries(entriesInRange)
                        )
                    }
                }
            }
        }
        return true
    }

    /**
     * Handle cut operation for range selections
     * Cuts entries or portions within the range and removes them
     */
    private fun handleRangeCut(rangeSelections: List<Selectable.TimelineRange>): Boolean {
        rangeSelections.forEach { range ->
            val track = TimelineRepository.tracks.value.getOrNull(range.trackIndex) ?: return@forEach
            
            when (track) {
                is AudioTimelineTrack -> {
                    val entriesInRange = getAudioEntriesInRange(track, range.startMs, range.endMs)
                    if (entriesInRange.isNotEmpty()) {
                        ClipboardManager.setClipboardData(
                            dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData.TimelineAudioEntries(entriesInRange)
                        )
                    }
                    deleteRangeFromAudioTrack(range.trackIndex, track, range.startMs, range.endMs)
                }
                is MidiTimelineTrack -> {
                    val entriesInRange = getMidiEntriesInRange(track, range.startMs, range.endMs)
                    if (entriesInRange.isNotEmpty()) {
                        ClipboardManager.setClipboardData(
                            dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData.TimelineMidiEntries(entriesInRange)
                        )
                    }
                    deleteRangeFromMidiTrack(range.trackIndex, track, range.startMs, range.endMs)
                }
            }
        }
        SelectionManager.clear()
        return true
    }

    /**
     * Handle delete operation for range selections
     * Deletes entries or portions within the range
     */
    private fun handleRangeDelete(rangeSelections: List<Selectable.TimelineRange>): Boolean {
        rangeSelections.forEach { range ->
            val track = TimelineRepository.tracks.value.getOrNull(range.trackIndex) ?: return@forEach
            
            when (track) {
                is AudioTimelineTrack -> deleteRangeFromAudioTrack(range.trackIndex, track, range.startMs, range.endMs)
                is MidiTimelineTrack -> deleteRangeFromMidiTrack(range.trackIndex, track, range.startMs, range.endMs)
            }
        }
        SelectionManager.clear()
        return true
    }

    /**
     * Handle duplicate operation for range selections
     * Duplicates entries or portions within the range
     */
    private fun handleRangeDuplicate(rangeSelections: List<Selectable.TimelineRange>): Boolean {
        rangeSelections.forEach { range ->
            val track = TimelineRepository.tracks.value.getOrNull(range.trackIndex) ?: return@forEach
            
            when (track) {
                is AudioTimelineTrack -> {
                    val entriesInRange = getAudioEntriesInRange(track, range.startMs, range.endMs)
                    if (entriesInRange.isNotEmpty()) {
                        duplicateAudioEntriesInRange(range.trackIndex, track, entriesInRange, range.endMs)
                    }
                }
                is MidiTimelineTrack -> {
                    val entriesInRange = getMidiEntriesInRange(track, range.startMs, range.endMs)
                    if (entriesInRange.isNotEmpty()) {
                        duplicateMidiEntriesInRange(range.trackIndex, track, entriesInRange, range.endMs)
                    }
                }
            }
        }
        return true
    }

    /**
     * Get audio entries within a range, trimming them at range boundaries
     */
    private fun getAudioEntriesInRange(track: AudioTimelineTrack, startMs: Long, endMs: Long): List<AudioEntry> {
        val result = mutableListOf<AudioEntry>()
        
        track.entries.values.forEach { entry ->
            if (entry.endTimeMs <= startMs || entry.startTimeMs >= endMs) {
                // Entry is completely outside the range
                return@forEach
            }
            
            // Entry overlaps with range - trim it
            val trimmedStart = maxOf(entry.startTimeMs, startMs)
            val trimmedEnd = minOf(entry.endTimeMs, endMs)
            val trimmedEntry = trimAudioEntry(entry, trimmedStart, trimmedEnd)
            if (trimmedEntry != null) {
                result.add(trimmedEntry)
            }
        }
        
        return result.sortedBy { it.startTimeMs }
    }

    /**
     * Get MIDI entries within a range, trimming them at range boundaries
     */
    private fun getMidiEntriesInRange(track: MidiTimelineTrack, startMs: Long, endMs: Long): List<MidiEntry> {
        val result = mutableListOf<MidiEntry>()
        
        track.entries.values.forEach { entry ->
            if (entry.endTimeMs <= startMs || entry.startTimeMs >= endMs) {
                // Entry is completely outside the range
                return@forEach
            }
            
            // Entry overlaps with range - trim it
            val trimmedStart = maxOf(entry.startTimeMs, startMs)
            val trimmedEnd = minOf(entry.endTimeMs, endMs)
            val trimmedEntry = trimMidiEntry(entry, trimmedStart, trimmedEnd)
            if (trimmedEntry != null) {
                result.add(trimmedEntry)
            }
        }
        
        return result.sortedBy { it.startTimeMs }
    }

    /**
     * Trim an audio entry to a specific range
     */
    private fun trimAudioEntry(entry: AudioEntry, trimStart: Long, trimEnd: Long): AudioEntry? {
        if (trimEnd <= trimStart) return null
        
        val offsetStart = trimStart - entry.startTimeMs
        val newDuration = trimEnd - trimStart
        
        val rawData = entry.rawData
        if (rawData == null || rawData.isEmpty()) {
            return entry.copy(startTimeMs = trimStart, durationMs = newDuration, rawData = rawData)
        }
        
        val bytesPerSample = (entry.bitDepth / 8) * entry.channels
        val samplesPerMs = entry.sampleRate.toDouble() / 1000.0
        
        val startSampleExact = offsetStart * samplesPerMs
        val endSampleExact = (offsetStart + newDuration) * samplesPerMs
        val startSample = kotlin.math.round(startSampleExact).toLong().coerceAtLeast(0)
        val endSample = kotlin.math.round(endSampleExact).toLong()
        
        val startByte = (startSample * bytesPerSample).toInt().coerceIn(0, rawData.size)
        val endByte = (endSample * bytesPerSample).toInt().coerceIn(0, rawData.size)
        
        if (startByte >= endByte) return null
        
        val trimmedData = rawData.sliceArray(startByte until endByte)
        return entry.copy(startTimeMs = trimStart, durationMs = newDuration, rawData = trimmedData)
    }

    /**
     * Trim a MIDI entry to a specific range
     */
    private fun trimMidiEntry(entry: MidiEntry, trimStart: Long, trimEnd: Long): MidiEntry? {
        if (trimEnd <= trimStart) return null
        
        val offsetStart = trimStart - entry.startTimeMs
        val newDuration = trimEnd - trimStart
        
        // Filter and adjust notes to fit within the trimmed range
        val trimmedNotes = entry.notes.mapNotNull { note ->
            val noteStart = note.startTimeMs
            val noteEnd = note.startTimeMs + note.durationMs
            
            when {
                // Note entirely before trim range
                noteEnd <= offsetStart -> null
                // Note entirely after trim range
                noteStart >= offsetStart + newDuration -> null
                // Note overlaps with trim range
                else -> {
                    val newNoteStart = maxOf(noteStart, offsetStart) - offsetStart
                    val newNoteEnd = minOf(noteEnd, offsetStart + newDuration) - offsetStart
                    val newNoteDuration = newNoteEnd - newNoteStart
                    
                    if (newNoteDuration > 0) {
                        note.copy(startTimeMs = newNoteStart, durationMs = newNoteDuration)
                    } else null
                }
            }
        }
        
        return entry.copy(startTimeMs = trimStart, durationMs = newDuration, notes = trimmedNotes)
    }

    /**
     * Delete a range from an audio track, trimming overlapping entries
     */
    private fun deleteRangeFromAudioTrack(trackIndex: Int, track: AudioTimelineTrack, startMs: Long, endMs: Long) {
        val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
        val entriesToRemove = mutableListOf<Long>()
        val entriesToAdd = mutableListOf<AudioEntry>()
        
        track.entries.values.forEach { entry ->
            if (entry.startTimeMs >= startMs && entry.endTimeMs <= endMs) {
                // Entry completely within range - delete it
                entriesToRemove.add(entry.startTimeMs)
            } else if (entry.startTimeMs < startMs && entry.endTimeMs > endMs) {
                // Entry spans across range - split it
                entriesToRemove.add(entry.startTimeMs)
                
                val leftPart = trimAudioEntry(entry, entry.startTimeMs, startMs)
                val rightPart = trimAudioEntry(entry, endMs, entry.endTimeMs)
                
                leftPart?.let { entriesToAdd.add(it) }
                rightPart?.let { entriesToAdd.add(it) }
            } else if (entry.startTimeMs < endMs && entry.endTimeMs > endMs) {
                // Entry overlaps at the end of range - trim it
                entriesToRemove.add(entry.startTimeMs)
                
                val rightPart = trimAudioEntry(entry, endMs, entry.endTimeMs)
                rightPart?.let { entriesToAdd.add(it) }
            } else if (entry.startTimeMs < startMs && entry.endTimeMs > startMs) {
                // Entry overlaps at the start of range - trim it
                entriesToRemove.add(entry.startTimeMs)
                
                val leftPart = trimAudioEntry(entry, entry.startTimeMs, startMs)
                leftPart?.let { entriesToAdd.add(it) }
            }
        }
        
        entriesToRemove.forEach { track.entries.remove(it) }
        entriesToAdd.forEach { track.entries[it.startTimeMs] = it }
        
        val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
        val current = TimelineRepository.tracks.value.toMutableList()
        val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
        current[trackIndex] = newTrack
        TimelineRepository.tracks.value = current.toList()
        UndoManager.addAction(UndoableAction.TimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
    }

    /**
     * Delete a range from a MIDI track, trimming overlapping entries
     */
    private fun deleteRangeFromMidiTrack(trackIndex: Int, track: MidiTimelineTrack, startMs: Long, endMs: Long) {
        val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
        val entriesToRemove = mutableListOf<Long>()
        val entriesToAdd = mutableListOf<MidiEntry>()
        
        track.entries.values.forEach { entry ->
            if (entry.startTimeMs >= startMs && entry.endTimeMs <= endMs) {
                // Entry completely within range - delete it
                entriesToRemove.add(entry.startTimeMs)
            } else if (entry.startTimeMs < startMs && entry.endTimeMs > endMs) {
                // Entry spans across range - split it
                entriesToRemove.add(entry.startTimeMs)
                
                val leftPart = trimMidiEntry(entry, entry.startTimeMs, startMs)
                val rightPart = trimMidiEntry(entry, endMs, entry.endTimeMs)
                
                leftPart?.let { entriesToAdd.add(it) }
                rightPart?.let { entriesToAdd.add(it) }
            } else if (entry.startTimeMs < endMs && entry.endTimeMs > endMs) {
                // Entry overlaps at the end of range - trim it
                entriesToRemove.add(entry.startTimeMs)
                
                val rightPart = trimMidiEntry(entry, endMs, entry.endTimeMs)
                rightPart?.let { entriesToAdd.add(it) }
            } else if (entry.startTimeMs < startMs && entry.endTimeMs > startMs) {
                // Entry overlaps at the start of range - trim it
                entriesToRemove.add(entry.startTimeMs)
                
                val leftPart = trimMidiEntry(entry, entry.startTimeMs, startMs)
                leftPart?.let { entriesToAdd.add(it) }
            }
        }
        
        entriesToRemove.forEach { track.entries.remove(it) }
        entriesToAdd.forEach { track.entries[it.startTimeMs] = it }
        
        val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
        val current = TimelineRepository.tracks.value.toMutableList()
        val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
        current[trackIndex] = newTrack
        TimelineRepository.tracks.value = current.toList()
        UndoManager.addAction(UndoableAction.MidiTimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
    }

    /**
     * Duplicate audio entries in range
     */
    private fun duplicateAudioEntriesInRange(trackIndex: Int, track: AudioTimelineTrack, entries: List<AudioEntry>, placeAfterMs: Long) {
        val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
        val rangeStart = entries.minByOrNull { it.startTimeMs }?.startTimeMs ?: return
        
        entries.forEach { entry ->
            val offset = entry.startTimeMs - rangeStart
            val newStart = placeAfterMs + offset
            val duplicated = entry.copy(startTimeMs = newStart)
            val resolved = resolveOverlapForDuplicate(track, duplicated, entry.startTimeMs)
            track.entries[resolved.startTimeMs] = resolved
        }
        
        val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
        val current = TimelineRepository.tracks.value.toMutableList()
        val newTrack = AudioTimelineTrack().apply {
            entries.plus(track.entries)
        }
        current[trackIndex] = newTrack
        TimelineRepository.tracks.value = current.toList()
        UndoManager.addAction(UndoableAction.TimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
    }

    /**
     * Duplicate MIDI entries in range
     */
    private fun duplicateMidiEntriesInRange(trackIndex: Int, track: MidiTimelineTrack, entries: List<MidiEntry>, placeAfterMs: Long) {
        val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
        val rangeStart = entries.minByOrNull { it.startTimeMs }?.startTimeMs ?: return
        
        entries.forEach { entry ->
            val offset = entry.startTimeMs - rangeStart
            val newStart = placeAfterMs + offset
            val duplicated = entry.copy(startTimeMs = newStart)
            track.entries[duplicated.startTimeMs] = duplicated
        }
        
        val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
        val current = TimelineRepository.tracks.value.toMutableList()
        val newTrack = MidiTimelineTrack().apply {
            entries.plus(track.entries)
        }
        current[trackIndex] = newTrack
        TimelineRepository.tracks.value = current.toList()
        UndoManager.addAction(UndoableAction.MidiTimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
    }
}