package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.ScrollState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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

                _tracks.value = currentTracks.toList()

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

                val newTrack = AudioTimelineTrack().apply {
                    entries.putAll(track.entries)
                }
                currentTracks[trackIndex] = newTrack

                _tracks.value = currentTracks.toList()

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
        val clamped = zoom.coerceIn(0.01f, 10.0f)
        _zoomLevel.value = clamped
        val updated = SelectionManager.selections.value.map { sel ->
            if (sel is Selectable.TimelineTime) {
                val snapped = GridUtils.snapToGrid(sel.timeMs, clamped)
                if (snapped != sel.timeMs) Selectable.TimelineTime(trackIndex = sel.trackIndex, timeMs = snapped) else sel
            } else sel
        }
        SelectionManager.selections.value = updated
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

    fun getSelectedTimelineTimeMs(): Long? = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineTime>().firstOrNull()?.timeMs
}
