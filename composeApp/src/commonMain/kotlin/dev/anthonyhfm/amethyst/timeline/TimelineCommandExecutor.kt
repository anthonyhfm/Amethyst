package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.buildSegment
import dev.anthonyhfm.amethyst.timeline.data.copyWithPreciseTiming
import dev.anthonyhfm.amethyst.timeline.data.cropAudioEntryEnd
import dev.anthonyhfm.amethyst.timeline.data.deepCopy
import dev.anthonyhfm.amethyst.timeline.data.endTimeUs
import dev.anthonyhfm.amethyst.timeline.data.msToUs
import dev.anthonyhfm.amethyst.timeline.data.trimAudioEntry
import kotlin.math.round

object TimelineCommandExecutor {
    fun execute(command: TimelineEditCommand): TimelineCommandResult {
        return when (command) {
            is TimelineEditCommand.DeleteTracks -> deleteTracks(command.trackIndices)
            is TimelineEditCommand.DuplicateTracks -> duplicateTracks(command.trackIndices)
            is TimelineEditCommand.RenameTrack -> renameTrack(command.trackIndex, command.newName)
            is TimelineEditCommand.DeleteEntries -> deleteEntries(command.trackIndex, command.entryStartTimes)
            is TimelineEditCommand.DuplicateEntries -> duplicateEntries(command.trackIndex, command.entryStartTimes)
            is TimelineEditCommand.RenameEntry -> renameEntry(command.trackIndex, command.entryStartTime, command.newName)
            is TimelineEditCommand.DeleteRange -> deleteRange(command.trackIndex, command.startMs, command.endMs)
            is TimelineEditCommand.DuplicateRange -> duplicateRange(command.trackIndex, command.startMs, command.endMs)
            is TimelineEditCommand.CreateNotes -> createNotes(command.trackIndex, command.entryStartTime, command.notes)
            is TimelineEditCommand.MoveNotes -> moveNotes(command.trackIndex, command.entryStartTime, command.changes)
            is TimelineEditCommand.ResizeNotes -> resizeNotes(command.trackIndex, command.entryStartTime, command.changes)
            is TimelineEditCommand.UpdateNotes -> updateNotes(command.trackIndex, command.entryStartTime, command.changes)
            is TimelineEditCommand.DeleteNotes -> deleteNotes(command.trackIndex, command.entryStartTime, command.notes)
            is TimelineEditCommand.CreateAutomationPoints -> createAutomationPoints(command.trackIndex, command.lane, command.points)
            is TimelineEditCommand.MoveAutomationPoints -> moveAutomationPoints(command.trackIndex, command.lane, command.changes)
            is TimelineEditCommand.DeleteAutomationPoints -> deleteAutomationPoints(command.trackIndex, command.lane, command.pointIds)
            is TimelineEditCommand.DeleteAutomationRange -> deleteAutomationRange(command.trackIndex, command.lane, command.startMs, command.endMs)
            is TimelineEditCommand.DuplicateAutomationRange -> duplicateAutomationRange(command.trackIndex, command.lane, command.startMs, command.endMs)
            is TimelineEditCommand.SetAutomationLaneVisibility -> setAutomationLaneVisibility(command.trackIndex, command.lane, command.visible)
            is TimelineEditCommand.SetAutomationLaneEnabled -> setAutomationLaneEnabled(command.trackIndex, command.lane, command.enabled)
        }
    }

    fun execute(commands: Iterable<TimelineEditCommand>): TimelineCommandResult {
        return commands.fold(TimelineCommandResult()) { result, command ->
            result + execute(command)
        }
    }

    fun getAudioEntriesInRange(track: AudioTimelineTrack, startMs: Long, endMs: Long): List<AudioEntry> {
        val result = mutableListOf<AudioEntry>()

        track.entries.values.forEach { entry ->
            if (entry.endTimeMs <= startMs || entry.startTimeMs >= endMs) {
                return@forEach
            }

            val trimmedStart = maxOf(entry.startTimeMs, startMs)
            val trimmedEnd = minOf(entry.endTimeMs, endMs)
            trimAudioEntry(entry, trimmedStart, trimmedEnd)?.let(result::add)
        }

        return result.sortedBy { it.startTimeMs }
    }

