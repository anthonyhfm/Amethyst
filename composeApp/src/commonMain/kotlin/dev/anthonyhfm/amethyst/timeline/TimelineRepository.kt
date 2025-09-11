package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object TimelineRepository {
    val tracks: MutableStateFlow<List<TimelineTrack<*>>> = MutableStateFlow(emptyList())

    fun addTrack(track: TimelineTrack<*>) {
        tracks.update {
            tracks.value + track
        }
    }
}