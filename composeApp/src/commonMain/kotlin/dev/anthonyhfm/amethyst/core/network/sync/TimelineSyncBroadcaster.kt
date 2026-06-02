package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
class TimelineSyncBroadcaster(
    private val provider: AmethystConnectProvider,
    private val scope: CoroutineScope
) {
    private val jobs = mutableListOf<Job>()

    fun start() {
        if (jobs.isNotEmpty()) return

        jobs += scope.launch {
            var previousTracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value
            TimelineRepository.tracks
                .drop(1)
                .collect { newTracks ->
                    if (TimelineRepository.isApplyingRemoteUpdate) {
                        TimelineRepository.markRemoteUpdateConsumed()
                        previousTracks = newTracks
                        return@collect
                    }
                    broadcastDiff(previousTracks, newTracks)
                    previousTracks = newTracks
                }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private suspend fun broadcastDiff(
        previousTracks: List<TimelineTrack<*>>,
        newTracks: List<TimelineTrack<*>>
    ) {
        val previousIdToIndex = previousTracks.mapIndexed { i, t -> t.trackId to i }.toMap()
        val newIdToIndex = newTracks.mapIndexed { i, t -> t.trackId to i }.toMap()

        val previousIdSet = previousIdToIndex.keys
        val newIdSet = newIdToIndex.keys

        previousTracks
            .mapIndexed { i, t -> t.trackId to i }
            .filter { (id, _) -> id !in newIdSet }
            .sortedByDescending { (_, i) -> i }
            .forEach { (_, index) ->
                provider.send(ConnectEvent.TimelineTrackRemoved(index))
            }

        newTracks
            .mapIndexed { i, t -> t.trackId to i }
            .filter { (id, _) -> id !in previousIdSet }
            .sortedBy { (_, i) -> i }
            .forEach { (_, index) ->
                val track = newTracks[index]
                if (track is MidiTimelineTrack) {
                    val bytes = encodeTrack(track) ?: return@forEach
                    provider.send(ConnectEvent.TimelineTrackAdded(index, bytes))
                }
            }

        // Updated tracks — same trackId but different object reference means content changed
        val previousById = previousTracks.associateBy { it.trackId }
        newTracks.forEachIndexed { index, newTrack ->
            if (newTrack.trackId !in previousIdSet) return@forEachIndexed
            val prevTrack = previousById[newTrack.trackId] ?: return@forEachIndexed
            if (newTrack is MidiTimelineTrack && newTrack !== prevTrack) {
                val bytes = encodeTrack(newTrack) ?: return@forEachIndexed
                provider.send(ConnectEvent.TimelineTrackUpdated(index, bytes))
            }
        }
    }

    private fun encodeTrack(track: MidiTimelineTrack): ByteArray? {
        return try {
            AmethystProtoBuf.encodeToByteArray(MidiTimelineTrack.serializer(), track)
        } catch (e: Exception) {
            null
        }
    }
}