    fun getMidiEntriesInRange(track: MidiTimelineTrack, startMs: Long, endMs: Long): List<MidiEntry> {
        val result = mutableListOf<MidiEntry>()

        track.entries.values.forEach { entry ->
            if (entry.endTimeMs <= startMs || entry.startTimeMs >= endMs) {
                return@forEach
            }

            val trimmedStart = maxOf(entry.startTimeMs, startMs)
            val trimmedEnd = minOf(entry.endTimeMs, endMs)
            trimMidiEntry(entry, trimmedStart, trimmedEnd)?.let(result::add)
        }

        return result.sortedBy { it.startTimeMs }
    }

    private fun deleteTracks(trackIndices: List<Int>): TimelineCommandResult {
        var didChange = false

        trackIndices.distinct().sortedDescending().forEach { trackIndex ->
            val removed = TimelineRepository.tracks.value.getOrNull(trackIndex) ?: return@forEach
            UndoManager.addAction(
                UndoableAction.TrackRemoval(
                    trackIndex = trackIndex,
                    track = removed
                )
            )
            TimelineRepository.removeTrack(trackIndex)
            didChange = true
        }

        return TimelineCommandResult(didChange = didChange)
    }

    private fun duplicateTracks(trackIndices: List<Int>): TimelineCommandResult {
        var didChange = false

        trackIndices.distinct().sortedDescending().forEach { trackIndex ->
            val duplicated = TimelineRepository.duplicateTrack(trackIndex) ?: return@forEach
            UndoManager.addAction(
                UndoableAction.TrackDuplication(
                    originalIndex = trackIndex,
                    duplicatedIndex = trackIndex + 1,
                    duplicatedTrack = duplicated
                )
            )
            didChange = true
        }

        return TimelineCommandResult(didChange = didChange)
    }

    private fun renameTrack(trackIndex: Int, newName: String): TimelineCommandResult {
        val track = TimelineRepository.tracks.value.getOrNull(trackIndex) ?: return TimelineCommandResult()
        if (track.name == newName) return TimelineCommandResult()

        UndoManager.addAction(
            UndoableAction.TrackRename(
                trackIndex = trackIndex,
                oldName = track.name,
                newName = newName
            )
        )
        TimelineRepository.renameTrack(trackIndex, newName)

        return TimelineCommandResult(didChange = true)
    }

    private fun deleteEntries(trackIndex: Int, entryStartTimes: List<Long>): TimelineCommandResult {
        val track = TimelineRepository.tracks.value.getOrNull(trackIndex) ?: return TimelineCommandResult()

        return when (track) {
            is AudioTimelineTrack -> {
                val before = track.entries.sortedAudioEntriesCopy()
                val updatedEntries = track.entries.toMutableMap().apply {
                    entryStartTimes.forEach(::remove)
                }
                val after = updatedEntries.sortedAudioEntriesCopy()
                TimelineCommandResult(didChange = applyAudioChange(trackIndex, before, after))
            }

            is MidiTimelineTrack -> {
                val before = track.entries.sortedMidiEntriesCopy()
                val updatedEntries = track.entries.toMutableMap().apply {
                    entryStartTimes.forEach(::remove)
                }
                val after = updatedEntries.sortedMidiEntriesCopy()
                TimelineCommandResult(didChange = applyMidiChange(trackIndex, before, after))
            }

            else -> TimelineCommandResult()
        }
    }

    private fun renameEntry(trackIndex: Int, entryStartTime: Long, newName: String): TimelineCommandResult {
        val track = TimelineRepository.tracks.value.getOrNull(trackIndex) ?: return TimelineCommandResult()

        return when (track) {
            is AudioTimelineTrack -> {
                val existing = track.entries[entryStartTime] ?: return TimelineCommandResult()
                if (existing.name == newName) return TimelineCommandResult()

                val before = track.entries.sortedAudioEntriesCopy()
                val after = track.entries.toMutableMap().apply {
                    this[entryStartTime] = existing.copy(name = newName)
                }.sortedAudioEntriesCopy()

                TimelineCommandResult(didChange = applyAudioChange(trackIndex, before, after))
            }

            is MidiTimelineTrack -> {
                val existing = track.entries[entryStartTime] ?: return TimelineCommandResult()
                if (existing.name == newName) return TimelineCommandResult()

                val before = track.entries.sortedMidiEntriesCopy()
                val after = track.entries.toMutableMap().apply {
                    this[entryStartTime] = existing.copy(name = newName)
                }.sortedMidiEntriesCopy()

                TimelineCommandResult(didChange = applyMidiChange(trackIndex, before, after))
            }

            else -> TimelineCommandResult()
        }
    }

