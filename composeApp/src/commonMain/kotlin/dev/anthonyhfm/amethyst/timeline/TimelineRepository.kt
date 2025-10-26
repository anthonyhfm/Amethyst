package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
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

    // Basis für Zeitberechnung (wird nur bei Play & Seek aktualisiert)
    private var baselineMark: TimeMark? = null
    private var baselinePlayheadMs: Long = 0L

    fun addTrack(track: TimelineTrack<*>) {
        tracks.update { tracks.value + track }
    }

    fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        baselinePlayheadMs = _playheadPositionMs.value
        baselineMark = TimeSource.Monotonic.markNow()
        updatePlayingEntries()
        startPlayback()
    }

    fun pause() {
        if (!_isPlaying.value) return
        _isPlaying.value = false
        stopPlayback()
        activeEntries.forEach { it.stop() }
        activeEntries.clear()
    }

    fun stop() {
        pause()
        _playheadPositionMs.value = 0L
    }

    fun setPlayheadPosition(positionMs: Long) {
        val coerced = positionMs.coerceAtLeast(0L)
        _playheadPositionMs.value = coerced
        if (_isPlaying.value) {
            // Seek während Playback: Baseline neu setzen, damit Playhead nicht springt oder driftet
            baselinePlayheadMs = coerced
            baselineMark = TimeSource.Monotonic.markNow()
        }
        updatePlayingEntries()
    }

    private fun startPlayback() {
        playbackJob = playbackScope.launch {
            while (_isPlaying.value) {
                val mark = baselineMark
                if (mark != null) {
                    val elapsed = mark.elapsedNow().inWholeMilliseconds
                    val newPos = baselinePlayheadMs + elapsed
                    _playheadPositionMs.value = newPos
                    updatePlayingEntries()
                }
                // 8ms tick ~125Hz. Für weniger CPU ggf. 10-16ms testen.
                delay(8L)
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        baselineMark = null
    }

    private fun updatePlayingEntries() {
        val currentPosition = _playheadPositionMs.value
        val allEntries = buildList {
            tracks.value.forEach { track ->
                if (track is AudioTimelineTrack) addAll(track.entries.values)
            }
        }

        // Stop entries die nicht mehr laufen sollen
        activeEntries.removeAll { entry ->
            val shouldStop = currentPosition < entry.startTimeMs || currentPosition >= entry.endTimeMs
            if (shouldStop) {
                entry.stop()
                println("Timeline: Stopped ${entry.fileName} at ${currentPosition}ms")
            }
            shouldStop
        }

        allEntries.forEach { entry ->
            val shouldPlay = currentPosition >= entry.startTimeMs &&
                currentPosition < entry.endTimeMs &&
                !activeEntries.contains(entry)
            if (shouldPlay) {
                entry.start(startAt = currentPosition)
                activeEntries.add(entry)
                println("Timeline: Started ${entry.fileName} at ${currentPosition}ms (entry starts at ${entry.startTimeMs}ms)")
            }
        }
    }

    fun setTrackEntries(trackIndex: Int, audioEntries: List<AudioEntry>) {
        val current = tracks.value.toMutableList()
        val track = current.getOrNull(trackIndex) as? AudioTimelineTrack ?: return
        track.entries.clear()
        audioEntries.sortedBy { it.startTimeMs }.forEach { e -> track.entries[e.startTimeMs] = e }

        val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
        current[trackIndex] = newTrack
        tracks.value = current.toList()
    }
}