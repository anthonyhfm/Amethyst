package dev.anthonyhfm.amethyst.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.ChainEffectEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.data.buildSegment
import dev.anthonyhfm.amethyst.timeline.data.copyWithShiftedStartMs
import dev.anthonyhfm.amethyst.timeline.data.cropAudioEntryEnd
import dev.anthonyhfm.amethyst.timeline.data.deepCopy
import dev.anthonyhfm.amethyst.timeline.data.timelineTrackRows
import dev.anthonyhfm.amethyst.timeline.data.trimAudioEntry
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipContext
import dev.anthonyhfm.amethyst.timeline.contract.TimelineTimingContext
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.toTimelineEntrySelection
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.util.randomUUID
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineTriggerable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimelineViewModel : ViewModel() {
    private val _tracks = MutableStateFlow<List<TimelineTrack<*>>>(emptyList())
    val tracks: StateFlow<List<TimelineTrack<*>>> = _tracks.asStateFlow()

    // Single authoritative viewport state — zoom and scroll are always emitted together so
    // renderers never observe a mismatched (newScroll, oldZoom) combination.
    private val _viewport = MutableStateFlow(EditorViewportState(zoomX = 0.025f))
    val viewport: StateFlow<EditorViewportState> = _viewport.asStateFlow()

    val playheadPositionMs = TimelineRepository.playheadPositionMs
    val isPlaying = TimelineRepository.isPlaying

    private val _openChainEffectClipId = MutableStateFlow<String?>(null)
    val openChainEffectClipId: StateFlow<String?> = _openChainEffectClipId.asStateFlow()

    init {
        TimelineKeyHandler.closeChainEffectPanel = {
            if (_openChainEffectClipId.value == null) false else {
                if (SelectionManager.selections.value.isNotEmpty()) {
                    SelectionManager.clear()
                    true
                } else {
                    closeChainEffect()
                    true
                }
            }
        }
        TimelineKeyHandler.deleteChainEffectClip = ::deleteChainEffect
        TimelineKeyHandler.duplicateChainEffectClip = { trackIndex, clipId ->
            duplicateChainEffect(trackIndex, clipId)
        }
        TimelineKeyHandler.nudgeTimelineTime = ::nudgeSelectedTimelineTime
        viewModelScope.launch {
            TimelineRepository.tracks.collect { repoTracks ->
                _tracks.value = repoTracks
                pruneTimelineSelections(repoTracks)
            }
        }

        viewModelScope.launch {
            WorkspaceRepository.gridType.collect { resnapTimelineSelections() }
        }
        viewModelScope.launch {
            WorkspaceRepository.bpm.collect {
                resnapTimelineSelections()
                TimelineRepository.refreshChainEffectDurations()
            }
        }
    }

    private fun currentTimingContext(): TimelineTimingContext {
        return TimelineTimingContext(
            bpm = WorkspaceRepository.bpm.value,
            gridType = WorkspaceRepository.gridType.value,
            zoomLevel = _viewport.value.zoomX,
            playheadPositionMs = TimelineRepository.playheadPositionMs.value,
            isPlaying = TimelineRepository.isPlaying.value
        )
    }

    private fun publishTrackSnapshot(updatedTracks: List<TimelineTrack<*>>) {
        TimelineRepository.updateTracksSnapshot(updatedTracks)
        _tracks.value = TimelineRepository.tracks.value
        pruneTimelineSelections(_tracks.value)
    }

    private fun pruneTimelineSelections(tracks: List<TimelineTrack<*>>) {
        val visibleTrackIndices = tracks.timelineTrackRows().mapTo(mutableSetOf()) { it.trackIndex }
        val filteredSelections = SelectionManager.selections.value.filter { selection ->
            when (selection) {
                is Selectable.TimelineAutomationLane -> {
                    val track = tracks.getOrNull(selection.trackIndex)
                    selection.trackIndex in visibleTrackIndices &&
                        track?.automationLane(selection.laneKey) != null
                }

                is Selectable.TimelineAutomationPoint -> {
                    val track = tracks.getOrNull(selection.trackIndex)
                    selection.trackIndex in visibleTrackIndices &&
                        track?.automationLane(selection.laneKey)?.point(selection.pointId) != null
                }

                is Selectable.TimelineEntryItem -> {
                    when (val track = tracks.getOrNull(selection.trackIndex)) {
                        is AudioTimelineTrack -> {
                            selection.trackIndex in visibleTrackIndices &&
                                track.entries.containsKey(selection.entryStartMs)
                        }

                        is MidiTimelineTrack -> {
                            selection.trackIndex in visibleTrackIndices &&
                                if (selection.clipId != null) {
                                    track.chainEffectEntries.values.any { it.clipId == selection.clipId }
                                } else {
                                    track.entries.containsKey(selection.entryStartMs)
                                }
                        }

                        else -> false
                    }
                }

                is Selectable.TimelineRange -> selection.trackIndex in visibleTrackIndices
                is Selectable.TimelineTime -> selection.trackIndex in visibleTrackIndices
                is Selectable.TimelineTrack -> selection.trackIndex in visibleTrackIndices
                is Selectable.PianoRollNote -> {
                    val track = tracks.getOrNull(selection.trackIndex) as? MidiTimelineTrack
                    // Match by identity (startTimeMs + pitch) rather than full structural equality.
                    // LED/gradient edits update the SelectionManager first, then the timeline note
                    // by note; full equality would incorrectly prune still-pending selections.
                    selection.trackIndex in visibleTrackIndices &&
                        track?.entries?.get(selection.entryStartMs)?.notes?.any {
                            it.startTimeMs == selection.note.startTimeMs && it.pitch == selection.note.pitch
                        } == true
                }

                else -> true
            }
        }

        if (filteredSelections != SelectionManager.selections.value) {
            SelectionManager.replaceSelections(filteredSelections)
        }
    }

    private fun resnapTimelineSelections() {
        val bpm = WorkspaceRepository.bpm.value
        val gridType = WorkspaceRepository.gridType.value
        val zoom = _viewport.value.zoomX
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
        SelectionManager.replaceSelections(updated)
    }

    private fun snapToGrid(timeMs: Long, intervalMs: Long): Long {
        if (intervalMs <= 0) return timeMs.coerceAtLeast(0L)
        val q = timeMs.toDouble() / intervalMs.toDouble()
        return (kotlin.math.round(q) * intervalMs).toLong().coerceAtLeast(0L)
    }

    private fun cropEntryEnd(entry: AudioEntry, newEndMs: Long): AudioEntry? = entry.cropAudioEntryEnd(newEndMs)

    private fun cropEntryEndInPlace(entry: AudioEntry, newEndMs: Long): AudioEntry? = cropEntryEnd(entry, newEndMs)

    private fun cropNewEntryEnd(newEntry: AudioEntry, newEndMs: Long): AudioEntry? = newEntry.cropAudioEntryEnd(newEndMs)

    private fun buildEntrySegment(original: AudioEntry, segStartMs: Long, segEndMs: Long): AudioEntry? =
        original.buildSegment(segStartMs, segEndMs)

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

    private fun snapshotMidiEntries(track: MidiTimelineTrack): List<MidiEntry> =
        track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }

    private data class PendingTrackStateChange(
        val trackIndex: Int,
        val beforeTrack: TimelineTrack<*>,
        val afterTrack: TimelineTrack<*>
    )

    private fun cloneTimelineTrack(track: TimelineTrack<*>): TimelineTrack<*> = track.deepCopy()

    private fun updateTrackState(
        trackIndex: Int,
        mutate: TimelineTrack<*>.() -> Boolean
    ) {
        val track = _tracks.value.getOrNull(trackIndex) ?: return
        val beforeTrack = cloneTimelineTrack(track)
        val afterTrack = cloneTimelineTrack(track)
        if (!afterTrack.mutate()) return

        TimelineRepository.replaceTrack(trackIndex, afterTrack)
        _tracks.value = TimelineRepository.tracks.value
        pruneTimelineSelections(_tracks.value)
        UndoManager.addAction(
            UndoableAction.TrackStateChange(
                trackIndex = trackIndex,
                beforeTrack = beforeTrack,
                afterTrack = afterTrack
            )
        )
    }

    fun addAudioFileToTrack(trackIndex: Int, file: PlatformFile, at: Long = 0) {
        viewModelScope.launch {
            val currentTracks = _tracks.value.toMutableList()
            val track = currentTracks.getOrNull(trackIndex) as? AudioTimelineTrack ?: return@launch
            val before = snapshotAudioEntries(track)
            track.addFromFile(file, at)
            val original = track.entries[at] ?: return@launch
            val bpm = WorkspaceRepository.bpm.value
            val gridType = WorkspaceRepository.gridType.value
            val snappedStart = if (gridType is GridUtils.GridType.NoGrid) {
                original.startTimeMs
            } else {
                val intervals = GridUtils.computeWithGridType(_viewport.value.zoomX, bpm, gridType)
                snapToGrid(original.startTimeMs, intervals.intervalMs)
            }
            val newEntry = if (snappedStart != original.startTimeMs) {
                track.entries.remove(original.startTimeMs)
                original.copyWithShiftedStartMs(snappedStart)
            } else original
            val resolved = resolveOverlapAsymmetric(track, newEntry, originStartMs = original.startTimeMs) ?: return@launch
            track.entries[resolved.startTimeMs] = resolved
            val after = snapshotAudioEntries(track)
            val newTrack = track.copyWithEntries()
            currentTracks[trackIndex] = newTrack
            _tracks.value = currentTracks.toList()
            TimelineRepository.tracks.value = currentTracks.toList()
            SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = resolved.startTimeMs))
            UndoManager.addAction(UndoableAction.TimelineChange(trackIndex = trackIndex, beforeEntries = before, afterEntries = after))
        }
    }

    /** Update zoom and scroll atomically to prevent mismatched recompositions. */
    fun updateViewport(vp: EditorViewportState) {
        _viewport.value = vp
    }

    /** Read-modify-write against the latest viewport snapshot. */
    fun updateViewport(transform: (EditorViewportState) -> EditorViewportState) {
        _viewport.update(transform)
    }

    fun setZoomLevel(zoom: Float) {
        val clamped = zoom.coerceIn(0.01f, 10.0f)
        _viewport.value = _viewport.value.copy(zoomX = clamped)
    }

    fun zoomBy(factor: Float) {
        val before = _viewport.value.zoomX
        val requested = before * factor
        val clamped = requested.coerceIn(0.01f, 10.0f)
        _viewport.value = _viewport.value.copy(zoomX = clamped)
    }

    fun msToPixels(timeMs: Long): Float {
        // Use Double precision for better accuracy
        val px = (timeMs.toDouble() * _viewport.value.zoomX.toDouble()).toFloat()
        return px
    }

    fun pixelsToMs(pixels: Float): Long {
        // Use Double precision for better accuracy
        val ms = (pixels.toDouble() / _viewport.value.zoomX.toDouble()).toLong()
        return ms
    }

    fun setScrollOffset(offsetPx: Float) {
        _viewport.value = _viewport.value.copy(scrollX = offsetPx)
    }

    fun renameTrack(trackIndex: Int, newName: String) {
        val normalizedName = newName.trim()
        val track = _tracks.value.getOrNull(trackIndex) ?: return
        if (track.name == normalizedName) return

        UndoManager.addAction(
            UndoableAction.TrackRename(
                trackIndex = trackIndex,
                oldName = track.name,
                newName = normalizedName
            )
        )
        TimelineRepository.renameTrack(trackIndex, normalizedName)
        _tracks.value = TimelineRepository.tracks.value
    }

    fun reorderTracks(fromIndex: Int, toIndex: Int) {
        TimelineCommandExecutor.execute(
            TimelineEditCommand.ReorderTracks(fromIndex = fromIndex, toIndex = toIndex)
        )
        _tracks.value = TimelineRepository.tracks.value
    }

    fun renameTimelineEntry(trackIndex: Int, entryStartMs: Long, newName: String) {
        val normalizedName = newName.trim()
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) ?: return

        when (track) {
            is AudioTimelineTrack -> {
                val entry = track.entries[entryStartMs] ?: return
                if (entry.name == normalizedName) return

                val before = snapshotAudioEntries(track)
                track.entries[entryStartMs] = entry.copy(name = normalizedName)
                val after = snapshotAudioEntries(track)
                val newTrack = track.copyWithEntries()

                currentTracks[trackIndex] = newTrack
                _tracks.value = currentTracks.toList()
                TimelineRepository.tracks.value = currentTracks.toList()
                SelectionManager.select(
                    Selectable.TimelineEntryItem(
                        trackIndex = trackIndex,
                        entryStartMs = entryStartMs
                    )
                )
                UndoManager.addAction(
                    UndoableAction.TimelineChange(
                        trackIndex = trackIndex,
                        beforeEntries = before,
                        afterEntries = after
                    )
                )
            }

            is MidiTimelineTrack -> {
                val entry = track.entries[entryStartMs] ?: return
                if (entry.name == normalizedName) return

                val before = snapshotMidiEntries(track)
                track.entries[entryStartMs] = entry.copy(name = normalizedName)
                val after = snapshotMidiEntries(track)
                val newTrack = track.copyWithEntries()

                currentTracks[trackIndex] = newTrack
                _tracks.value = currentTracks.toList()
                TimelineRepository.tracks.value = currentTracks.toList()
                SelectionManager.select(
                    Selectable.TimelineEntryItem(
                        trackIndex = trackIndex,
                        entryStartMs = entryStartMs
                    )
                )
                UndoManager.addAction(
                    UndoableAction.MidiTimelineChange(
                        trackIndex = trackIndex,
                        beforeEntries = before,
                        afterEntries = after
                    )
                )
            }
        }
    }

    fun setTrackBaseAutomation(
        trackIndex: Int,
        target: TimelineTrackAutomationTarget,
        value: Float
    ) {
        val normalizedValue = target.normalizeValue(value)
        updateTrackState(trackIndex) {
            if (baseAutomationValue(target) == normalizedValue) {
                false
            } else {
                setBaseAutomationValue(target, normalizedValue)
                true
            }
        }
    }

    fun nudgeTrackBaseAutomation(
        trackIndex: Int,
        target: TimelineTrackAutomationTarget,
        delta: Float
    ) {
        val track = _tracks.value.getOrNull(trackIndex) ?: return
        setTrackBaseAutomation(
            trackIndex = trackIndex,
            target = target,
            value = track.baseAutomationValue(target) + delta
        )
    }

    fun toggleTrackMute(trackIndex: Int) {
        updateTrackState(trackIndex) {
            isMuted = !isMuted
            true
        }
    }

    fun toggleTrackSolo(trackIndex: Int) {
        updateTrackState(trackIndex) {
            isSoloed = !isSoloed
            true
        }
    }

    fun moveAudioEntry(trackIndex: Int, oldStartMs: Long, newStartMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? AudioTimelineTrack ?: return
        val before = snapshotAudioEntries(track)
        val entry = track.entries.remove(oldStartMs) ?: return
        val bpm = WorkspaceRepository.bpm.value
        val gridType = WorkspaceRepository.gridType.value
        val snappedStart = if (gridType is GridUtils.GridType.NoGrid) {
            newStartMs.coerceAtLeast(0L)
        } else {
            val intervals = GridUtils.computeWithGridType(_viewport.value.zoomX, bpm, gridType)
            val gridInterval = intervals.intervalMs
            val isAlreadySnapped = gridInterval > 0 && newStartMs % gridInterval == 0L
            if (isAlreadySnapped) newStartMs else snapToGrid(newStartMs, gridInterval)
        }
        val movedEntry = entry.copyWithShiftedStartMs(snappedStart)
        val resolved = resolveOverlapAsymmetric(track, movedEntry, originStartMs = oldStartMs) ?: run {
            track.entries[oldStartMs] = entry
            return
        }
        track.entries[resolved.startTimeMs] = resolved
        val after = snapshotAudioEntries(track)
        val newTrack = track.copyWithEntries()
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = resolved.startTimeMs))
        UndoManager.addAction(UndoableAction.TimelineChange(trackIndex = trackIndex, beforeEntries = before, afterEntries = after))
    }

    fun deleteAudioEntry(trackIndex: Int, entryStartMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? AudioTimelineTrack ?: return
        val original = track.entries.remove(entryStartMs) ?: return
        val newTrack = track.copyWithEntries()
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
            val trackIndex = currentTracks.size
            currentTracks.add(newTrack)
            _tracks.value = currentTracks.toList()
            TimelineRepository.addTrack(newTrack)
            
            UndoManager.addAction(
                UndoableAction.TrackAddition(
                    trackIndex = trackIndex,
                    track = newTrack
                )
            )
        }
    }

    /**
     * Add a MIDI track to the timeline
     */
    fun addMidiTrack() {
        viewModelScope.launch {
            val newTrack = MidiTimelineTrack()
            val currentTracks = _tracks.value.toMutableList()
            val trackIndex = currentTracks.size
            currentTracks.add(newTrack)
            _tracks.value = currentTracks.toList()
            TimelineRepository.addTrack(newTrack)
            
            UndoManager.addAction(
                UndoableAction.TrackAddition(
                    trackIndex = trackIndex,
                    track = newTrack
                )
            )
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
            val newTrack = track.copyWithEntries()
            currentTracks[trackIndex] = newTrack
            publishTrackSnapshot(currentTracks.toList())
            
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
            val newTrack = track.copyWithEntries()
            currentTracks[trackIndex] = newTrack
            publishTrackSnapshot(currentTracks.toList())
        }
    }

    /**
     * Update a MIDI note in an entry
     */
    fun updateMidiNote(trackIndex: Int, entryStartMs: Long, oldNote: MidiNote, newNote: MidiNote) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        
        track.updateNote(entryStartMs, oldNote, newNote)
        
        val newTrack = track.copyWithEntries()
        currentTracks[trackIndex] = newTrack
        publishTrackSnapshot(currentTracks.toList())
    }

    /**
     * Delete a MIDI note from an entry
     */
    fun deleteMidiNote(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        
        track.removeNote(entryStartMs, note)
        
        val newTrack = track.copyWithEntries()
        currentTracks[trackIndex] = newTrack
        publishTrackSnapshot(currentTracks.toList())
    }

    /**
     * Delete a MIDI entry
     */
    fun deleteMidiEntry(trackIndex: Int, entryStartMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        val original = track.entries.remove(entryStartMs) ?: return
        val newTrack = track.copyWithEntries()
        val current = _tracks.value.toMutableList(); current[trackIndex] = newTrack
        publishTrackSnapshot(current.toList())
        // Note: UndoableAction would need to be extended to support MIDI entries
    }

    /**
     * Handle an arrangement open request on a MIDI track by opening an existing entry.
     */
    fun onDoubleClickMidiTrack(trackIndex: Int, timeMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        
        // Only open the Piano Roll if there's an existing entry at this time
        val existingEntry = track.entries.values.firstOrNull { entry ->
            timeMs >= entry.startTimeMs && timeMs < entry.endTimeMs
        }
        
        if (existingEntry != null) {
            val clipContext = TimelineClipContext.midi(trackIndex, existingEntry)
            enterPianoRollForEntry(clipContext, existingEntry)
        }
    }

    /**
     * Create a new MIDI entry for a specific time range.
     */
    fun createMidiEntry(trackIndex: Int, startMs: Long, endMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        if (track.hasOneDimensionalBlockerAt(startMs)) return
        val before = track.entries.values.toList()
        val nextStart = track.allOneDimensionalEntries().map { it.startTimeMs }.filter { it > startMs }.minOrNull()
        val duration = minOf(
            (endMs - startMs).coerceAtLeast(100L),
            nextStart?.minus(startMs) ?: Long.MAX_VALUE,
        ).coerceAtLeast(1L)
        val newEntry = MidiEntry(
            startTimeMs = startMs,
            durationMs = duration,
            notes = emptyList(),
            name = "Lights Clip"
        )
        
        track.addEntry(newEntry)
        val after = track.entries.values.toList()
        
        val currentTracks = _tracks.value.toMutableList()
        val newTrack = track.copyWithEntries()
        currentTracks[trackIndex] = newTrack
        publishTrackSnapshot(currentTracks.toList())
        
        UndoManager.addAction(UndoableAction.MidiTimelineChange(trackIndex = trackIndex, beforeEntries = before, afterEntries = after))
        
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = startMs))
    }

    // ========== Timeline-owned Chain Effects ==========

    fun createChainEffectEntry(trackIndex: Int, startMs: Long): ChainEffectEntry? {
        if (!dev.anthonyhfm.amethyst.settings.data.ExperimentalSettings.timelineChainEffects.value) return null
        val originalTrack = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return null
        val snappedStart = snapTimelineTime(startMs)
        if (originalTrack.hasOneDimensionalBlockerAt(snappedStart)) return null

        val nextStart = originalTrack.allOneDimensionalEntries()
            .asSequence()
            .map { it.startTimeMs }
            .filter { it > snappedStart }
            .minOrNull()
        val placeholderDuration = minOf(
            oneBeatMs() * 4L,
            nextStart?.minus(snappedStart)?.coerceAtLeast(1L) ?: Long.MAX_VALUE,
        )
        val entry = ChainEffectEntry(
            startTimeMs = snappedStart,
            durationMs = placeholderDuration,
        )

        val before = originalTrack.deepCopy()
        val after = originalTrack.deepCopy() as MidiTimelineTrack
        after.chainEffectEntries[snappedStart] = entry
        commitChainEffectTrackChange(trackIndex, before, after)
        selectChainEffect(trackIndex, entry)
        _openChainEffectClipId.value = entry.clipId
        return entry
    }

    fun openChainEffect(trackIndex: Int, clipId: String) {
        val entry = findChainEffect(trackIndex, clipId) ?: return
        selectChainEffect(trackIndex, entry)
        _openChainEffectClipId.value = clipId
    }

    fun selectChainEffectClip(trackIndex: Int, clipId: String) {
        findChainEffect(trackIndex, clipId)?.let { selectChainEffect(trackIndex, it) }
    }

    fun closeChainEffect() {
        _openChainEffectClipId.value = null
    }

    fun deleteChainEffect(trackIndex: Int, clipId: String) {
        val track = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        val entry = track.chainEffectEntries.values.firstOrNull { it.clipId == clipId } ?: return
        val before = track.deepCopy()
        val after = track.deepCopy() as MidiTimelineTrack
        after.chainEffectEntries.remove(entry.startTimeMs)
        commitChainEffectTrackChange(trackIndex, before, after)
        if (_openChainEffectClipId.value == clipId) closeChainEffect()
    }

    fun duplicateChainEffect(trackIndex: Int, clipId: String): ChainEffectEntry? {
        val track = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return null
        val entry = track.chainEffectEntries.values.firstOrNull { it.clipId == clipId } ?: return null
        var candidate = entry.endTimeMs
        val blockers = track.allOneDimensionalEntries().sortedBy { it.startTimeMs }
        while (true) {
            val blocker = blockers.firstOrNull { candidate >= it.startTimeMs && candidate < it.endTimeMs } ?: break
            candidate = blocker.endTimeMs
        }
        val nextStart = blockers.map { it.startTimeMs }.filter { it > candidate }.minOrNull()
        val copiedDuration = minOf(entry.durationMs, nextStart?.minus(candidate) ?: Long.MAX_VALUE).coerceAtLeast(1L)
        val duplicate = entry.deepCopy(
            clipId = dev.anthonyhfm.amethyst.core.util.UUID.randomUUID(),
            startTimeMs = candidate,
            durationMs = copiedDuration,
            maxDurationMs = if (copiedDuration < entry.durationMs) copiedDuration else entry.maxDurationMs,
        )
        val before = track.deepCopy()
        val after = track.deepCopy() as MidiTimelineTrack
        after.chainEffectEntries[candidate] = duplicate
        commitChainEffectTrackChange(trackIndex, before, after)
        selectChainEffect(trackIndex, duplicate)
        return duplicate
    }

    fun setChainEffectSource(trackIndex: Int, clipId: String, device: GenericChainDevice<*>?) {
        require(device == null || device is TimelineTriggerable)
        mutateChainEffectRuntime(trackIndex, clipId) { replaceSource(device) }
    }

    fun addChainEffectProcessor(
        trackIndex: Int,
        clipId: String,
        device: GenericChainDevice<*>,
        atIndex: Int? = null,
    ) {
        mutateChainEffectRuntime(trackIndex, clipId) { addProcessor(device, atIndex) }
    }

    fun removeChainEffectProcessor(trackIndex: Int, clipId: String, index: Int) {
        mutateChainEffectRuntime(trackIndex, clipId) { removeProcessor(index) }
    }

    fun reorderChainEffectProcessor(trackIndex: Int, clipId: String, fromIndex: Int, toIndex: Int) {
        mutateChainEffectRuntime(trackIndex, clipId) { moveProcessor(fromIndex, toIndex) }
    }

    fun moveChainEffect(trackIndex: Int, clipId: String, requestedStartMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        val entry = track.chainEffectEntries.values.firstOrNull { it.clipId == clipId } ?: return
        val newStart = snapTimelineTime(requestedStartMs)
        val blockers = track.allOneDimensionalEntries().filterNot { it is ChainEffectEntry && it.clipId == clipId }
        if (blockers.any { newStart >= it.startTimeMs && newStart < it.endTimeMs }) return

        val nextStart = blockers.map { it.startTimeMs }.filter { it > newStart }.minOrNull()
        val available = nextStart?.minus(newStart)
        val duration = minOf(entry.durationMs, available ?: Long.MAX_VALUE).coerceAtLeast(1L)
        val moved = entry.copy(
            startTimeMs = newStart,
            durationMs = duration,
            maxDurationMs = if (duration < entry.durationMs) duration else entry.maxDurationMs,
        )
        val before = track.deepCopy()
        val after = track.deepCopy() as MidiTimelineTrack
        after.chainEffectEntries.remove(entry.startTimeMs)
        after.chainEffectEntries[newStart] = moved
        commitChainEffectTrackChange(trackIndex, before, after)
        selectChainEffect(trackIndex, moved)
    }

    fun resizeChainEffect(
        trackIndex: Int,
        clipId: String,
        requestedStartMs: Long,
        requestedDurationMs: Long,
    ) {
        val track = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        val entry = track.chainEffectEntries.values.firstOrNull { it.clipId == clipId } ?: return
        val runtime = TimelineRepository.chainEffectRuntime(clipId) ?: return
        val newStart = requestedStartMs.coerceAtLeast(0L)
        val blockers = track.allOneDimensionalEntries().filterNot { it is ChainEffectEntry && it.clipId == clipId }
        if (blockers.any { newStart >= it.startTimeMs && newStart < it.endTimeMs }) return
        val nextStart = blockers.map { it.startTimeMs }.filter { it > newStart }.minOrNull()
        val available = nextStart?.minus(newStart) ?: Long.MAX_VALUE
        val natural = runtime.naturalDuration()
        val minDurationMs = oneBeatMs() * 4L
        val naturalLimit = when (natural) {
            is TimelineDuration.Finite -> natural.milliseconds.coerceAtLeast(minDurationMs)
            TimelineDuration.None -> oneBeatMs() * 4L
            TimelineDuration.Unbounded -> Long.MAX_VALUE
        }
        val duration = requestedDurationMs.coerceAtLeast(minDurationMs).coerceAtMost(minOf(available, naturalLimit))
        val cap = when {
            natural is TimelineDuration.Unbounded -> duration
            natural is TimelineDuration.Finite && duration >= naturalLimit && available >= naturalLimit -> null
            else -> duration
        }
        val resized = runtime.snapshot(
            startTimeMs = newStart,
            durationMs = duration,
            maxDurationMs = cap,
        )
        val before = track.deepCopy()
        val after = track.deepCopy() as MidiTimelineTrack
        after.chainEffectEntries.remove(entry.startTimeMs)
        after.chainEffectEntries[newStart] = resized
        commitChainEffectTrackChange(trackIndex, before, after)
        selectChainEffect(trackIndex, resized)
    }

    private fun mutateChainEffectRuntime(
        trackIndex: Int,
        clipId: String,
        mutate: ChainEffectRuntime.() -> Unit,
    ) {
        val before = (_tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack)?.deepCopy() ?: return
        val runtime = TimelineRepository.chainEffectRuntime(clipId) ?: return
        runtime.mutate()
        _tracks.value = TimelineRepository.tracks.value
        val after = (_tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack)?.deepCopy() ?: return
        if (before != after) {
            UndoManager.addAction(UndoableAction.TrackStateChange(trackIndex, before, after, mergeable = false))
        }
    }

    private fun commitChainEffectTrackChange(
        trackIndex: Int,
        before: TimelineTrack<*>,
        after: MidiTimelineTrack,
    ) {
        TimelineRepository.replaceTrack(trackIndex, after)
        _tracks.value = TimelineRepository.tracks.value
        UndoManager.addAction(UndoableAction.TrackStateChange(trackIndex, before, after.deepCopy(), mergeable = false))
    }

    private fun findChainEffect(trackIndex: Int, clipId: String): ChainEffectEntry? =
        (_tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack)
            ?.chainEffectEntries?.values?.firstOrNull { it.clipId == clipId }

    private fun selectChainEffect(trackIndex: Int, entry: ChainEffectEntry) {
        SelectionManager.select(
            Selectable.TimelineEntryItem(
                trackIndex = trackIndex,
                entryStartMs = entry.startTimeMs,
                clipId = entry.clipId,
            )
        )
    }

    private fun MidiTimelineTrack.hasOneDimensionalBlockerAt(timeMs: Long): Boolean =
        allOneDimensionalEntries().any { timeMs >= it.startTimeMs && timeMs < it.endTimeMs }

    private fun snapTimelineTime(timeMs: Long): Long {
        val gridType = WorkspaceRepository.gridType.value
        if (gridType is GridUtils.GridType.NoGrid) return timeMs.coerceAtLeast(0L)
        val interval = GridUtils.computeWithGridType(
            _viewport.value.zoomX,
            WorkspaceRepository.bpm.value,
            gridType,
        ).intervalMs
        return snapToGrid(timeMs, interval)
    }

    private fun nudgeSelectedTimelineTime(direction: Int): Boolean {
        val selection = SelectionManager.selections.value
            .filterIsInstance<Selectable.TimelineTime>()
            .firstOrNull()
            ?: return false
        val intervalMs = GridUtils.computeWithGridType(
            _viewport.value.zoomX,
            WorkspaceRepository.bpm.value,
            WorkspaceRepository.gridType.value,
        ).intervalMs
        val targetTimeMs = adjacentTimelineGridTimeMs(selection.timeMs, intervalMs, direction)
        SelectionManager.select(selection.copy(timeMs = targetTimeMs))
        updateViewport { viewport ->
            val viewportWidth = viewport.viewportWidth
            if (viewportWidth <= 0f) return@updateViewport viewport

            val marginPx = minOf(24f, viewportWidth / 4f)
            val targetContentX = viewport.timeMsToContentX(targetTimeMs.toDouble())
            val targetScreenX = viewport.contentToScreenX(targetContentX)
            when {
                targetScreenX < marginPx -> viewport.copy(scrollX = targetContentX - marginPx).clamp()
                targetScreenX > viewportWidth - marginPx ->
                    viewport.copy(scrollX = targetContentX - viewportWidth + marginPx).clamp()
                else -> viewport
            }
        }
        return true
    }

    private fun oneBeatMs(): Long =
        (60_000.0 / WorkspaceRepository.bpm.value.coerceAtLeast(1.0)).toLong().coerceAtLeast(1L)

    override fun onCleared() {
        TimelineKeyHandler.closeChainEffectPanel = null
        TimelineKeyHandler.deleteChainEffectClip = null
        TimelineKeyHandler.duplicateChainEffectClip = null
        TimelineKeyHandler.nudgeTimelineTime = null
        super.onCleared()
    }

    private fun enterPianoRollForEntry(clipContext: TimelineClipContext, entry: MidiEntry) {
        SelectionManager.select(clipContext.toTimelineEntrySelection())
        openPianoRollForEntry(clipContext, entry)
    }

    /**
     * Open Piano Roll workspace mode for editing a MIDI entry
     */
    private fun openPianoRollForEntry(clipContext: TimelineClipContext, entry: MidiEntry) {
        val pianoRollMode = PianoRollWorkspaceMode()

        pianoRollMode.bindClipContext(clipContext, entry)
        pianoRollMode.timingContextProvider = ::currentTimingContext
        pianoRollMode.onNoteAdd = { note ->
            withPianoRollClipContext(pianoRollMode) { currentClipContext ->
                addMidiNoteLive(currentClipContext.trackIndex, currentClipContext.entryStartMs, note)
            }
        }
        pianoRollMode.onNoteUpdate = { old, new ->
            withPianoRollClipContext(pianoRollMode) { currentClipContext ->
                updateMidiNoteLive(currentClipContext.trackIndex, currentClipContext.entryStartMs, old, new)
            }
        }
        pianoRollMode.onNoteDelete = { note ->
            withPianoRollClipContext(pianoRollMode) { currentClipContext ->
                deleteMidiNoteLive(currentClipContext.trackIndex, currentClipContext.entryStartMs, note)
            }
        }
        pianoRollMode.modeClose = { WorkspaceRepository.switchToPreviousMode() }
        WorkspaceRepository.switchMode(pianoRollMode)
        println("Opened Piano Roll for entry at ${clipContext.entryStartMs}ms on track ${clipContext.trackIndex}")
    }

    private inline fun withPianoRollClipContext(
        pianoRollMode: PianoRollWorkspaceMode,
        block: (TimelineClipContext) -> Unit
    ) {
        pianoRollMode.clipContext?.let(block)
    }

    /**
     * Move a MIDI entry to a new position
     */
    fun moveMidiEntry(trackIndex: Int, oldStartMs: Long, newStartMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        
        if (track !is MidiTimelineTrack) return
        
        val entry = track.entries[oldStartMs] ?: return
        
        val bpm = WorkspaceRepository.bpm.value
        val gridType = WorkspaceRepository.gridType.value
        val snappedStart = if (gridType is GridUtils.GridType.NoGrid) {
            newStartMs.coerceAtLeast(0L)
        } else {
            val intervals = GridUtils.computeWithGridType(_viewport.value.zoomX, bpm, gridType)
            snapToGrid(newStartMs, intervals.intervalMs)
        }
        
        val blockers = track.allOneDimensionalEntries().filterNot { it is MidiEntry && it.startTimeMs == oldStartMs }
        if (blockers.any { snappedStart >= it.startTimeMs && snappedStart < it.endTimeMs }) return
        val nextStart = blockers.map { it.startTimeMs }.filter { it > snappedStart }.minOrNull()
        val movedEntry = entry.copy(
            startTimeMs = snappedStart,
            durationMs = minOf(entry.durationMs, nextStart?.minus(snappedStart) ?: Long.MAX_VALUE).coerceAtLeast(1L),
        )
        track.entries.remove(oldStartMs)
        track.entries[snappedStart] = movedEntry
        
        val newTrack = track.copyWithEntries()
        currentTracks[trackIndex] = newTrack
        publishTrackSnapshot(currentTracks.toList())
        
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = snappedStart))
    }

    /**
     * Duplicate a MIDI entry
     */
    fun duplicateMidiEntry(trackIndex: Int, entryStartMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        
        if (track !is MidiTimelineTrack) return
        
        val entry = track.entries[entryStartMs] ?: return
        
        var newStartMs = entry.endTimeMs
        val blockers = track.allOneDimensionalEntries()
        while (true) {
            val blocker = blockers.firstOrNull { newStartMs >= it.startTimeMs && newStartMs < it.endTimeMs } ?: break
            newStartMs = blocker.endTimeMs
        }
        val nextStart = blockers.map { it.startTimeMs }.filter { it > newStartMs }.minOrNull()
        val duplicatedEntry = entry.copy(
            startTimeMs = newStartMs,
            durationMs = minOf(entry.durationMs, nextStart?.minus(newStartMs) ?: Long.MAX_VALUE).coerceAtLeast(1L),
        )
        track.entries[newStartMs] = duplicatedEntry
        
        // Update track
        val newTrack = track.copyWithEntries()
        currentTracks[trackIndex] = newTrack
        publishTrackSnapshot(currentTracks.toList())
        
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = newStartMs))
    }

    private fun updateMidiEntry(trackIndex: Int, entryStartMs: Long, transform: (MidiEntry) -> MidiEntry) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        val isMidi = track is MidiTimelineTrack
        if (!isMidi) return
        val entry = track.entries[entryStartMs] ?: return
        val updated = transform(entry)
        track.entries[entryStartMs] = updated

        val maxEnd = updated.notes.maxOfOrNull { it.endTimeMs } ?: updated.durationMs
        val nextStart = track.allOneDimensionalEntries()
            .filterNot { it is MidiEntry && it.startTimeMs == entryStartMs }
            .map { it.startTimeMs }
            .filter { it > entryStartMs }
            .minOrNull()
        val newDuration = minOf(
            maxEnd.coerceAtLeast(updated.durationMs),
            nextStart?.minus(entryStartMs) ?: Long.MAX_VALUE,
        ).coerceAtLeast(1L)
        if (newDuration != updated.durationMs) {
            track.entries[entryStartMs] = updated.copy(durationMs = newDuration)
        }
        val newTrackInstance = track.copyWithEntries()
        currentTracks[trackIndex] = newTrackInstance
        publishTrackSnapshot(currentTracks.toList())
        // Update Piano Roll mode entry if open
        val mode = WorkspaceRepository.mode.value
        if (mode is PianoRollWorkspaceMode && mode.isEditingClip(trackIndex, entryStartMs)) {
            mode.syncCurrentEntry(newTrackInstance.entries[entryStartMs])
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

    fun resizeMidiEntry(trackIndex: Int, oldStartMs: Long, newStartMs: Long, newDurationMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        if (track !is MidiTimelineTrack) return
        val before = snapshotMidiEntries(track)
        val entry = track.entries[oldStartMs] ?: return

        val blockers = track.allOneDimensionalEntries().filterNot { it is MidiEntry && it.startTimeMs == oldStartMs }
        if (blockers.any { newStartMs >= it.startTimeMs && newStartMs < it.endTimeMs }) return
        val nextStart = blockers.map { it.startTimeMs }.filter { it > newStartMs }.minOrNull()

        val clampedDuration = minOf(
            newDurationMs.coerceAtLeast(50L),
            nextStart?.minus(newStartMs) ?: Long.MAX_VALUE,
        ).coerceAtLeast(1L)
        val startChanged = newStartMs != oldStartMs

        val movedNotes = if (startChanged) {
            val delta = newStartMs - oldStartMs
            entry.notes.map { n -> n.copy(startTimeMs = (n.startTimeMs + delta).coerceAtLeast(0L)) }
        } else entry.notes

        if (startChanged) track.entries.remove(oldStartMs)
        val updated = entry.copy(startTimeMs = newStartMs, durationMs = clampedDuration, notes = movedNotes)
        track.entries[newStartMs] = updated

        val maxEnd = updated.notes.maxOfOrNull { it.endTimeMs } ?: updated.endTimeMs
        val finalDuration = minOf(
            (maxEnd - updated.startTimeMs).coerceAtLeast(updated.durationMs),
            nextStart?.minus(newStartMs) ?: Long.MAX_VALUE,
        ).coerceAtLeast(1L)
        if (finalDuration != updated.durationMs) {
            track.entries[newStartMs] = updated.copy(durationMs = finalDuration)
        }

        val newTrackInstance = track.copyWithEntries()
        val after = snapshotMidiEntries(newTrackInstance)

        currentTracks[trackIndex] = newTrackInstance
        publishTrackSnapshot(currentTracks.toList())
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = newStartMs))

        val mode = WorkspaceRepository.mode.value
        if (mode is PianoRollWorkspaceMode && mode.isEditingClip(trackIndex, oldStartMs)) {
            mode.syncClipEntryStart(newStartMs)
            mode.syncCurrentEntry(newTrackInstance.entries[newStartMs])
        }

        if (before != after) {
            UndoManager.addAction(
                UndoableAction.MidiTimelineChange(
                    trackIndex = trackIndex,
                    beforeEntries = before,
                    afterEntries = after
                )
            )
        }
    }
}