    private fun duplicateEntries(trackIndex: Int, entryStartTimes: List<Long>): TimelineCommandResult {
        val track = TimelineRepository.tracks.value.getOrNull(trackIndex) ?: return TimelineCommandResult()

        return when (track) {
            is AudioTimelineTrack -> {
                val before = track.entries.sortedAudioEntriesCopy()
                val updatedEntries = track.entries.toMutableMap()
                val createdEntries = mutableListOf<TimelineCreatedEntry>()

                entryStartTimes.sorted().forEach { entryStartMs ->
                    val original = updatedEntries[entryStartMs] ?: return@forEach
                    val duplicate = original.copyWithPreciseTiming(startTimeUs = original.endTimeUs)
                    val resolved = resolveOverlapForDuplicate(updatedEntries, duplicate, original.startTimeMs)
                    updatedEntries[resolved.startTimeMs] = resolved
                    createdEntries += TimelineCreatedEntry(trackIndex, resolved.startTimeMs)
                }

                val after = updatedEntries.sortedAudioEntriesCopy()
                val didChange = applyAudioChange(trackIndex, before, after)
                TimelineCommandResult(
                    didChange = didChange,
                    createdEntries = if (didChange) createdEntries else emptyList()
                )
            }

            is MidiTimelineTrack -> {
                val before = track.entries.sortedMidiEntriesCopy()
                val updatedEntries = track.entries.toMutableMap()
                val createdEntries = mutableListOf<TimelineCreatedEntry>()

                entryStartTimes.sorted().forEach { entryStartMs ->
                    val original = updatedEntries[entryStartMs] ?: return@forEach
                    val duplicate = original.copy(startTimeMs = original.endTimeMs)
                    updatedEntries[duplicate.startTimeMs] = duplicate
                    createdEntries += TimelineCreatedEntry(trackIndex, duplicate.startTimeMs)
                }

                val after = updatedEntries.sortedMidiEntriesCopy()
                val didChange = applyMidiChange(trackIndex, before, after)
                TimelineCommandResult(
                    didChange = didChange,
                    createdEntries = if (didChange) createdEntries else emptyList()
                )
            }

            else -> TimelineCommandResult()
        }
    }

    private fun createNotes(trackIndex: Int, entryStartTime: Long, notes: List<MidiNote>): TimelineCommandResult {
        return updateMidiEntryNotes(trackIndex, entryStartTime) { entry ->
            val uniqueNotes = notes.distinct().filterNot(entry.notes::contains)
            if (uniqueNotes.isEmpty()) entry else entry.copy(notes = entry.notes + uniqueNotes)
        }
    }

    private fun moveNotes(
        trackIndex: Int,
        entryStartTime: Long,
        changes: List<TimelineEditedNote>
    ): TimelineCommandResult {
        return replaceEntryNotes(trackIndex, entryStartTime, changes)
    }

    private fun resizeNotes(
        trackIndex: Int,
        entryStartTime: Long,
        changes: List<TimelineEditedNote>
    ): TimelineCommandResult {
        return replaceEntryNotes(trackIndex, entryStartTime, changes)
    }

    private fun updateNotes(
        trackIndex: Int,
        entryStartTime: Long,
        changes: List<TimelineEditedNote>
    ): TimelineCommandResult {
        return replaceEntryNotes(trackIndex, entryStartTime, changes)
    }

    private fun deleteNotes(trackIndex: Int, entryStartTime: Long, notes: List<MidiNote>): TimelineCommandResult {
        return updateMidiEntryNotes(trackIndex, entryStartTime) { entry ->
            val notesToDelete = notes.distinct()
            if (notesToDelete.isEmpty()) {
                entry
            } else {
                entry.copy(notes = entry.notes.filterNot(notesToDelete::contains))
            }
        }
    }

