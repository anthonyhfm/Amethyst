package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.ScrollState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimelineViewModel : ViewModel() {
    private val _tracks = MutableStateFlow<List<TimelineTrack<*>>>(emptyList())
    val tracks: StateFlow<List<TimelineTrack<*>>> = _tracks.asStateFlow()

    private val _zoomLevel = MutableStateFlow(0.01f) // Deutlich kleinerer Standard-Zoom für bessere Sichtbarkeit
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _scrollState = MutableStateFlow<ScrollState?>(null)
    val scrollState: StateFlow<ScrollState?> = _scrollState.asStateFlow()

    val playheadPositionMs = TimelineRepository.playheadPositionMs
    val isPlaying = TimelineRepository.isPlaying

    init {
        initializeDemoData()
    }

    private fun initializeDemoData() {
        _tracks.value = listOf(AudioTimelineTrack())

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

                // Force update the StateFlow to trigger recomposition
                _tracks.value = currentTracks.toList()

                // Also update the repository
                TimelineRepository.tracks.value = currentTracks.toList()
            }
        }
    }

    fun addAudioFileToTrack(trackIndex: Int, file: PlatformFile, at: Long = 0) {
        viewModelScope.launch {
            val currentTracks = _tracks.value.toMutableList()
            if (trackIndex < currentTracks.size && currentTracks[trackIndex] is AudioTimelineTrack) {
                val track = currentTracks[trackIndex] as AudioTimelineTrack
                track.addFromFile(file, at)

                // Add the file to the track

                // Force update the StateFlow to trigger recomposition
                // Create a new list with a new track instance to force StateFlow update
                val newTrack = AudioTimelineTrack().apply {
                    entries.putAll(track.entries)
                }
                currentTracks[trackIndex] = newTrack

                _tracks.value = currentTracks.toList()

                // Also update the repository
                TimelineRepository.tracks.value = currentTracks.toList()

                println("ViewModel: StateFlow updated. Total tracks: ${_tracks.value.size}")
                _tracks.value.forEachIndexed { index, track ->
                    if (track is AudioTimelineTrack) {
                        println("ViewModel: Track $index has ${track.entries.size} entries")
                    }
                    track.entries.values.forEach { entry ->
                        println("ViewModel: Entry duration: ${entry.durationMs}ms")
                    }
                }
            }
        }
    }

    fun setZoomLevel(zoom: Float) {
        _zoomLevel.value = zoom.coerceIn(0.01f, 10.0f)
    }

    fun zoomBy(factor: Float) {
        val newZoom = _zoomLevel.value * factor
        setZoomLevel(newZoom)
    }

    fun msToPixels(timeMs: Long): Float {
        return timeMs * _zoomLevel.value
    }

    fun pixelsToMs(pixels: Float): Long {
        return (pixels / _zoomLevel.value).toLong()
    }

    fun setScrollState(scrollState: ScrollState) {
        _scrollState.value = scrollState
    }

    fun play() {
        TimelineRepository.play()
    }

    fun pause() {
        TimelineRepository.pause()
    }

    fun stop() {
        TimelineRepository.stop()
    }

    fun setPlayheadPosition(positionMs: Long) {
        TimelineRepository.setPlayheadPosition(positionMs)
    }
}
