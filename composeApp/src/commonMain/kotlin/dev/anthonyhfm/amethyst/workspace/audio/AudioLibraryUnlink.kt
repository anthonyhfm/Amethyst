package dev.anthonyhfm.amethyst.workspace.audio

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.audio.sample.resolvedRegion
import dev.anthonyhfm.amethyst.devices.devicesDepthFirst
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioRegion
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.deepCopy
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

object AudioLibraryUnlink {
    data class SampleChange(
        val deviceId: String,
        val before: SampleChainDeviceState,
        val after: SampleChainDeviceState,
    )

    data class Change(
        val removal: AudioLibraryRepository.Removal,
        val retainedSources: List<AudioSource>,
        val beforeTracks: Map<Int, AudioTimelineTrack>,
        val afterTracks: Map<Int, AudioTimelineTrack>,
        val samples: List<SampleChange>,
    ) {
        fun undo() {
            AudioLibraryRepository.restore(removal = removal)
            restoreUsages(tracks = beforeTracks, useOriginalSamples = true)
            AudioLibraryRepository.releaseInstances(
                sourceIds = retainedSources.mapTo(mutableSetOf(), AudioSource::id),
            )
        }

        fun redo() {
            AudioLibraryRepository.retainInstances(sources = retainedSources)
            restoreUsages(tracks = afterTracks, useOriginalSamples = false)
            AudioLibraryRepository.remove(removal = removal)
        }

        private fun restoreUsages(
            tracks: Map<Int, AudioTimelineTrack>,
            useOriginalSamples: Boolean,
        ) {
            val devices = WorkspaceRepository.samplingChain.devicesDepthFirst()
                .filterIsInstance<SampleChainDevice>()
                .associateBy(SampleChainDevice::selectionUUID)
            samples.forEach { change ->
                devices[change.deviceId]?.let { device ->
                    device.state.value = if (useOriginalSamples) {
                        change.before
                    } else {
                        change.after
                    }
                    device.onStateRestored()
                    device.parentChain?.onDeviceRuntimeStateChanged()
                }
            }
            if (tracks.isNotEmpty()) {
                TimelineRepository.updateTracksSnapshot(
                    updatedTracks = TimelineRepository.tracks.value.mapIndexed { index, track ->
                        tracks[index]?.deepCopy() ?: track
                    },
                )
            }
        }
    }

    fun remove(sourceId: String): Change? {
        val sourceIds = AudioLibraryRepository.removalSourceIds(sourceId = sourceId)
        if (sourceIds.isEmpty()) {
            return null
        }
        val instances = AudioInstanceUnlink(sources = AudioLibraryRepository.sources.value)
        val beforeTracks = mutableMapOf<Int, AudioTimelineTrack>()
        val afterTracks = mutableMapOf<Int, AudioTimelineTrack>()
        TimelineRepository.tracks.value.forEachIndexed { index, track ->
            if (track is AudioTimelineTrack && track.entries.values.any { it.sourceId in sourceIds }) {
                beforeTracks[index] = track.deepCopy() as AudioTimelineTrack
                afterTracks[index] = track.copyWithEntries(
                    entriesToCopy = track.entries.mapValues { (_, entry) ->
                        if (entry.sourceId in sourceIds) {
                            instances.unlink(entry = entry)
                        } else {
                            entry.copy()
                        }
                    },
                )
            }
        }
        val samples = WorkspaceRepository.samplingChain.devicesDepthFirst()
            .filterIsInstance<SampleChainDevice>()
            .filter { it.state.value.sourceId in sourceIds }
            .map { device ->
                val before = device.state.value
                SampleChange(
                    deviceId = device.selectionUUID,
                    before = before,
                    after = instances.unlink(state = before),
                )
            }
        val removedSources = AudioLibraryRepository.sourceOrder.value.mapIndexedNotNull { index, id ->
            AudioLibraryRepository.get(id = id)?.takeIf { id in sourceIds }?.let { source ->
                AudioLibraryRepository.RemovedSource(source = source, originalIndex = index)
            }
        }
        val change = Change(
            removal = AudioLibraryRepository.Removal(sources = removedSources),
            retainedSources = instances.retainedSources,
            beforeTracks = beforeTracks,
            afterTracks = afterTracks,
            samples = samples,
        )
        change.redo()
        return change
    }
}

internal class AudioInstanceUnlink(
    private val sources: Map<String, AudioSource>,
) {
    private val regions = linkedMapOf<AudioRegion, AudioSource>()
    val retainedSources: List<AudioSource> get() = regions.values.toList()

    fun unlink(entry: AudioEntry): AudioEntry {
        val source = checkNotNull(sources[entry.sourceId])
        val start = entry.clipStartSample.coerceIn(0L, source.totalSamples)
        val end = entry.clipEndSample.coerceIn(start, source.totalSamples)
        val retained = retain(source = source, startFrame = start, endFrame = end)
        return entry.copy(
            sourceId = retained.id,
            clipStartSample = 0L,
            clipEndSample = retained.totalSamples,
            legacyRawData = null,
        )
    }

    fun unlink(state: SampleChainDeviceState): SampleChainDeviceState {
        val source = checkNotNull(sources[state.sourceId])
        val (start, end) = state.resolvedRegion(totalFrames = source.totalSamples)
        val retained = retain(source = source, startFrame = start, endFrame = end)
        fun rebase(position: Float): Float {
            if (end == start) {
                return 0f
            }
            return ((source.totalSamples.toDouble() * position.toDouble() - start) / (end - start))
                .toFloat()
                .coerceIn(0f, 1f)
        }
        return state.copy(
            rawData = null,
            sourceId = retained.id,
            totalDurationMs = retained.totalDurationMs,
            startPosition = 0f,
            endPosition = 1f,
            sourceStartFrame = 0L,
            sourceEndFrameExclusive = retained.totalSamples,
            loopStartPosition = state.loopStartPosition?.let(::rebase),
            loopEndPosition = state.loopEndPosition?.let(::rebase),
        ).apply {
            isMuted = state.isMuted
            isCollapsed = state.isCollapsed
        }
    }

    private fun retain(source: AudioSource, startFrame: Long, endFrame: Long): AudioSource {
        val region = AudioRegion(
            sourceId = source.id,
            startFrame = startFrame,
            endFrameExclusive = endFrame,
        )
        return regions.getOrPut(key = region) {
            source.copy(
                id = UUID.randomUUID(),
                rawData = source.pcmRegionBytes(
                    fromIndex = (startFrame * source.bytesPerSample).toInt(),
                    toIndex = (endFrame * source.bytesPerSample).toInt(),
                ),
                stemMetadata = null,
                isLibraryAsset = false,
            )
        }
    }
}