    private fun deleteRange(trackIndex: Int, startMs: Long, endMs: Long): TimelineCommandResult {
        val track = TimelineRepository.tracks.value.getOrNull(trackIndex) ?: return TimelineCommandResult()
        val beforeTrack = track.deepCopy()
        val afterTrack = track.deepCopy()

        val didChange = when (afterTrack) {
            is AudioTimelineTrack -> {
                deleteAudioEntriesInRange(afterTrack, startMs, endMs) || afterTrack.deleteAutomationRange(startMs, endMs)
            }

            is MidiTimelineTrack -> {
                deleteMidiEntriesInRange(afterTrack, startMs, endMs) || afterTrack.deleteAutomationRange(startMs, endMs)
            }

            else -> false
        }

        if (!didChange) return TimelineCommandResult()

        TimelineRepository.replaceTrack(trackIndex, afterTrack)
        UndoManager.addAction(
            UndoableAction.TrackStateChange(
                trackIndex = trackIndex,
                beforeTrack = beforeTrack,
                afterTrack = afterTrack,
                mergeable = false
            )
        )

        return TimelineCommandResult(didChange = true)
    }

    private fun duplicateRange(trackIndex: Int, startMs: Long, endMs: Long): TimelineCommandResult {
        val track = TimelineRepository.tracks.value.getOrNull(trackIndex) ?: return TimelineCommandResult()
        val beforeTrack = track.deepCopy()
        val afterTrack = track.deepCopy()

        val createdEntries = when (afterTrack) {
            is AudioTimelineTrack -> duplicateAudioEntriesInRange(
                track = afterTrack,
                trackIndex = trackIndex,
                startMs = startMs,
                endMs = endMs
            )

            is MidiTimelineTrack -> duplicateMidiEntriesInRange(
                track = afterTrack,
                trackIndex = trackIndex,
                startMs = startMs,
                endMs = endMs
            )

            else -> emptyList()
        }
        val automationChanged = afterTrack.duplicateAutomationRange(startMs, endMs)

        if (createdEntries.isEmpty() && !automationChanged) {
            return TimelineCommandResult()
        }

        TimelineRepository.replaceTrack(trackIndex, afterTrack)
        UndoManager.addAction(
            UndoableAction.TrackStateChange(
                trackIndex = trackIndex,
                beforeTrack = beforeTrack,
                afterTrack = afterTrack,
                mergeable = false
            )
        )

        return TimelineCommandResult(
            didChange = true,
            createdEntries = createdEntries
        )
    }

    private fun createAutomationPoints(
        trackIndex: Int,
        lane: dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey,
        points: List<dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint>
    ): TimelineCommandResult {
        return TimelineCommandResult(
            didChange = applyTrackStateChange(trackIndex) {
                createAutomationPoints(
                    key = lane,
                    points = points
                )
            }
        )
    }

    private fun moveAutomationPoints(
        trackIndex: Int,
        lane: dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey,
        changes: List<TimelineEditedAutomationPoint>
    ): TimelineCommandResult {
        val effectiveChanges = changes
            .distinct()
            .filter { it.before != it.after }

        if (effectiveChanges.isEmpty()) return TimelineCommandResult()

        return TimelineCommandResult(
            didChange = applyTrackStateChange(trackIndex) {
                moveAutomationPoints(
                    key = lane,
                    points = effectiveChanges.map(TimelineEditedAutomationPoint::after)
                )
            }
        )
    }

    private fun deleteAutomationPoints(
        trackIndex: Int,
        lane: dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey,
        pointIds: List<String>
    ): TimelineCommandResult {
        val uniquePointIds = pointIds.distinct()
        if (uniquePointIds.isEmpty()) return TimelineCommandResult()

        return TimelineCommandResult(
            didChange = applyTrackStateChange(trackIndex) {
                deleteAutomationPoints(
                    key = lane,
                    pointIds = uniquePointIds
                )
            }
        )
    }

    private fun deleteAutomationRange(
        trackIndex: Int,
        lane: dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey,
        startMs: Long,
        endMs: Long
    ): TimelineCommandResult {
        if (endMs <= startMs) return TimelineCommandResult()

        return TimelineCommandResult(
            didChange = applyTrackStateChange(trackIndex) {
                deleteAutomationRange(
                    key = lane,
                    startMs = startMs,
                    endMs = endMs
                )
            }
        )
    }

