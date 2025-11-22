package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.ScrollState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.utils.MidiImporter
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimelineViewModel : ViewModel() {
    private val _tracks = MutableStateFlow<List<TimelineTrack<*>>>(emptyList())
    val tracks: StateFlow<List<TimelineTrack<*>>> = _tracks.asStateFlow()

    private val _zoomLevel = MutableStateFlow(0.025f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _scrollState = MutableStateFlow<ScrollState?>(null)
    val scrollState: StateFlow<ScrollState?> = _scrollState.asStateFlow()

    val playheadPositionMs = TimelineRepository.playheadPositionMs
    val isPlaying = TimelineRepository.isPlaying

    init {
        initializeDemoData()
        viewModelScope.launch {
            TimelineRepository.tracks.collect { repoTracks ->
                _tracks.value = repoTracks
            }
        }

        viewModelScope.launch {
            WorkspaceRepository.gridType.collect { resnapTimelineSelections() }
        }
        viewModelScope.launch {
            WorkspaceRepository.bpm.collect { resnapTimelineSelections() }
        }
    }

    private fun resnapTimelineSelections() {
        val bpm = WorkspaceRepository.bpm.value
        val gridType = WorkspaceRepository.gridType.value
        val zoom = _zoomLevel.value
        val updated = SelectionManager.selections.value.map { sel ->
            when (sel) {
                is Selectable.TimelineTime -> {
                    val snapped = GridUtils.snapToGrid(sel.timeMs, zoom, bpm, gridType)
                    if (snapped != sel.timeMs) Selectable.TimelineTime(trackIndex = sel.trackIndex, timeMs = snapped) else sel
                }
                is Selectable.TimelineRange -> {
                    val newStart = GridUtils.snapToGrid(sel.startMs, zoom, bpm, gridType)
                    val newEnd = GridUtils.snapToGrid(sel.endMs, zoom, bpm, gridType).coerceAtLeast(newStart)
                    if (newStart != sel.startMs || newEnd != sel.endMs) Selectable.TimelineRange(trackIndex = sel.trackIndex, startMs = newStart, endMs = newEnd) else sel
                }
                else -> sel
            }
        }
        SelectionManager.selections.value = updated
    }

    private fun initializeDemoData() {
        _tracks.value = listOf(AudioTimelineTrack(), LightsTimelineTrack())

        _tracks.value.forEach { track ->
            TimelineRepository.addTrack(track)
        }
    }

    fun addAudioEntry(trackIndex: Int, audioEntry: AudioEntry) {
        viewModelScope.launch {
            val currentTracks = _tracks.value.toMutableList()
            if (trackIndex < currentTracks.size && currentTracks[trackIndex] is AudioTimelineTrack) {
                val track = currentTracks[trackIndex] as AudioTimelineTrack
                track.entries[audioEntry.startTimeMs] = audioEntry

                _tracks.value = currentTracks.toList()

                TimelineRepository.tracks.value = currentTracks.toList()
            }
        }
    }

    private fun snapToGrid(timeMs: Long, intervalMs: Long): Long {
        if (intervalMs <= 0) return timeMs.coerceAtLeast(0L)
        val q = timeMs.toDouble() / intervalMs.toDouble()
        return (kotlin.math.round(q) * intervalMs).toLong().coerceAtLeast(0L)
    }

    private fun cropEntryEnd(entry: AudioEntry, newEndMs: Long): AudioEntry? {
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

    private fun cropEntryEndInPlace(entry: AudioEntry, newEndMs: Long): AudioEntry? = cropEntryEnd(entry, newEndMs)

    private fun cropNewEntryEnd(newEntry: AudioEntry, newEndMs: Long): AudioEntry? {
        if (newEndMs <= newEntry.startTimeMs) return null
        val newDuration = newEndMs - newEntry.startTimeMs
        val raw = newEntry.rawData
        val croppedData = if (raw != null && raw.isNotEmpty()) {
            val bytesPerSample = (newEntry.bitDepth / 8) * newEntry.channels
            val samplesPerMs = newEntry.sampleRate.toDouble() / 1000.0
            val totalSamplesExact = newDuration * samplesPerMs
            val totalSamples = kotlin.math.round(totalSamplesExact).toLong()
            val totalBytes = (totalSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
            raw.sliceArray(0 until totalBytes)
        } else raw
        return newEntry.copy(durationMs = newDuration, rawData = croppedData)
    }

    private fun sliceAudio(entry: AudioEntry, startMs: Long, endMs: Long): ByteArray? {
        val raw = entry.rawData ?: return null
        val safeStart = startMs.coerceAtLeast(entry.startTimeMs)
        val safeEnd = endMs.coerceAtMost(entry.endTimeMs)
        if (safeEnd <= safeStart) return ByteArray(0)
        val bytesPerSample = (entry.bitDepth / 8) * entry.channels
        val samplesPerMs = entry.sampleRate.toDouble() / 1000.0
        val startSamplesExact = (safeStart - entry.startTimeMs) * samplesPerMs
        val endSamplesExact = (safeEnd - entry.startTimeMs) * samplesPerMs
        val startSamples = kotlin.math.round(startSamplesExact).toLong().coerceAtLeast(0)
        val endSamples = kotlin.math.round(endSamplesExact).toLong().coerceAtLeast(startSamples)
        val startByte = (startSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
        val endByte = (endSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
        if (startByte >= endByte) return ByteArray(0)
        return raw.sliceArray(startByte until endByte)
    }

    private fun buildEntrySegment(original: AudioEntry, segStartMs: Long, segEndMs: Long): AudioEntry? {
        if (segEndMs <= segStartMs) return null
        val data = sliceAudio(original, segStartMs, segEndMs)
        return original.copy(
            startTimeMs = segStartMs,
            durationMs = segEndMs - segStartMs,
            rawData = data
        )
    }

    private fun splitEntry(original: AudioEntry, splitStartMs: Long, splitEndMs: Long): Pair<AudioEntry?, AudioEntry?> {
        val left = if (original.startTimeMs < splitStartMs && original.endTimeMs > splitStartMs) {
            buildEntrySegment(original, original.startTimeMs, splitStartMs)
        } else null

        val right = if (original.endTimeMs > splitEndMs && original.startTimeMs < splitEndMs) {
            buildEntrySegment(original, splitEndMs, original.endTimeMs)
        } else null
        return left to right
    }

    private fun resolveOverlapAsymmetric(
        track: AudioTimelineTrack,
        newEntry: AudioEntry,
        originStartMs: Long
    ): AudioEntry? {
        val direction = newEntry.startTimeMs - originStartMs
        var adjustedNew = newEntry
        val toRemove = mutableListOf<Long>()
        val toReplace = mutableMapOf<Long, AudioEntry>()
        val toAdd = mutableListOf<AudioEntry>()

        val sorted = track.entries.values.sortedBy { it.startTimeMs }
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
                        left?.let { toReplace[it.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }

                    existingFullyInsideNew -> {
                        toRemove.add(existing.startTimeMs)
                    }

                    existingOverlapsAtStart -> {
                        cropEntryEndInPlace(existing, adjustedNew.startTimeMs)?.let { toReplace[existing.startTimeMs] = it } ?: toRemove.add(existing.startTimeMs)
                    }

                    existingOverlapsAtEnd -> {
                        val tailStart = adjustedNew.endTimeMs
                        val tailEnd = existing.endTimeMs
                        toRemove.add(existing.startTimeMs)

                        buildEntrySegment(existing, tailStart, tailEnd)?.let { toAdd.add(it) }
                    }

                    existingSpansAcross -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)
                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[it.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }
                }
            }
        } else {
            var earliestBlockingStart: Long? = null
            val earlierClips = mutableListOf<AudioEntry>()
            val laterClips = mutableListOf<AudioEntry>()

            sorted.forEach { existing ->
                val overlaps = existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.startTimeMs
                if (!overlaps) return@forEach
                if (existing.startTimeMs < originStartMs) {
                    earlierClips.add(existing)
                } else {
                    laterClips.add(existing)
                }
            }

            earlierClips.forEach { existing ->
                val existingFullyInsideNew = existing.startTimeMs >= adjustedNew.startTimeMs && existing.endTimeMs <= adjustedNew.endTimeMs
                val newFullyInsideExisting = existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs >= adjustedNew.endTimeMs
                val existingOverlapsAtStart = existing.startTimeMs < adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.startTimeMs && existing.endTimeMs <= adjustedNew.endTimeMs
                val existingOverlapsAtEnd = existing.startTimeMs >= adjustedNew.startTimeMs && existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.endTimeMs
                val existingSpansAcross = existing.startTimeMs < adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.endTimeMs
                when {
                    newFullyInsideExisting -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)
                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[it.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }
                    existingFullyInsideNew -> {
                        toRemove.add(existing.startTimeMs)
                    }
                    existingOverlapsAtStart -> {
                        cropEntryEndInPlace(existing, adjustedNew.startTimeMs)?.let { toReplace[existing.startTimeMs] = it } ?: toRemove.add(existing.startTimeMs)
                    }
                    existingOverlapsAtEnd -> {
                        val tailStart = adjustedNew.endTimeMs
                        val tailEnd = existing.endTimeMs
                        toRemove.add(existing.startTimeMs)
                        buildEntrySegment(existing, tailStart, tailEnd)?.let { toAdd.add(it) }
                    }
                    existingSpansAcross -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)
                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[it.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }
                }
            }

            for (existing in laterClips) {
                if (existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.startTimeMs) {
                    return null
                }

                if (existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs >= adjustedNew.endTimeMs) {
                    return null
                }

                if (existing.startTimeMs > adjustedNew.startTimeMs && existing.startTimeMs < adjustedNew.endTimeMs) {
                    if (earliestBlockingStart == null || existing.startTimeMs < earliestBlockingStart) {
                        earliestBlockingStart = existing.startTimeMs
                    }
                }
            }
            if (earliestBlockingStart != null) {
                cropNewEntryEnd(adjustedNew, earliestBlockingStart)?.let { adjustedNew = it } ?: return null
            }
        }

        toRemove.forEach { track.entries.remove(it) }
        toReplace.forEach { (k, v) -> track.entries[k] = v }
        toAdd.forEach { add -> track.entries[add.startTimeMs] = add }
        return adjustedNew
    }

    private fun snapshotAudioEntries(track: AudioTimelineTrack): List<AudioEntry> =
        track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }

    fun addAudioFileToTrack(trackIndex: Int, file: PlatformFile, at: Long = 0) {
        viewModelScope.launch {
            val currentTracks = _tracks.value.toMutableList()
            val track = currentTracks.getOrNull(trackIndex) as? AudioTimelineTrack ?: return@launch
            val before = snapshotAudioEntries(track)
            track.addFromFile(file, at)
            val original = track.entries[at] ?: return@launch
            val bpm = WorkspaceRepository.bpm.value
            val gridType = WorkspaceRepository.gridType.value
            val intervals = GridUtils.computeWithGridType(_zoomLevel.value, bpm, gridType)
            val gridInterval = intervals.intervalMs
            val snappedStart = snapToGrid(original.startTimeMs, gridInterval)
            val newEntry = if (snappedStart != original.startTimeMs) {
                track.entries.remove(original.startTimeMs)
                original.copy(startTimeMs = snappedStart)
            } else original
            val resolved = resolveOverlapAsymmetric(track, newEntry, originStartMs = original.startTimeMs) ?: return@launch
            track.entries[resolved.startTimeMs] = resolved
            val after = snapshotAudioEntries(track)
            val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
            currentTracks[trackIndex] = newTrack
            _tracks.value = currentTracks.toList()
            TimelineRepository.tracks.value = currentTracks.toList()
            SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = resolved.startTimeMs))
            UndoManager.addAction(UndoableAction.TimelineChange(trackIndex = trackIndex, beforeEntries = before, afterEntries = after))
        }
    }

    fun setZoomLevel(zoom: Float) {
        val clamped = zoom.coerceIn(0.01f, 10.0f)
        _zoomLevel.value = clamped
    }

    fun zoomBy(factor: Float) {
        val newZoom = _zoomLevel.value * factor
        setZoomLevel(newZoom)
    }

    fun msToPixels(timeMs: Long): Float {
        // Use Double precision for better accuracy
        return (timeMs.toDouble() * _zoomLevel.value.toDouble()).toFloat()
    }

    fun pixelsToMs(pixels: Float): Long {
        // Use Double precision for better accuracy
        return (pixels.toDouble() / _zoomLevel.value.toDouble()).toLong()
    }

    fun setScrollState(scrollState: ScrollState) {
        _scrollState.value = scrollState
    }

    fun moveAudioEntry(trackIndex: Int, oldStartMs: Long, newStartMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? AudioTimelineTrack ?: return
        val before = snapshotAudioEntries(track)
        val entry = track.entries.remove(oldStartMs) ?: return
        val bpm = WorkspaceRepository.bpm.value
        val gridType = WorkspaceRepository.gridType.value
        val intervals = GridUtils.computeWithGridType(_zoomLevel.value, bpm, gridType)
        val gridInterval = intervals.intervalMs
        val isAlreadySnapped = gridInterval > 0 && newStartMs % gridInterval == 0L
        val snappedStart = if (isAlreadySnapped) newStartMs else snapToGrid(newStartMs, gridInterval)
        println("[TimelineViewModel] moveAudioEntry: old=$oldStartMs requested=$newStartMs snapped=$snappedStart gridInterval=$gridInterval alreadySnapped=$isAlreadySnapped")
        val movedEntry = entry.copy(startTimeMs = snappedStart)
        val resolved = resolveOverlapAsymmetric(track, movedEntry, originStartMs = oldStartMs) ?: run {
            println("[TimelineViewModel] moveAudioEntry: overlap resolution failed, restoring original")
            track.entries[oldStartMs] = entry
            return
        }
        track.entries[resolved.startTimeMs] = resolved
        val after = snapshotAudioEntries(track)
        val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = resolved.startTimeMs))
        UndoManager.addAction(UndoableAction.TimelineChange(trackIndex = trackIndex, beforeEntries = before, afterEntries = after))
    }

    fun deleteAudioEntry(trackIndex: Int, entryStartMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? AudioTimelineTrack ?: return
        val original = track.entries.remove(entryStartMs) ?: return
        val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
        val current = _tracks.value.toMutableList(); current[trackIndex] = newTrack
        _tracks.value = current.toList(); TimelineRepository.tracks.value = current.toList()
        UndoManager.addAction(UndoableAction.TimelineClipDeletion(trackIndex, deleted = original))
    }

    // ========== MIDI Track Operations ==========

    /**
     * Add an audio track to the timeline
     */
    fun addAudioTrack() {
        viewModelScope.launch {
            val newTrack = AudioTimelineTrack()
            val currentTracks = _tracks.value.toMutableList()
            currentTracks.add(newTrack)
            _tracks.value = currentTracks.toList()
            TimelineRepository.addTrack(newTrack)
        }
    }

    /**
     * Add a lights track to the timeline
     */
    fun addLightsTrack() {
        viewModelScope.launch {
            val newTrack = LightsTimelineTrack()
            val currentTracks = _tracks.value.toMutableList()
            currentTracks.add(newTrack)
            _tracks.value = currentTracks.toList()
            TimelineRepository.addTrack(newTrack)
        }
    }

    /**
     * Add a MIDI track to the timeline
     */
    fun addMidiTrack() {
        viewModelScope.launch {
            val newTrack = MidiTimelineTrack()
            val currentTracks = _tracks.value.toMutableList()
            currentTracks.add(newTrack)
            _tracks.value = currentTracks.toList()
            TimelineRepository.addTrack(newTrack)
        }
    }

    /**
     * Add a MIDI entry to a track
     */
    fun addMidiEntry(trackIndex: Int, entry: MidiEntry) {
        viewModelScope.launch {
            val currentTracks = _tracks.value.toMutableList()
            val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return@launch
            
            track.addEntry(entry)
            
            // Update tracks
            val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
            currentTracks[trackIndex] = newTrack
            _tracks.value = currentTracks.toList()
            TimelineRepository.tracks.value = currentTracks.toList()
            
            SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = entry.startTimeMs))
        }
    }

    /**
     * Add a MIDI note to an entry
     */
    fun addMidiNote(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        
        val entry = track.entries[entryStartMs]
        if (entry != null) {
            // Add note to existing entry
            val updatedEntry = entry.copy(notes = entry.notes + note)
            track.entries[entryStartMs] = updatedEntry
            
            // Update timeline
            val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
            currentTracks[trackIndex] = newTrack
            _tracks.value = currentTracks.toList()
            TimelineRepository.tracks.value = currentTracks.toList()
        }
    }

    /**
     * Update a MIDI note in an entry
     */
    fun updateMidiNote(trackIndex: Int, entryStartMs: Long, oldNote: MidiNote, newNote: MidiNote) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        
        track.updateNote(entryStartMs, oldNote, newNote)
        
        val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
    }

    /**
     * Delete a MIDI note from an entry
     */
    fun deleteMidiNote(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        
        track.removeNote(entryStartMs, note)
        
        val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
    }

    /**
     * Delete a MIDI entry
     */
    fun deleteMidiEntry(trackIndex: Int, entryStartMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        val original = track.entries.remove(entryStartMs) ?: return
        val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
        val current = _tracks.value.toMutableList(); current[trackIndex] = newTrack
        _tracks.value = current.toList(); TimelineRepository.tracks.value = current.toList()
        // Note: UndoableAction would need to be extended to support MIDI entries
    }

    /**
     * Import a MIDI file and add it to a track
     */
    fun addMidiFileToTrack(trackIndex: Int, file: PlatformFile, at: Long = 0) {
        viewModelScope.launch {
            val currentTracks = _tracks.value.toMutableList()
            val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return@launch
            
            // Import MIDI file
            val midiEntry = MidiImporter.importMidiFile(file, at) ?: run {
                println("Failed to import MIDI file: ${file.name}")
                return@launch
            }
            
            // Snap to grid if needed
            val bpm = WorkspaceRepository.bpm.value
            val gridType = WorkspaceRepository.gridType.value
            val intervals = GridUtils.computeWithGridType(_zoomLevel.value, bpm, gridType)
            val gridInterval = intervals.intervalMs
            val snappedStart = snapToGrid(midiEntry.startTimeMs, gridInterval)
            
            val snappedEntry = if (snappedStart != midiEntry.startTimeMs) {
                midiEntry.copy(startTimeMs = snappedStart)
            } else {
                midiEntry
            }
            
            track.addEntry(snappedEntry)
            
            // Update tracks
            val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
            currentTracks[trackIndex] = newTrack
            _tracks.value = currentTracks.toList()
            TimelineRepository.tracks.value = currentTracks.toList()
            
            SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = snappedEntry.startTimeMs))
        }
    }

    /**
     * Handle double-click on lights track to create a new MIDI clip or open existing one
     */
    fun onDoubleClickLightsTrack(trackIndex: Int, timeMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? LightsTimelineTrack ?: return
        
        // Check if there's an existing entry at this time
        val existingEntry = track.entries.values.firstOrNull { entry ->
            timeMs >= entry.startTimeMs && timeMs < entry.endTimeMs
        }
        
        if (existingEntry != null) {
            // Open Piano Roll for existing entry
            openPianoRollForEntry(trackIndex, existingEntry)
        } else {
            // Create a new empty MIDI entry at the double-click position
            val defaultDuration = 4000L // 4 seconds default
            val newEntry = MidiEntry(
                startTimeMs = timeMs,
                durationMs = defaultDuration,
                notes = emptyList(),
                name = "Lights Clip"
            )
            
            track.addEntry(newEntry)
            
            val currentTracks = _tracks.value.toMutableList()
            val newTrack = LightsTimelineTrack().apply { entries.putAll(track.entries) }
            currentTracks[trackIndex] = newTrack
            _tracks.value = currentTracks.toList()
            TimelineRepository.tracks.value = currentTracks.toList()
            
            SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = newEntry.startTimeMs))
            
            println("Created new lights clip at ${timeMs}ms on track $trackIndex")
            
            // Open Piano Roll for the new entry
            openPianoRollForEntry(trackIndex, newEntry)
        }
    }

    /**
     * Open Piano Roll workspace mode for editing a MIDI entry
     */
    private fun openPianoRollForEntry(trackIndex: Int, entry: MidiEntry) {
        val pianoRollMode = PianoRollWorkspaceMode()

        pianoRollMode.currentEntry = entry
        pianoRollMode.trackIndex = trackIndex
        pianoRollMode.entryStartMs = entry.startTimeMs
        pianoRollMode.onNoteAdd = { note ->
            addNoteToPianoRoll(trackIndex, entry.startTimeMs, note)
        }
        pianoRollMode.onNoteUpdate = { old, new ->
            updateNoteInPianoRoll(trackIndex, entry.startTimeMs, old, new)
        }
        pianoRollMode.onNoteDelete = { note ->
            deleteNoteFromPianoRoll(trackIndex, entry.startTimeMs, note)
        }
        pianoRollMode.modeClose = { WorkspaceRepository.switchToPreviousMode() }
        WorkspaceRepository.switchMode(pianoRollMode)
        println("Opened Piano Roll for entry at ${entry.startTimeMs}ms on track $trackIndex")
    }

    private fun addNoteToPianoRoll(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        addMidiNoteLive(trackIndex, entryStartMs, note)
    }
    private fun updateNoteInPianoRoll(trackIndex: Int, entryStartMs: Long, old: MidiNote, new: MidiNote) {
        updateMidiNoteLive(trackIndex, entryStartMs, old, new)
    }
    private fun deleteNoteFromPianoRoll(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        deleteMidiNoteLive(trackIndex, entryStartMs, note)
    }

    /**
     * Move a MIDI entry to a new position
     */
    fun moveMidiEntry(trackIndex: Int, oldStartMs: Long, newStartMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        
        if (track !is MidiTimelineTrack && track !is LightsTimelineTrack) return
        
        val midiTrack = track as TimelineTrack<MidiEntry>
        val entry = midiTrack.entries.remove(oldStartMs) ?: return
        
        val bpm = WorkspaceRepository.bpm.value
        val gridType = WorkspaceRepository.gridType.value
        val intervals = GridUtils.computeWithGridType(_zoomLevel.value, bpm, gridType)
        val gridInterval = intervals.intervalMs
        val snappedStart = snapToGrid(newStartMs, gridInterval)
        
        val movedEntry = entry.copy(startTimeMs = snappedStart)
        midiTrack.entries[snappedStart] = movedEntry
        
        // Update track
        val newTrack = if (track is MidiTimelineTrack) {
            MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }
        } else {
            LightsTimelineTrack().apply { entries.putAll(midiTrack.entries) }
        }
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
        
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = snappedStart))
    }

    /**
     * Duplicate a MIDI entry
     */
    fun duplicateMidiEntry(trackIndex: Int, entryStartMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        
        if (track !is MidiTimelineTrack && track !is LightsTimelineTrack) return
        
        val midiTrack = track as TimelineTrack<MidiEntry>
        val entry = midiTrack.entries[entryStartMs] ?: return
        
        // Place duplicate right after the original
        val newStartMs = entry.endTimeMs
        val duplicatedEntry = entry.copy(startTimeMs = newStartMs)
        midiTrack.entries[newStartMs] = duplicatedEntry
        
        // Update track
        val newTrack = if (track is MidiTimelineTrack) {
            MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }
        } else {
            LightsTimelineTrack().apply { entries.putAll(midiTrack.entries) }
        }
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
        
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = newStartMs))
    }

    private fun updateMidiEntry(trackIndex: Int, entryStartMs: Long, transform: (MidiEntry) -> MidiEntry) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        val isMidi = track is MidiTimelineTrack || track is LightsTimelineTrack
        if (!isMidi) return
        val midiTrack = track as TimelineTrack<MidiEntry>
        val entry = midiTrack.entries[entryStartMs] ?: return
        val updated = transform(entry)
        midiTrack.entries[entryStartMs] = updated
        // Adjust duration if notes extend beyond clip
        val maxEnd = updated.notes.maxOfOrNull { it.endTimeMs } ?: updated.durationMs
        val newDuration = maxEnd.coerceAtLeast(updated.durationMs)
        if (newDuration != updated.durationMs) {
            midiTrack.entries[entryStartMs] = updated.copy(durationMs = newDuration)
        }
        val newTrackInstance = when (track) {
            is MidiTimelineTrack -> MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }
            is LightsTimelineTrack -> LightsTimelineTrack().apply { entries.putAll(midiTrack.entries) }
            else -> return
        }
        currentTracks[trackIndex] = newTrackInstance
        _tracks.value = currentTracks.toList(); TimelineRepository.tracks.value = currentTracks.toList()
        // Update Piano Roll mode entry if open
        val mode = WorkspaceRepository.mode.value
        if (mode is PianoRollWorkspaceMode && mode.trackIndex == trackIndex && mode.entryStartMs == entryStartMs) {
            mode.currentEntry = newTrackInstance.entries[entryStartMs]
        }
    }

    fun updateMidiEntryNotes(trackIndex: Int, entryStartMs: Long, newNotes: List<MidiNote>) {
        updateMidiEntry(trackIndex, entryStartMs) { it.copy(notes = newNotes) }
    }

    // Override addMidiNote to reuse logic
    fun addMidiNoteLive(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        updateMidiEntry(trackIndex, entryStartMs) { it.copy(notes = it.notes + note) }
    }

    fun updateMidiNoteLive(trackIndex: Int, entryStartMs: Long, oldNote: MidiNote, newNote: MidiNote) {
        updateMidiEntry(trackIndex, entryStartMs) { it.copy(notes = it.notes.map { n -> if (n == oldNote) newNote else n }) }
    }

    fun deleteMidiNoteLive(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        updateMidiEntry(trackIndex, entryStartMs) { it.copy(notes = it.notes.filter { n -> n != note }) }
    }
}
