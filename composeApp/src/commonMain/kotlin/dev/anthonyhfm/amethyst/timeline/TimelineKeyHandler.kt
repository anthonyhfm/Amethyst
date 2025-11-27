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
            val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
            if (entrySelections.isNotEmpty()) {
                ClipboardManager.copy(entrySelections)
                return true
            }
        }
        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.X) {
            val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
            if (entrySelections.isNotEmpty()) {
                // Copy first
                ClipboardManager.copy(entrySelections)
                
                // Then delete
                val grouped = entrySelections.groupBy { it.trackIndex }
                grouped.forEach { (trackIndex, selList) ->
                    val track = TimelineRepository.tracks.value.getOrNull(trackIndex)
                    
                    // Handle AudioTimelineTrack
                    if (track is AudioTimelineTrack) {
                        val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        selList.forEach { sel -> track.entries.remove(sel.entryStartMs) }
                        val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        val current = TimelineRepository.tracks.value.toMutableList()
                        val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
                        current[trackIndex] = newTrack
                        TimelineRepository.tracks.value = current.toList()
                        UndoManager.addAction(UndoableAction.TimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
                    }
                    // Handle MidiTimelineTrack and LightsTimelineTrack
                    else if (track is dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack || track is dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack) {
                        val midiTrack = track as dev.anthonyhfm.amethyst.timeline.data.TimelineTrack<dev.anthonyhfm.amethyst.timeline.data.MidiEntry>
                        val before = midiTrack.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        selList.forEach { sel -> midiTrack.entries.remove(sel.entryStartMs) }
                        val after = midiTrack.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        val current = TimelineRepository.tracks.value.toMutableList()
                        val newTrack = if (track is dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack) {
                            dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }
                        } else {
                            dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack().apply { entries.putAll(midiTrack.entries) }
                        }
                        current[trackIndex] = newTrack
                        TimelineRepository.tracks.value = current.toList()
                        UndoManager.addAction(UndoableAction.MidiTimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
                    }
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
            val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
            if (entrySelections.isNotEmpty()) {
                val grouped = entrySelections.groupBy { it.trackIndex }
                grouped.forEach { (trackIndex, selList) ->
                    val track = TimelineRepository.tracks.value.getOrNull(trackIndex)
                    
                    // Handle AudioTimelineTrack
                    if (track is AudioTimelineTrack) {
                        val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        selList.forEach { sel -> track.entries.remove(sel.entryStartMs) }
                        val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        val current = TimelineRepository.tracks.value.toMutableList()
                        val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
                        current[trackIndex] = newTrack
                        TimelineRepository.tracks.value = current.toList()
                        UndoManager.addAction(UndoableAction.TimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
                    }
                    // Handle MidiTimelineTrack and LightsTimelineTrack
                    else if (track is dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack || track is dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack) {
                        val midiTrack = track as dev.anthonyhfm.amethyst.timeline.data.TimelineTrack<dev.anthonyhfm.amethyst.timeline.data.MidiEntry>
                        val before = midiTrack.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        selList.forEach { sel -> midiTrack.entries.remove(sel.entryStartMs) }
                        val after = midiTrack.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        val current = TimelineRepository.tracks.value.toMutableList()
                        val newTrack = if (track is dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack) {
                            dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }
                        } else {
                            dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack().apply { entries.putAll(midiTrack.entries) }
                        }
                        current[trackIndex] = newTrack
                        TimelineRepository.tracks.value = current.toList()
                        UndoManager.addAction(UndoableAction.MidiTimelineChange(trackIndex, beforeEntries = before, afterEntries = after))
                    }
                }
                SelectionManager.clear()
                return true
            }
        }
        if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.D) {
            val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
            if (entrySelections.isNotEmpty()) {
                val grouped = entrySelections.groupBy { it.trackIndex }
                val newSelections = mutableListOf<Selectable>()
                grouped.forEach { (trackIndex, selList) ->
                    val track = TimelineRepository.tracks.value.getOrNull(trackIndex)
                    
                    // Handle AudioTimelineTrack
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
                    }
                    // Handle MidiTimelineTrack and LightsTimelineTrack
                    else if (track is dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack || track is dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack) {
                        val midiTrack = track as dev.anthonyhfm.amethyst.timeline.data.TimelineTrack<dev.anthonyhfm.amethyst.timeline.data.MidiEntry>
                        val before = midiTrack.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }

                        selList.sortedBy { it.entryStartMs }.forEach { sel ->
                            val original = midiTrack.entries[sel.entryStartMs] ?: return@forEach
                            val duplicateStart = original.endTimeMs
                            val duplicate = original.copy(startTimeMs = duplicateStart)
                            midiTrack.entries[duplicate.startTimeMs] = duplicate
                            newSelections += Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = duplicate.startTimeMs)
                        }
                        val after = midiTrack.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                        val current = TimelineRepository.tracks.value.toMutableList()
                        val newTrack = if (track is dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack) {
                            dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }
                        } else {
                            dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack().apply { entries.putAll(midiTrack.entries) }
                        }
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
}