    private fun duplicateAutomationRange(
        trackIndex: Int,
        lane: dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey,
        startMs: Long,
        endMs: Long
    ): TimelineCommandResult {
        if (endMs <= startMs) return TimelineCommandResult()

        return TimelineCommandResult(
            didChange = applyTrackStateChange(trackIndex) {
                duplicateAutomationRange(
                    key = lane,
                    startMs = startMs,
                    endMs = endMs
                )
            }
        )
    }

    private fun setAutomationLaneVisibility(
        trackIndex: Int,
        lane: dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey,
        visible: Boolean
    ): TimelineCommandResult {
        return TimelineCommandResult(
            didChange = applyTrackStateChange(trackIndex) {
                setAutomationLaneVisibility(
                    key = lane,
                    visible = visible
                )
            }
        )
    }

    private fun setAutomationLaneEnabled(
        trackIndex: Int,
        lane: dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey,
        enabled: Boolean
    ): TimelineCommandResult {
        return TimelineCommandResult(
            didChange = applyTrackStateChange(trackIndex) {
                setAutomationLaneEnabled(
                    key = lane,
                    enabled = enabled
                )
            }
        )
    }

    private fun applyAudioChange(trackIndex: Int, before: List<AudioEntry>, after: List<AudioEntry>): Boolean {
        if (audioEntriesMatch(before, after)) return false
        TimelineRepository.setTrackEntries(trackIndex, after)
        UndoManager.addAction(
            UndoableAction.TimelineChange(
                trackIndex = trackIndex,
                beforeEntries = before,
                afterEntries = after
            )
        )
        return true
    }

    private fun replaceEntryNotes(
        trackIndex: Int,
        entryStartTime: Long,
        changes: List<TimelineEditedNote>
    ): TimelineCommandResult {
        val effectiveChanges = changes
            .distinct()
            .filter { it.before != it.after }

        if (effectiveChanges.isEmpty()) return TimelineCommandResult()

        return updateMidiEntryNotes(trackIndex, entryStartTime) { entry ->
            val replacements = effectiveChanges.associateBy(TimelineEditedNote::before, TimelineEditedNote::after)
            if (replacements.isEmpty()) {
                entry
            } else {
                entry.copy(
                    notes = entry.notes.map { note ->
                        replacements[note] ?: note
                    }
                )
            }
        }
    }

    private fun updateMidiEntryNotes(
        trackIndex: Int,
        entryStartTime: Long,
        transform: (MidiEntry) -> MidiEntry
    ): TimelineCommandResult {
        val track = TimelineRepository.tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return TimelineCommandResult()
        val existing = track.entries[entryStartTime] ?: return TimelineCommandResult()
        val updatedEntry = normalizeMidiEntry(transform(existing))
        if (updatedEntry == existing) return TimelineCommandResult()

        val before = track.entries.sortedMidiEntriesCopy()
        val after = track.entries.toMutableMap().apply {
            this[entryStartTime] = updatedEntry
        }.sortedMidiEntriesCopy()

        return TimelineCommandResult(didChange = applyMidiChange(trackIndex, before, after))
    }

    private fun normalizeMidiEntry(entry: MidiEntry): MidiEntry {
        val maxNoteEnd = entry.notes.maxOfOrNull(MidiNote::endTimeMs) ?: entry.durationMs
        val normalizedDuration = maxOf(entry.durationMs, maxNoteEnd)
        return if (normalizedDuration == entry.durationMs) {
            entry
        } else {
            entry.copy(durationMs = normalizedDuration)
        }
    }

    private fun audioEntriesMatch(before: List<AudioEntry>, after: List<AudioEntry>): Boolean {
        if (before.size != after.size) return false

        return before.zip(after).all { (left, right) -> left.contentMatches(right) }
    }

    private fun AudioEntry.contentMatches(other: AudioEntry): Boolean {
        if (startTimeMs != other.startTimeMs) return false
        if (durationMs != other.durationMs) return false
        if (fileName != other.fileName) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (bitDepth != other.bitDepth) return false
        if (name != other.name) return false
        if (sourceId != other.sourceId) return false
        if (clipStartSample != other.clipStartSample) return false
        if (startTimeUs != other.startTimeUs) return false
        if (durationUs != other.durationUs) return false
        return clipEndSample == other.clipEndSample
    }

