package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.engine.echo.AudioOutput
import dev.anthonyhfm.amethyst.core.util.mainDispatcherOrDefault
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.timeline.automation.TimelineAutomationEvaluator
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.endTimeUs
import dev.anthonyhfm.amethyst.timeline.data.msToUs
import dev.anthonyhfm.amethyst.timeline.data.usToRoundedMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.concurrent.Volatile


object TimelineRepository {
    private data class TrackAudioEntry(
        val trackIndex: Int,
        val track: AudioTimelineTrack,
        val entry: AudioEntry
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TrackAudioEntry) return false
            return trackIndex == other.trackIndex && entry == other.entry
        }

        override fun hashCode(): Int {
            var result = trackIndex
            result = 31 * result + entry.hashCode()
            return result
        }
    }

    private data class TrackMidiEntry(
        val trackIndex: Int,
        val track: MidiTimelineTrack,
        val entry: MidiEntry
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TrackMidiEntry) return false
            return trackIndex == other.trackIndex && entry == other.entry
        }

        override fun hashCode(): Int {
            var result = trackIndex
            result = 31 * result + entry.hashCode()
            return result
        }
    }

    val tracks: MutableStateFlow<List<TimelineTrack<*>>> = MutableStateFlow(emptyList())

    /**
     * Set to true before applying a remote track update so the
     * [dev.anthonyhfm.amethyst.core.network.sync.TimelineSyncBroadcaster]
     * can skip re-broadcasting the incoming change. Call [markRemoteUpdateConsumed] to reset.
     */
    @Volatile var isApplyingRemoteUpdate: Boolean = false

    fun markRemoteUpdateConsumed() {
        isApplyingRemoteUpdate = false
    }

    private val _playheadPositionMs = MutableStateFlow(0L)
    val playheadPositionMs: StateFlow<Long> = _playheadPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playbackJob: Job? = null
    private val playbackScope = CoroutineScope(
        mainDispatcherOrDefault(owner = "TimelineRepository")
    )

    private val activeEntries = mutableSetOf<TrackAudioEntry>()
    private val activeMidiEntries = mutableSetOf<TrackMidiEntry>()

    // Basis für Zeitberechnung (wird nur bei Play & Seek aktualisiert)
    private var baselineMark: TimeMark? = null
    private var baselinePlayheadMs: Long = 0L

    private var sortedAudioEntries: List<TrackAudioEntry> = emptyList()
    private var sortedMidiEntries: List<TrackMidiEntry> = emptyList()
    private var nextStartIndex: Int = 0
    private var nextMidiStartIndex: Int = 0
    private var lastPlayheadMs: Long = 0L

    // Lookahead window: pre-start clips this many ms before their boundary so the audio
    // queue is already drained by AudioOutput before the actual start time. This eliminates
    // With synchronous AudioOutput.playMultiple() there is no queue delay, so no lookahead
    // is needed. The tick fires exactly at each clip boundary; startAudioEntry is called
    // at that point and starts the clip with zero offset (or a sub-1ms offset if the tick
    // has minor jitter — inaudible). Removing lookahead eliminates the 12ms pre-stop
    // silence that the old approach produced.

    private fun hasSoloedTracks(): Boolean = tracks.value.any { it.isSoloed }

    private fun isPlaybackEnabled(track: TimelineTrack<*>, anySoloedTrack: Boolean): Boolean {
        return TimelineAutomationEvaluator.isPlaybackEnabled(
            track = track,
            anySoloedTrack = anySoloedTrack,
        )
    }

    private fun TrackAudioEntry.shouldPlayAt(positionMs: Long, anySoloedTrack: Boolean): Boolean {
        val positionUs = msToUs(positionMs)
        return positionUs >= entry.startTimeUs &&
            positionUs < entry.endTimeUs &&
            isPlaybackEnabled(track, anySoloedTrack)
    }

    private fun TrackMidiEntry.shouldPlayAt(positionMs: Long, anySoloedTrack: Boolean): Boolean {
        return positionMs >= entry.startTimeMs &&
            positionMs < entry.endTimeMs &&
            isPlaybackEnabled(track, anySoloedTrack)
    }

    private fun TrackMidiEntry.overlapsPlaybackWindow(
        startExclusiveMs: Long,
        endInclusiveMs: Long,
        anySoloedTrack: Boolean
    ): Boolean {
        return entry.startTimeMs <= endInclusiveMs &&
            entry.endTimeMs > startExclusiveMs &&
            isPlaybackEnabled(track, anySoloedTrack)
    }

    private fun startAudioEntry(entry: TrackAudioEntry, startAt: Long) {
        val automation = TimelineAutomationEvaluator.evaluate(entry.track, startAt)
        val signal = entry.entry.buildPlaybackSignal(startAt, automation)
        if (signal == null) {
            println("PLAYBACK: startAudioEntry — buildPlaybackSignal returned null for ${entry.entry.fileName} (startAt=$startAt startTimeMs=${entry.entry.startTimeMs} clipStart=${entry.entry.clipStartSample} clipEnd=${entry.entry.clipEndSample} source=${entry.entry.source()?.let { "ok size=${it.rawData.size}" } ?: "NULL"})")
            return
        }
        println("PLAYBACK: startAudioEntry — ${entry.entry.fileName} startAt=$startAt entryStart=${entry.entry.startTimeMs} clipStart=${entry.entry.clipStartSample} clipEnd=${entry.entry.clipEndSample} signalBytes=${signal.rawData?.size} durationMs=${signal.durationMs}")
        val ids = AudioOutput.playMultiple(listOf(signal))
        entry.entry.receiveSourceId(ids.firstOrNull())
    }

    private fun startAudioEntriesBatch(entries: List<TrackAudioEntry>, startAt: Long) {
        if (entries.isEmpty()) return
        if (entries.size == 1) { startAudioEntry(entries[0], startAt); return }

        // Build playback signals for all entries, then start them atomically.
        val automations = entries.map { TimelineAutomationEvaluator.evaluate(it.track, startAt) }
        val signals = entries.mapIndexed { i, e -> e.entry.buildPlaybackSignal(startAt, automations[i]) }
        val sourceIds = AudioOutput.playMultiple(signals.filterNotNull())

        var sourceIdx = 0
        entries.forEachIndexed { i, e ->
            if (signals[i] != null) {
                e.entry.receiveSourceId(sourceIds.getOrNull(sourceIdx))
                sourceIdx++
            }
        }
    }

    private fun startMidiEntry(entry: TrackMidiEntry, startAt: Long, processTo: Long? = null) {
        entry.entry.start(
            startAt = startAt,
            automation = TimelineAutomationEvaluator.evaluate(
                track = entry.track,
                timeMs = startAt,
            )
        )
        processTo
            ?.takeIf { it > startAt }
            ?.let(entry.entry::processAtTime)
    }

    private fun syncEntriesAtPosition(positionMs: Long) {
        activeEntries.clear()
        activeMidiEntries.clear()

        val anySoloedTrack = hasSoloedTracks()
        sortedAudioEntries.forEach { entry ->
            if (entry.shouldPlayAt(positionMs, anySoloedTrack)) {
                activeEntries.add(entry)
            }
        }
        sortedMidiEntries.forEach { entry ->
            if (entry.shouldPlayAt(positionMs, anySoloedTrack)) {
                activeMidiEntries.add(entry)
            }
        }
    }

    private fun normalizeTrackIdentityAndRouting(updatedTracks: List<TimelineTrack<*>>) {
        updatedTracks.forEach { track ->
            if (track.trackId.isBlank()) {
                track.trackId = UUID.randomUUID()
            }
        }
    }

    fun updateTracksSnapshot(updatedTracks: List<TimelineTrack<*>>) {
        normalizeTrackIdentityAndRouting(updatedTracks)
        updatedTracks.forEach { track ->
            track.normalizeAutomationState()
        }
        tracks.value = updatedTracks
        rebuildSortedEntries()
        if (_isPlaying.value) {
            syncActiveEntriesWithCurrentTracks()
            refreshPlaybackAt()
        } else {
            syncEntriesAtPosition(_playheadPositionMs.value)
        }
    }

    private fun refreshPlaybackAt(positionMs: Long = _playheadPositionMs.value) {
        if (!_isPlaying.value) return

        val anySoloedTrack = hasSoloedTracks()

        val audioIterator = activeEntries.iterator()
        while (audioIterator.hasNext()) {
            val entry = audioIterator.next()
            if (!entry.shouldPlayAt(positionMs, anySoloedTrack)) {
                entry.entry.stop()
                audioIterator.remove()
            }
        }

        val midiIterator = activeMidiEntries.iterator()
        while (midiIterator.hasNext()) {
            val entry = midiIterator.next()
            if (!entry.shouldPlayAt(positionMs, anySoloedTrack)) {
                entry.entry.stop()
                midiIterator.remove()
            }
        }

        val newAudioEntries = sortedAudioEntries.filter { it.shouldPlayAt(positionMs, anySoloedTrack) && !activeEntries.contains(it) }
        startAudioEntriesBatch(newAudioEntries, positionMs)
        activeEntries.addAll(newAudioEntries)

        sortedMidiEntries.forEach { entry ->
            if (entry.shouldPlayAt(positionMs, anySoloedTrack) && !activeMidiEntries.contains(entry)) {
                startMidiEntry(entry, positionMs)
                activeMidiEntries.add(entry)
            }
        }
    }

    private fun syncActiveEntriesWithCurrentTracks() {
        val activeAudioKeys = activeEntries
            .map { it.trackIndex to it.entry }
            .toSet()
        val activeMidiKeys = activeMidiEntries
            .map { it.trackIndex to it.entry }
            .toSet()

        activeEntries.clear()
        activeEntries.addAll(
            sortedAudioEntries.filter { activeAudioKeys.contains(it.trackIndex to it.entry) }
        )

        activeMidiEntries.clear()
        activeMidiEntries.addAll(
            sortedMidiEntries.filter { activeMidiKeys.contains(it.trackIndex to it.entry) }
        )
    }

    private fun preserveRuntimeTrackEntries(
        currentTrack: TimelineTrack<*>,
        replacementTrack: TimelineTrack<*>
    ): TimelineTrack<*> =
        when {
            currentTrack is AudioTimelineTrack && replacementTrack is AudioTimelineTrack -> {
                replacementTrack.copyWithEntries(
                    replacementTrack.entries.mapValues { (startTimeMs, replacementEntry) ->
                        currentTrack.entries[startTimeMs]
                            ?.takeIf { existingEntry -> existingEntry == replacementEntry }
                            ?: replacementEntry
                    }
                )
            }

            currentTrack is MidiTimelineTrack && replacementTrack is MidiTimelineTrack -> {
                replacementTrack.copyWithEntries(
                    replacementTrack.entries.mapValues { (startTimeMs, replacementEntry) ->
                        currentTrack.entries[startTimeMs]
                            ?.takeIf { existingEntry -> existingEntry == replacementEntry }
                            ?: replacementEntry
                    }
                )
            }

            else -> replacementTrack
        }

    private fun rebuildSortedEntries() {
        sortedAudioEntries = tracks.value
            .flatMapIndexed { trackIndex, track ->
                when (track) {
                    is AudioTimelineTrack -> track.entries.values.map { entry ->
                        TrackAudioEntry(trackIndex = trackIndex, track = track, entry = entry)
                    }

                    else -> emptyList()
                }
            }
            .sortedBy { it.entry.startTimeUs }

        sortedMidiEntries = tracks.value
            .flatMapIndexed { trackIndex, track ->
                when (track) {
                    is MidiTimelineTrack -> track.entries.values.map { entry ->
                        TrackMidiEntry(trackIndex = trackIndex, track = track, entry = entry)
                    }

                    else -> emptyList()
                }
            }
            .sortedBy { it.entry.startTimeMs }

        nextStartIndex = binarySearchFirst(sortedAudioEntries) { it.entry.startTimeUs >= msToUs(_playheadPositionMs.value) }
        nextMidiStartIndex = binarySearchFirst(sortedMidiEntries) { it.entry.startTimeMs >= _playheadPositionMs.value }
    }

    private inline fun <T> binarySearchFirst(list: List<T>, predicate: (T) -> Boolean): Int {
        var low = 0; var high = list.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (predicate(list[mid])) high = mid else low = mid + 1
        }
        return low
    }

    fun addTrack(track: TimelineTrack<*>) {
        updateTracksSnapshot(tracks.value + track)
    }

    fun loadTracks(loadedTracks: List<TimelineTrack<*>>) {
        stop()
        SelectionManager.clear()
        updateTracksSnapshot(loadedTracks)
    }

    fun removeTrack(trackIndex: Int) {
        if (trackIndex !in tracks.value.indices) return
        val current = tracks.value.toMutableList()
        current.removeAt(trackIndex)
        updateTracksSnapshot(current.toList())

        val selectionIndex = when {
            current.isEmpty() -> null
            trackIndex <= current.lastIndex -> trackIndex
            trackIndex > 0 -> trackIndex - 1
            else -> null
        }

        if (selectionIndex == null) {
            SelectionManager.clear()
        } else {
            SelectionManager.selectTimelineTracks(
                trackIndices = listOf(selectionIndex),
                anchorTrackIndex = selectionIndex,
            )
        }
    }

    fun insertTrack(trackIndex: Int, track: TimelineTrack<*>) {
        SelectionManager.clear()
        val current = tracks.value.toMutableList()
        val safeIndex = trackIndex.coerceIn(0, current.size)
        current.add(safeIndex, track)
        updateTracksSnapshot(current.toList())
    }

    fun duplicateTrack(trackIndex: Int): TimelineTrack<*>? {
        if (trackIndex !in tracks.value.indices) return null
        val original = tracks.value[trackIndex]
        
        val duplicate = when (original) {
            is AudioTimelineTrack -> {
                original.copyWithEntries(
                    original.entries.mapValues { (_, entry) -> entry.copy() },
                    preserveTrackIdentity = false
                )
            }

            is MidiTimelineTrack -> {
                original.copyWithEntries(
                    original.entries.mapValues { (_, entry) -> entry.copy() },
                    preserveTrackIdentity = false
                )
            }

            else -> return null
        }

        val current = tracks.value.toMutableList()
        current.add(trackIndex + 1, duplicate)
        updateTracksSnapshot(current.toList())
        return duplicate
    }

    fun renameTrack(trackIndex: Int, newName: String) {
        val current = tracks.value.toMutableList()
        val track = current.getOrNull(trackIndex) ?: return

        current[trackIndex] = when (track) {
            is AudioTimelineTrack -> track.copyWithEntries().apply { name = newName }

            is MidiTimelineTrack -> track.copyWithEntries().apply { name = newName }

            else -> return
        }

        updateTracksSnapshot(current.toList())
    }

    fun replaceTrack(trackIndex: Int, track: TimelineTrack<*>) {
        if (trackIndex !in tracks.value.indices) return
        val current = tracks.value.toMutableList()
        current[trackIndex] = preserveRuntimeTrackEntries(
            currentTrack = current[trackIndex],
            replacementTrack = track
        )
        updateTracksSnapshot(current.toList())
    }

    fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        baselinePlayheadMs = _playheadPositionMs.value
        baselineMark = TimeSource.Monotonic.markNow()
        rebuildSortedEntries()
        nextStartIndex = binarySearchFirst(sortedAudioEntries) { it.entry.startTimeUs >= msToUs(baselinePlayheadMs) }
        nextMidiStartIndex = binarySearchFirst(sortedMidiEntries) { it.entry.startTimeMs >= baselinePlayheadMs }
        lastPlayheadMs = baselinePlayheadMs
        val anySoloedTrack = hasSoloedTracks()

        activeEntries.forEach { it.entry.stop() }; activeEntries.clear()
        activeMidiEntries.forEach { it.entry.stop() }; activeMidiEntries.clear()

        val entriesToStart = sortedAudioEntries.filter { it.shouldPlayAt(baselinePlayheadMs, anySoloedTrack) }
        startAudioEntriesBatch(entriesToStart, baselinePlayheadMs)
        activeEntries.addAll(entriesToStart)
        
        sortedMidiEntries.forEach { entry ->
            if (entry.shouldPlayAt(baselinePlayheadMs, anySoloedTrack)) {
                startMidiEntry(entry, baselinePlayheadMs)
                activeMidiEntries.add(entry)
            }
        }
        
        startPlayback()
    }

    fun pause() {
        if (!_isPlaying.value) return
        _isPlaying.value = false
        stopPlayback()
        activeEntries.forEach { it.entry.stop() }
        activeEntries.clear()
        activeMidiEntries.forEach { it.entry.stop() }
        activeMidiEntries.clear()
    }

    fun stop() {
        pause()
        _playheadPositionMs.value = 0L
        lastPlayheadMs = 0L
        nextStartIndex = 0
        nextMidiStartIndex = 0
    }

    fun setPlayheadPosition(positionMs: Long) {
        val coerced = positionMs.coerceAtLeast(0L)
        _playheadPositionMs.value = coerced
        if (_isPlaying.value) {
            baselinePlayheadMs = coerced
            baselineMark = TimeSource.Monotonic.markNow()
            // Beim Seek während Playback: alle aktiven stoppen + neu bestücken
            activeEntries.forEach { it.entry.stop() }; activeEntries.clear()
            activeMidiEntries.forEach { it.entry.stop() }; activeMidiEntries.clear()
            lastPlayheadMs = coerced
            rebuildSortedEntries()
            nextStartIndex = binarySearchFirst(sortedAudioEntries) { it.entry.startTimeUs >= msToUs(coerced) }
            nextMidiStartIndex = binarySearchFirst(sortedMidiEntries) { it.entry.startTimeMs >= coerced }
            val anySoloedTrack = hasSoloedTracks()
            val entriesToStart = sortedAudioEntries.filter { it.shouldPlayAt(coerced, anySoloedTrack) }
            startAudioEntriesBatch(entriesToStart, coerced)
            activeEntries.addAll(entriesToStart)
            sortedMidiEntries.forEach { entry ->
                if (entry.shouldPlayAt(coerced, anySoloedTrack)) {
                    startMidiEntry(entry, coerced)
                    activeMidiEntries.add(entry)
                }
            }
        } else {
            syncEntriesAtPosition(coerced)
        }
    }

    private fun startPlayback() {
        playbackJob = playbackScope.launch {
            while (_isPlaying.value) {
                val mark = baselineMark
                if (mark != null) {
                    val elapsed = mark.elapsedNow().inWholeMilliseconds
                    val newPos = baselinePlayheadMs + elapsed
                    _playheadPositionMs.value = newPos
                    processPlaybackIncremental(newPos)
                    delay(computeTickDelay(newPos))
                } else {
                    delay(8L)
                }
            }
        }
    }

    private fun processPlaybackIncremental(currentMs: Long) {
        val anySoloedTrack = hasSoloedTracks()
        val currentUs = msToUs(currentMs)
        val lastPlayheadUs = msToUs(lastPlayheadMs)
        if (currentMs >= lastPlayheadMs) {
            // Start clips whose startTimeMs has been passed in this tick.
            // No lookahead: playMultiple is synchronous so we fire exactly at the boundary.
            // Any ≤1ms tick-jitter only skips a few inaudible samples via the offset path.
            while (nextStartIndex < sortedAudioEntries.size && sortedAudioEntries[nextStartIndex].entry.startTimeUs <= currentUs) {
                val entry = sortedAudioEntries[nextStartIndex]
                val entryStart = entry.entry.startTimeMs
                if (entry.entry.startTimeUs >= lastPlayheadUs && entry.entry.startTimeUs <= currentUs) {
                    if (!activeEntries.contains(entry) && entry.shouldPlayAt(entryStart, anySoloedTrack)) {
                        startAudioEntry(entry, currentMs)
                        println("TimelineInc: Started ${entry.entry.fileName} at ${currentMs}ms (entry starts ${entryStart}ms lateBy=${currentMs - entryStart}ms)")
                        activeEntries.add(entry)
                    }
                }
                nextStartIndex++
            }
            
            // Start MIDI entries
            while (nextMidiStartIndex < sortedMidiEntries.size && sortedMidiEntries[nextMidiStartIndex].entry.startTimeMs <= currentMs) {
                val entry = sortedMidiEntries[nextMidiStartIndex]
                if (entry.entry.startTimeMs >= lastPlayheadMs && entry.entry.startTimeMs <= currentMs) {
                    if (!activeMidiEntries.contains(entry) && entry.overlapsPlaybackWindow(lastPlayheadMs, currentMs, anySoloedTrack)) {
                        startMidiEntry(
                            entry = entry,
                            startAt = entry.entry.startTimeMs,
                            processTo = currentMs
                        )
                        if (entry.shouldPlayAt(currentMs, anySoloedTrack)) {
                            activeMidiEntries.add(entry)
                        }
                        println("TimelineInc: Started MIDI ${entry.entry.name} at ${currentMs}ms")
                    }
                }
                nextMidiStartIndex++
            }
            
            // Stop entries that have ended or been disabled. Using explicit endTimeMs check
            // (rather than !shouldPlayAt) so pre-started clips whose startTimeMs is still
            // in the future are not evicted from activeEntries on the next tick.
            val iterator = activeEntries.iterator()
            while (iterator.hasNext()) {
                val e = iterator.next()
                if (currentUs >= e.entry.endTimeUs || !isPlaybackEnabled(e.track, anySoloedTrack)) {
                    e.entry.stop(); iterator.remove(); println("TimelineInc: Stopped ${e.entry.fileName} @${currentMs}ms")
                } else {
                    e.entry.updateAutomation(
                        TimelineAutomationEvaluator.evaluate(
                            track = e.track,
                            timeMs = currentMs,
                        )
                    )
                }
            }
            
            // Stop MIDI entries that have ended
            val midiIterator = activeMidiEntries.iterator()
            while (midiIterator.hasNext()) {
                val e = midiIterator.next()
                if (!e.shouldPlayAt(currentMs, anySoloedTrack)) {
                    e.entry.stop(); midiIterator.remove(); println("TimelineInc: Stopped MIDI ${e.entry.name} @${currentMs}ms")
                } else {
                    // Process MIDI notes at current time
                    e.entry.processAtTime(currentMs)
                }
            }
        } else {
            // Rückwärts (Scrub rückwärts): Rebuild aktive Menge
            activeEntries.forEach { it.entry.stop() }; activeEntries.clear()
            activeMidiEntries.forEach { it.entry.stop() }; activeMidiEntries.clear()
            nextStartIndex = binarySearchFirst(sortedAudioEntries) { it.entry.startTimeUs >= msToUs(currentMs) }
            nextMidiStartIndex = binarySearchFirst(sortedMidiEntries) { it.entry.startTimeMs >= currentMs }
            val entriesToStart = sortedAudioEntries.filter { it.shouldPlayAt(currentMs, anySoloedTrack) }
            startAudioEntriesBatch(entriesToStart, currentMs)
            activeEntries.addAll(entriesToStart)
            sortedMidiEntries.forEach { e ->
                if (e.shouldPlayAt(currentMs, anySoloedTrack)) {
                    startMidiEntry(e, currentMs)
                    activeMidiEntries.add(e)
                }
            }
        }
        lastPlayheadMs = currentMs
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        baselineMark = null
    }

    // Returns how many ms to wait before the next tick.
    // Shrinks the delay so the tick fires exactly at the next clip boundary (start or end)
    // rather than up to 8ms late, keeping transitions tight without any lookahead.
    private fun computeTickDelay(currentMs: Long): Long {
        var minDelay = 8L
        val currentUs = msToUs(currentMs)
        if (nextStartIndex < sortedAudioEntries.size) {
            val msToNext = usToRoundedMs(sortedAudioEntries[nextStartIndex].entry.startTimeUs - currentUs)
            if (msToNext in 1L..7L) minDelay = minOf(minDelay, msToNext)
        }
        for (e in activeEntries) {
            val msToEnd = usToRoundedMs(e.entry.endTimeUs - currentUs)
            if (msToEnd in 1L..7L) minDelay = minOf(minDelay, msToEnd)
        }
        return minDelay.coerceIn(1L, 8L)
    }

    private fun updatePlayingEntries() { /* Legacy Vollscan behalten für Fallback oder Debug; jetzt ersetzt durch processPlaybackIncremental */ }

    fun setTrackEntries(trackIndex: Int, audioEntries: List<AudioEntry>) {
        val current = tracks.value.toMutableList()
        val track = current.getOrNull(trackIndex) as? AudioTimelineTrack ?: return
        track.entries.clear()
        audioEntries.sortedBy { it.startTimeMs }.forEach { e -> track.entries[e.startTimeMs] = e }

        val newTrack = track.copyWithEntries()
        current[trackIndex] = newTrack
        updateTracksSnapshot(current.toList())
        nextStartIndex = binarySearchFirst(sortedAudioEntries) { it.entry.startTimeUs >= msToUs(_playheadPositionMs.value) }
    }

    fun setMidiTrackEntries(trackIndex: Int, midiEntries: List<MidiEntry>) {
        val current = tracks.value.toMutableList()
        val track = current.getOrNull(trackIndex)
        if (track !is MidiTimelineTrack) return
        
        track.entries.clear()
        midiEntries.sortedBy { it.startTimeMs }.forEach { e -> track.entries[e.startTimeMs] = e }

        val newTrack = track.copyWithEntries()

        current[trackIndex] = newTrack
        updateTracksSnapshot(current.toList())
        nextMidiStartIndex = binarySearchFirst(sortedMidiEntries) { it.entry.startTimeMs >= _playheadPositionMs.value }
    }
}
