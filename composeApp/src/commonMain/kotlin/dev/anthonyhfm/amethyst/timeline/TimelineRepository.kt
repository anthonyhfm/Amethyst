package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

object TimelineRepository {
    val tracks: MutableStateFlow<List<TimelineTrack<*>>> = MutableStateFlow(emptyList())

    private val _playheadPositionMs = MutableStateFlow(0L)
    val playheadPositionMs: StateFlow<Long> = _playheadPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playbackJob: Job? = null
    private val playbackScope = CoroutineScope(Dispatchers.Main)

    private val activeEntries = mutableSetOf<AudioEntry>()
    private val activeMidiEntries = mutableSetOf<MidiEntry>()

    // Basis für Zeitberechnung (wird nur bei Play & Seek aktualisiert)
    private var baselineMark: TimeMark? = null
    private var baselinePlayheadMs: Long = 0L

    private var sortedAudioEntries: List<AudioEntry> = emptyList()
    private var sortedMidiEntries: List<MidiEntry> = emptyList()
    private var nextStartIndex: Int = 0
    private var nextMidiStartIndex: Int = 0
    private var lastPlayheadMs: Long = 0L

    private fun rebuildSortedEntries() {
        sortedAudioEntries = tracks.value
            .filterIsInstance<AudioTimelineTrack>()
            .flatMap { it.entries.values }
            .sortedBy { it.startTimeMs }

        sortedMidiEntries = tracks.value
            .filterIsInstance<MidiTimelineTrack>()
            .flatMap { it.entries.values }
            .sortedBy { it.startTimeMs }

        nextStartIndex = binarySearchFirst(sortedAudioEntries) { it.startTimeMs >= _playheadPositionMs.value }
        nextMidiStartIndex = binarySearchFirst(sortedMidiEntries) { it.startTimeMs >= _playheadPositionMs.value }
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
        tracks.update { tracks.value + track }
        rebuildSortedEntries()
    }

    fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        baselinePlayheadMs = _playheadPositionMs.value
        baselineMark = TimeSource.Monotonic.markNow()
        rebuildSortedEntries()
        nextStartIndex = binarySearchFirst(sortedAudioEntries) { it.startTimeMs >= baselinePlayheadMs }
        nextMidiStartIndex = binarySearchFirst(sortedMidiEntries) { it.startTimeMs >= baselinePlayheadMs }
        lastPlayheadMs = baselinePlayheadMs

        activeEntries.forEach { it.stop() }; activeEntries.clear()
        activeMidiEntries.forEach { it.stop() }; activeMidiEntries.clear()

        sortedAudioEntries.forEach { entry ->
            if (baselinePlayheadMs >= entry.startTimeMs && baselinePlayheadMs < entry.endTimeMs) {
                entry.start(startAt = baselinePlayheadMs)
                activeEntries.add(entry)
            }
        }
        
        sortedMidiEntries.forEach { entry ->
            if (baselinePlayheadMs >= entry.startTimeMs && baselinePlayheadMs < entry.endTimeMs) {
                entry.start(startAt = baselinePlayheadMs)
                activeMidiEntries.add(entry)
            }
        }
        