    private fun applyMidiChange(trackIndex: Int, before: List<MidiEntry>, after: List<MidiEntry>): Boolean {
        if (before == after) return false
        TimelineRepository.setMidiTrackEntries(trackIndex, after)
        UndoManager.addAction(
            UndoableAction.MidiTimelineChange(
                trackIndex = trackIndex,
                beforeEntries = before,
                afterEntries = after
            )
        )
        return true
    }

    private fun resolveOverlapForDuplicate(
        entries: MutableMap<Long, AudioEntry>,
        newEntry: AudioEntry,
        originStartMs: Long
    ): AudioEntry {
        val direction = newEntry.startTimeMs - originStartMs
        val toRemove = mutableListOf<Long>()
        val toReplace = mutableMapOf<Long, AudioEntry>()
        val toAdd = mutableListOf<AudioEntry>()
        val sorted = entries.values.sortedBy { it.startTimeMs }

        fun splitEntry(original: AudioEntry, splitStartMs: Long, splitEndMs: Long): Pair<AudioEntry?, AudioEntry?> {
            val left = if (original.startTimeMs < splitStartMs && original.endTimeMs > original.startTimeMs) {
                original.buildSegment(original.startTimeMs, splitStartMs)
            } else {
                null
            }
            val right = if (original.endTimeMs > splitEndMs) {
                original.buildSegment(splitEndMs, original.endTimeMs)
            } else {
                null
            }
            return left to right
        }

        if (direction >= 0) {
            sorted.forEach { existing ->
                val overlaps = existing.startTimeMs < newEntry.endTimeMs && existing.endTimeMs > newEntry.startTimeMs
                if (!overlaps) return@forEach

                val existingFullyInsideNew = existing.startTimeMs >= newEntry.startTimeMs && existing.endTimeMs <= newEntry.endTimeMs
                val newFullyInsideExisting = existing.startTimeMs <= newEntry.startTimeMs && existing.endTimeMs >= newEntry.endTimeMs
                val existingOverlapsAtStart =
                    existing.startTimeMs < newEntry.startTimeMs &&
                        existing.endTimeMs > newEntry.startTimeMs &&
                        existing.endTimeMs <= newEntry.endTimeMs
                val existingOverlapsAtEnd =
                    existing.startTimeMs >= newEntry.startTimeMs &&
                        existing.startTimeMs < newEntry.endTimeMs &&
                        existing.endTimeMs > newEntry.endTimeMs
                val existingSpansAcross = existing.startTimeMs < newEntry.startTimeMs && existing.endTimeMs > newEntry.endTimeMs

                when {
                    newFullyInsideExisting -> {
                        val (left, right) = splitEntry(existing, newEntry.startTimeMs, newEntry.endTimeMs)
                        toRemove += existing.startTimeMs
                        left?.let { toReplace[existing.startTimeMs] = it }
                        right?.let(toAdd::add)
                    }

                    existingFullyInsideNew -> toRemove += existing.startTimeMs
                    existingOverlapsAtStart -> {
                        existing.cropAudioEntryEnd(newEntry.startTimeMs)?.let {
                            toReplace[existing.startTimeMs] = it
                        } ?: run {
                            toRemove += existing.startTimeMs
                        }
                    }

                    existingOverlapsAtEnd -> {
                        toRemove += existing.startTimeMs
                        existing.buildSegment(newEntry.endTimeMs, existing.endTimeMs)?.let(toAdd::add)
                    }

                    existingSpansAcross -> {
                        val (left, right) = splitEntry(existing, newEntry.startTimeMs, newEntry.endTimeMs)
                        toRemove += existing.startTimeMs
                        left?.let { toReplace[existing.startTimeMs] = it }
                        right?.let(toAdd::add)
                    }
                }
            }
        }

        toRemove.forEach(entries::remove)
        toReplace.forEach { (key, value) -> entries[key] = value }
        toAdd.forEach { add -> entries[add.startTimeMs] = add }
        return newEntry
    }

