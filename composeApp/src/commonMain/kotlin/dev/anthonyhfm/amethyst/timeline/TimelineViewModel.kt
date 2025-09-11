package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.ScrollState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimelineViewModel : ViewModel() {
    private val _tracks = MutableStateFlow<List<TimelineTrack<*>>>(emptyList())
    val tracks: StateFlow<List<TimelineTrack<*>>> = _tracks.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _scrollState = MutableStateFlow<ScrollState?>(null)
    val scrollState: StateFlow<ScrollState?> = _scrollState.asStateFlow()

    val playheadPositionMs = TimelineRepository.playheadPositionMs
    val isPlaying = TimelineRepository.isPlaying

    init {
        initializeDemoData()
    }

    private fun initializeDemoData() {
        val track1 = AudioTimelineTrack().apply {
            entries[0L] = AudioEntry(
                startTimeMs = 0L,
                durationMs = 3000L,
                fileName = "drum_loop.wav",
                rawData = null,
                sampleRate = 44100,
                channels = 2,
                bitDepth = 16
            )
            entries[5000L] = AudioEntry(
                startTimeMs = 5000L,
                durationMs = 2500L,
                fileName = "bass_line.wav",
                rawData = null,
                sampleRate = 44100,
                channels = 2,
                bitDepth = 16
            )
        }

        val track2 = AudioTimelineTrack().apply {
            entries[1500L] = AudioEntry(
                startTimeMs = 1500L,
                durationMs = 4000L,
                fileName = "melody.wav",
                rawData = null,
                sampleRate = 44100,
                channels = 2,
                bitDepth = 16
            )
        }

        val track3 = AudioTimelineTrack()

        _tracks.value = listOf(track1, track2, track3)

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
                _tracks.value = currentTracks
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
