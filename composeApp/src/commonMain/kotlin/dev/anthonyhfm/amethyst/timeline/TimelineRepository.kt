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

object TimelineRepository {
    val tracks: MutableStateFlow<List<TimelineTrack<*>>> = MutableStateFlow(emptyList())

    private val _playheadPositionMs = MutableStateFlow(0L)
    val playheadPositionMs: StateFlow<Long> = _playheadPositionMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playbackJob: Job? = null
    private val playbackScope = CoroutineScope(Dispatchers.Main)

    private val activeEntries = mutableSetOf<AudioEntry>()

    fun addTrack(track: TimelineTrack<*>) {
        tracks.update {
            tracks.value + track
        }
    }

    fun play() {
        if (_isPlaying.value) return

        _isPlaying.value = true
        updatePlayingEntries()
        startPlayback()
    }

    fun pause() {
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
        _playheadPositionMs.value = positionMs.coerceAtLeast(0L)
        // Always update playing entries, whether playing or not
        updatePlayingEntries()
    }

    private fun startPlayback() {
        playbackJob = playbackScope.launch {
            while (_isPlaying.value) {
                delay(4L)
                _playheadPositionMs.update { it + 4L }
                updatePlayingEntries()
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun updatePlayingEntries() {
        val currentPosition = _playheadPositionMs.value
        val allEntries = mutableListOf<AudioEntry>()

        tracks.value.forEach { track ->
            if (track is AudioTimelineTrack) {
                allEntries.addAll(track.entries.values)
            }
        }

        activeEntries.removeAll { entry ->
            val shouldStop = currentPosition < entry.startTimeMs || currentPosition >= entry.endTimeMs
            if (shouldStop) {
                entry.stop()
            }
            shouldStop
        }

        allEntries.forEach { entry ->
            val shouldPlay = currentPosition >= entry.startTimeMs &&
                           currentPosition < entry.endTimeMs &&
                           !activeEntries.contains(entry)

            if (shouldPlay) {
                val offsetInEntry = currentPosition - entry.startTimeMs

                entry.start(startAt = offsetInEntry)
                activeEntries.add(entry)
            }
        }
    }
}