    private fun deleteAudioEntriesInRange(track: AudioTimelineTrack, startMs: Long, endMs: Long): Boolean {
        val before = track.entries.sortedAudioEntriesCopy()
        val updatedEntries = track.entries.toMutableMap()
        val entriesToRemove = mutableListOf<Long>()
        val entriesToAdd = mutableListOf<AudioEntry>()

        track.entries.values.forEach { entry ->
            when {
                entry.startTimeMs >= startMs && entry.endTimeMs <= endMs -> {
                    entriesToRemove += entry.startTimeMs
                }

                entry.startTimeMs < startMs && entry.endTimeMs > endMs -> {
                    entriesToRemove += entry.startTimeMs
                    trimAudioEntry(entry, entry.startTimeMs, startMs)?.let(entriesToAdd::add)
                    trimAudioEntry(entry, endMs, entry.endTimeMs)?.let(entriesToAdd::add)
                }

                entry.startTimeMs < endMs && entry.endTimeMs > endMs -> {
                    entriesToRemove += entry.startTimeMs
                    trimAudioEntry(entry, endMs, entry.endTimeMs)?.let(entriesToAdd::add)
                }

                entry.startTimeMs < startMs && entry.endTimeMs > startMs -> {
                    entriesToRemove += entry.startTimeMs
                    trimAudioEntry(entry, entry.startTimeMs, startMs)?.let(entriesToAdd::add)
                }
            }
        }

        entriesToRemove.forEach(updatedEntries::remove)
        entriesToAdd.forEach { updatedEntries[it.startTimeMs] = it }
        val after = updatedEntries.sortedAudioEntriesCopy()
        if (audioEntriesMatch(before, after)) return false

        track.entries.clear()
        after.forEach { entry -> track.entries[entry.startTimeMs] = entry }
        return true
    }

    private fun deleteMidiEntriesInRange(track: MidiTimelineTrack, startMs: Long, endMs: Long): Boolean {
        val before = track.entries.sortedMidiEntriesCopy()
        val updatedEntries = track.entries.toMutableMap()
        val entriesToRemove = mutableListOf<Long>()
        val entriesToAdd = mutableListOf<MidiEntry>()

        track.entries.values.forEach { entry ->
            when {
                entry.startTimeMs >= startMs && entry.endTimeMs <= endMs -> {
                    entriesToRemove += entry.startTimeMs
                }

                entry.startTimeMs < startMs && entry.endTimeMs > endMs -> {
                    entriesToRemove += entry.startTimeMs
                    trimMidiEntry(entry, entry.startTimeMs, startMs)?.let(entriesToAdd::add)
                    trimMidiEntry(entry, endMs, entry.endTimeMs)?.let(entriesToAdd::add)
                }

                entry.startTimeMs < endMs && entry.endTimeMs > endMs -> {
                    entriesToRemove += entry.startTimeMs
                    trimMidiEntry(entry, endMs, entry.endTimeMs)?.let(entriesToAdd::add)
                }

                entry.startTimeMs < startMs && entry.endTimeMs > startMs -> {
                    entriesToRemove += entry.startTimeMs
                    trimMidiEntry(entry, entry.startTimeMs, startMs)?.let(entriesToAdd::add)
                }
            }
        }

        entriesToRemove.forEach(updatedEntries::remove)
        entriesToAdd.forEach { updatedEntries[it.startTimeMs] = it }
        val after = updatedEntries.sortedMidiEntriesCopy()
        if (before == after) return false

        track.entries.clear()
        after.forEach { entry -> track.entries[entry.startTimeMs] = entry }
        return true
    }

    private fun duplicateAudioEntriesInRange(
        track: AudioTimelineTrack,
        trackIndex: Int,
        startMs: Long,
        endMs: Long
    ): List<TimelineCreatedEntry> {
        val entriesInRange = getAudioEntriesInRange(track, startMs, endMs)
        if (entriesInRange.isEmpty()) return emptyList()

        val before = track.entries.sortedAudioEntriesCopy()
        val updatedEntries = track.entries.toMutableMap()
        val rangeStartUs = entriesInRange.minByOrNull { it.startTimeUs }?.startTimeUs ?: return emptyList()
        val createdEntries = mutableListOf<TimelineCreatedEntry>()

        entriesInRange.forEach { entry ->
            val offsetUs = entry.startTimeUs - rangeStartUs
            val duplicate = entry.copyWithPreciseTiming(startTimeUs = msToUs(endMs) + offsetUs)
            val resolved = resolveOverlapForDuplicate(updatedEntries, duplicate, entry.startTimeMs)
            updatedEntries[resolved.startTimeMs] = resolved
            createdEntries += TimelineCreatedEntry(trackIndex, resolved.startTimeMs)
        }

        val after = updatedEntries.sortedAudioEntriesCopy()
        if (audioEntriesMatch(before, after)) return emptyList()

        track.entries.clear()
        after.forEach { entry -> track.entries[entry.startTimeMs] = entry }
        return createdEntries
    }