        startPlayback()
    }

    fun pause() {
        if (!_isPlaying.value) return
        _isPlaying.value = false
        stopPlayback()
        activeEntries.forEach { it.stop() }
        activeEntries.clear()
        activeMidiEntries.forEach { it.stop() }
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
            activeEntries.forEach { it.stop() }; activeEntries.clear()
            activeMidiEntries.forEach { it.stop() }; activeMidiEntries.clear()
            lastPlayheadMs = coerced
            rebuildSortedEntries()
            nextStartIndex = binarySearchFirst(sortedAudioEntries) { it.startTimeMs >= coerced }
            nextMidiStartIndex = binarySearchFirst(sortedMidiEntries) { it.startTimeMs >= coerced }
            sortedAudioEntries.forEach { entry ->
                if (coerced >= entry.startTimeMs && coerced < entry.endTimeMs) {
                    entry.start(startAt = coerced)
                    activeEntries.add(entry)
                }
            }
            sortedMidiEntries.forEach { entry ->
                if (coerced >= entry.startTimeMs && coerced < entry.endTimeMs) {
                    entry.start(startAt = coerced)
                    activeMidiEntries.add(entry)
                }
            }
        } else {
            // Im Pausenmodus nur aktive Menge aktualisieren (keine Wiedergabe, aber Konsistenz)
            activeEntries.clear()
            activeMidiEntries.clear()
            sortedAudioEntries.forEach { entry ->
                if (coerced >= entry.startTimeMs && coerced < entry.endTimeMs) activeEntries.add(entry)
            }
            sortedMidiEntries.forEach { entry ->
                if (coerced >= entry.startTimeMs && coerced < entry.endTimeMs) activeMidiEntries.add(entry)
            }
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
                }
                delay(8L)
            }
        }
    }

    private fun processPlaybackIncremental(currentMs: Long) {
        // Vorwärtsbewegung
        if (currentMs >= lastPlayheadMs) {
            // Starte neue Einträge zwischen lastPlayheadMs und currentMs
            while (nextStartIndex < sortedAudioEntries.size && sortedAudioEntries[nextStartIndex].startTimeMs <= currentMs) {
                val entry = sortedAudioEntries[nextStartIndex]
                if (entry.startTimeMs >= lastPlayheadMs && entry.startTimeMs <= currentMs) {
                    if (!activeEntries.contains(entry) && currentMs < entry.endTimeMs) {
                        entry.start(startAt = currentMs)
                        activeEntries.add(entry)
                        println("TimelineInc: Started ${entry.fileName} at ${currentMs}ms (entry starts ${entry.startTimeMs}ms)")
                    }
                }
                nextStartIndex++
            }
            
            // Start MIDI entries
            while (nextMidiStartIndex < sortedMidiEntries.size && sortedMidiEntries[nextMidiStartIndex].startTimeMs <= currentMs) {
                val entry = sortedMidiEntries[nextMidiStartIndex]
                if (entry.startTimeMs >= lastPlayheadMs && entry.startTimeMs <= currentMs) {
                    if (!activeMidiEntries.contains(entry) && currentMs < entry.endTimeMs) {
                        entry.start(startAt = currentMs)
                        activeMidiEntries.add(entry)
                        println("TimelineInc: Started MIDI ${entry.name} at ${currentMs}ms")
                    }
                }
                nextMidiStartIndex++
            }
            
            // Stoppe Einträge deren Ende überschritten wurde
            val iterator = activeEntries.iterator()
            while (iterator.hasNext()) {
                val e = iterator.next()
                if (currentMs >= e.endTimeMs || currentMs < e.startTimeMs) {
                    e.stop(); iterator.remove(); println("TimelineInc: Stopped ${e.fileName} @${currentMs}ms")
                }
            }
            
            // Stop MIDI entries that have ended
            val midiIterator = activeMidiEntries.iterator()
            while (midiIterator.hasNext()) {
                val e = midiIterator.next()
                if (currentMs >= e.endTimeMs || currentMs < e.startTimeMs) {
                    e.stop(); midiIterator.remove(); println("TimelineInc: Stopped MIDI ${e.name} @${currentMs}ms")
                } else {
                    // Process MIDI notes at current time
                    e.processAtTime(currentMs)
                }
            }
        } else {
            // Rückwärts (Scrub rückwärts): Rebuild aktive Menge
            activeEntries.forEach { it.stop() }; activeEntries.clear()
            activeMidiEntries.forEach { it.stop() }; activeMidiEntries.clear()
            nextStartIndex = binarySearchFirst(sortedAudioEntries) { it.startTimeMs >= currentMs }
            nextMidiStartIndex = binarySearchFirst(sortedMidiEntries) { it.startTimeMs >= currentMs }
            sortedAudioEntries.forEach { e ->
                if (currentMs >= e.startTimeMs && currentMs < e.endTimeMs) {
                    e.start(startAt = currentMs); activeEntries.add(e)
                }
            }
            sortedMidiEntries.forEach { e ->
                if (currentMs >= e.startTimeMs && currentMs < e.endTimeMs) {
                    e.start(startAt = currentMs); activeMidiEntries.add(e)
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

    private fun updatePlayingEntries() { /* Legacy Vollscan behalten für Fallback oder Debug; jetzt ersetzt durch processPlaybackIncremental */ }

    fun setTrackEntries(trackIndex: Int, audioEntries: List<AudioEntry>) {
        val current = tracks.value.toMutableList()
        val track = current.getOrNull(trackIndex) as? AudioTimelineTrack ?: return
        track.entries.clear()
        audioEntries.sortedBy { it.startTimeMs }.forEach { e -> track.entries[e.startTimeMs] = e }

        val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
        current[trackIndex] = newTrack
        tracks.value = current.toList()
        rebuildSortedEntries()
        nextStartIndex = binarySearchFirst(sortedAudioEntries) { it.startTimeMs >= _playheadPositionMs.value }
    }
}