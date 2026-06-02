package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
class TimelineSyncReceiver(
    private val provider: AmethystConnectProvider,
    private val scope: CoroutineScope
) {
    private val jobs = mutableListOf<Job>()

    fun start() {
        if (jobs.isNotEmpty()) return

        jobs += scope.launch {
            provider.events.collect { event ->
                when (event) {
                    is ConnectEvent.TimelineTrackUpdated -> handleTrackUpdated(event)
                    is ConnectEvent.TimelineTrackAdded -> handleTrackAdded(event)
                    is ConnectEvent.TimelineTrackRemoved -> handleTrackRemoved(event)

                    else -> Unit
                }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private fun handleTrackUpdated(event: ConnectEvent.TimelineTrackUpdated) {
        val track = decodeTrack(event.serializedTrack) ?: return

        TimelineRepository.isApplyingRemoteUpdate = true
        TimelineRepository.replaceTrack(event.trackIndex, track)
    }

    private fun handleTrackAdded(event: ConnectEvent.TimelineTrackAdded) {
        val track = decodeTrack(event.serializedTrack) ?: return

        TimelineRepository.isApplyingRemoteUpdate = true
        TimelineRepository.insertTrack(event.trackIndex, track)
    }

    private fun handleTrackRemoved(event: ConnectEvent.TimelineTrackRemoved) {
        TimelineRepository.isApplyingRemoteUpdate = true
        TimelineRepository.removeTrack(event.trackIndex)
    }

    private fun decodeTrack(bytes: ByteArray): MidiTimelineTrack? {
        return try {
            AmethystProtoBuf.decodeFromByteArray(MidiTimelineTrack.serializer(), bytes)
        } catch (e: Exception) {
            null
        }
    }
}