    private fun duplicateMidiEntriesInRange(
        track: MidiTimelineTrack,
        trackIndex: Int,
        startMs: Long,
        endMs: Long
    ): List<TimelineCreatedEntry> {
        val entriesInRange = getMidiEntriesInRange(track, startMs, endMs)
        if (entriesInRange.isEmpty()) return emptyList()

        val before = track.entries.sortedMidiEntriesCopy()
        val updatedEntries = track.entries.toMutableMap()
        val rangeStart = entriesInRange.minByOrNull { it.startTimeMs }?.startTimeMs ?: return emptyList()
        val createdEntries = mutableListOf<TimelineCreatedEntry>()

        entriesInRange.forEach { entry ->
            val offset = entry.startTimeMs - rangeStart
            val duplicate = entry.copy(startTimeMs = endMs + offset)
            updatedEntries[duplicate.startTimeMs] = duplicate
            createdEntries += TimelineCreatedEntry(trackIndex, duplicate.startTimeMs)
        }

        val after = updatedEntries.sortedMidiEntriesCopy()
        if (before == after) return emptyList()

        track.entries.clear()
        after.forEach { entry -> track.entries[entry.startTimeMs] = entry }
        return createdEntries
    }

    private fun trimAudioEntry(entry: AudioEntry, trimStart: Long, trimEnd: Long): AudioEntry? {
        return entry.trimAudioEntry(trimStart, trimEnd)
    }

    private fun trimMidiEntry(entry: MidiEntry, trimStart: Long, trimEnd: Long): MidiEntry? {
        if (trimEnd <= trimStart) return null

        val offsetStart = trimStart - entry.startTimeMs
        val newDuration = trimEnd - trimStart
        val trimmedNotes = entry.notes.mapNotNull { note ->
            val noteStart = note.startTimeMs
            val noteEnd = note.startTimeMs + note.durationMs

            when {
                noteEnd <= offsetStart -> null
                noteStart >= offsetStart + newDuration -> null
                else -> {
                    val newNoteStart = maxOf(noteStart, offsetStart) - offsetStart
                    val newNoteEnd = minOf(noteEnd, offsetStart + newDuration) - offsetStart
                    val newNoteDuration = newNoteEnd - newNoteStart
                    if (newNoteDuration > 0) {
                        note.copy(startTimeMs = newNoteStart, durationMs = newNoteDuration)
                    } else {
                        null
                    }
                }
            }
        }

        return entry.copy(startTimeMs = trimStart, durationMs = newDuration, notes = trimmedNotes)
    }

    private fun Map<Long, AudioEntry>.sortedAudioEntriesCopy(): List<AudioEntry> {
        return values.sortedBy { it.startTimeMs }.map { it.copy() }
    }

    private fun Map<Long, MidiEntry>.sortedMidiEntriesCopy(): List<MidiEntry> {
        return values.sortedBy { it.startTimeMs }.map { it.copy() }
    }

    private fun applyTrackStateChange(
        trackIndex: Int,
        mutate: TimelineTrack<*>.() -> Boolean
    ): Boolean {
        val track = TimelineRepository.tracks.value.getOrNull(trackIndex) ?: return false
        val beforeTrack = track.deepCopy()
        val afterTrack = track.deepCopy()
        if (!afterTrack.mutate()) return false

        TimelineRepository.replaceTrack(trackIndex, afterTrack)
        UndoManager.addAction(
            UndoableAction.TrackStateChange(
                trackIndex = trackIndex,
                beforeTrack = beforeTrack,
                afterTrack = afterTrack,
                mergeable = false
            )
        )
        return true
    }
}
