@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.copyWithPreciseTiming
import dev.anthonyhfm.amethyst.timeline.data.deepCopy
import dev.anthonyhfm.amethyst.timeline.data.endTimeUs
import dev.anthonyhfm.amethyst.timeline.data.msToUs
import dev.anthonyhfm.amethyst.timeline.data.usToSamples
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TimelineAudioConsolidation {
    private val scope = CoroutineScope(context = SupervisorJob() + Dispatchers.Main)
    private val _progress = MutableStateFlow<Float?>(value = null)
    val progress = _progress.asStateFlow()
    private val _error = MutableStateFlow<String?>(value = null)
    val error = _error.asStateFlow()
    private var activeJob: Job? = null
    private var generation = 0L

    fun start(selections: List<Selectable>): Boolean {
        if (_progress.value != null || resolveAudioConsolidation(selections = selections).isEmpty()) {
            return false
        }
        _progress.value = 0f
        _error.value = null
        val requestGeneration = generation
        activeJob = scope.launch {
            try {
                consolidate(selections = selections) { value ->
                    if (generation == requestGeneration) {
                        _progress.value = value
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == requestGeneration) {
                    _error.value = failure.message ?: "Audio consolidation failed"
                }
            } finally {
                if (generation == requestGeneration) {
                    _progress.value = null
                    activeJob = null
                }
            }
        }
        return true
    }

    fun dismissError() {
        _error.value = null
    }

    fun reset() {
        generation++
        activeJob?.cancel()
        activeJob = null
        _progress.value = null
        _error.value = null
    }

    internal suspend fun consolidate(
        selections: List<Selectable>,
        onProgress: (Float) -> Unit = {},
    ): Boolean {
        val tracks = TimelineRepository.tracks.value
        val targets = resolveAudioConsolidation(selections = selections, tracks = tracks)
        if (targets.isEmpty()) {
            return false
        }
        val requestGeneration = generation
        val before = targets.associate { target ->
            target.trackIndex to (tracks[target.trackIndex].deepCopy() as AudioTimelineTrack)
        }
        val snapshots = before.mapValues { (_, track) -> track.snapshotBytes() }
        val sources = targets.flatMap { target ->
            before.getValue(key = target.trackIndex).entries.values
                .filter { it.intersects(target = target) }
        }.associate { entry ->
            entry.sourceId to requireNotNull(value = AudioLibraryRepository.get(id = entry.sourceId)) {
                "Audio source is missing: ${entry.fileName}"
            }
        }
        val rendered = mutableListOf<ConsolidatedAudio>()
        val retainedPaths = mutableSetOf<String>()
        val addedSourceIds = mutableListOf<String>()
        var committed = false
        try {
            withContext(context = Dispatchers.Default) {
                val context = currentCoroutineContext()
                targets.forEachIndexed { index, target ->
                    context.ensureActive()
                    val result = renderConsolidatedAudio(
                        target = target,
                        entries = before.getValue(key = target.trackIndex).entries.values
                            .filter { it.intersects(target = target) },
                        sources = sources,
                        checkpoint = { value ->
                            context.ensureActive()
                            onProgress((index + value) / targets.size)
                        },
                    )
                    rendered.add(element = result)
                    result.source.pcmContentHash
                }
            }
            currentCoroutineContext().ensureActive()
            check(generation == requestGeneration) { "The workspace has changed" }
            val current = TimelineRepository.tracks.value
            check(before.all { (index, track) ->
                val actual = current.getOrNull(index = index) as? AudioTimelineTrack
                actual != null && actual.trackId == track.trackId &&
                    actual.snapshotBytes().contentEquals(other = snapshots.getValue(key = index))
            } && sources.all { (id, source) -> AudioLibraryRepository.get(id = id) === source }) {
                "The selected audio changed while consolidating. Please try again."
            }

            val updated = current.toMutableList()
            targets.forEachIndexed { index, target ->
                val original = before.getValue(key = target.trackIndex)
                val entries = original.entries.values.flatMap { entry ->
                    if (!entry.intersects(target = target)) {
                        listOf(entry)
                    } else {
                        listOfNotNull(
                            entry.segment(startUs = entry.startTimeUs, endUs = minOf(entry.endTimeUs, target.startUs)),
                            entry.segment(startUs = maxOf(entry.startTimeUs, target.endUs), endUs = entry.endTimeUs),
                        )
                    }
                } + rendered[index].entry
                check(entries.map { it.startTimeMs }.distinct().size == entries.size) {
                    "The selected boundaries are too close to an existing clip edge"
                }
                updated[target.trackIndex] = original.copyWithEntries(entriesToCopy = entries.associateBy { it.startTimeMs })
            }
            val registered = rendered.map { result ->
                AudioLibraryRepository.add(source = result.source).also { source ->
                    if (source === result.source) {
                        addedSourceIds.add(element = source.id)
                    }
                    source.pcmBytes.filePath?.let { retainedPaths.add(element = it) }
                }
            }
            targets.forEachIndexed { index, target ->
                val track = updated[target.trackIndex] as AudioTimelineTrack
                val entry = rendered[index].entry
                track.entries[entry.startTimeMs] = entry.copy(sourceId = registered[index].id, fileName = registered[index].fileName)
            }
            val affected = targets.mapTo(destination = mutableSetOf()) { it.trackIndex }
            val afterSelections = selections.filterNot { selection ->
                when (selection) {
                    is Selectable.TimelineEntryItem -> selection.trackIndex in affected
                    is Selectable.TimelineRange -> selection.trackIndex in affected
                    else -> false
                }
            } + targets.mapIndexed { index, target ->
                Selectable.TimelineEntryItem(trackIndex = target.trackIndex, entryStartMs = rendered[index].entry.startTimeMs)
            }
            TimelineRepository.updateTracksSnapshot(updatedTracks = updated)
            SelectionManager.replaceSelections(updatedSelections = afterSelections)
            UndoManager.addAction(
                action = UndoableAction.TimelineMultiTrackChange(
                    beforeTracks = before,
                    afterTracks = affected.associateWith { updated[it].deepCopy() },
                    beforeSelections = selections,
                    afterSelections = afterSelections,
                ),
            )
            committed = true
            return true
        } finally {
            if (!committed) {
                addedSourceIds.forEach { id -> AudioLibraryRepository.remove(sourceId = id) }
                retainedPaths.clear()
            }
            rendered.forEach { result ->
                if (result.source.pcmBytes.filePath !in retainedPaths) {
                    discardConsolidationStorage(storage = result.source.pcmBytes)
                }
            }
        }
    }
}

internal fun resolveAudioConsolidation(
    selections: List<Selectable>,
    tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value,
): List<AudioConsolidationTarget> {
    if (selections.any { it is Selectable.TimelineAutomationLane || it is Selectable.TimelineAutomationPoint }) {
        return emptyList()
    }
    val ranges = selections.filterIsInstance<Selectable.TimelineRange>()
    val targets = if (ranges.isNotEmpty()) {
        ranges.groupBy { it.trackIndex }.map { (index, rangesForTrack) ->
            AudioConsolidationTarget(
                trackIndex = index,
                startUs = msToUs(timeMs = rangesForTrack.minOf { minOf(it.startMs, it.endMs) }.coerceAtLeast(minimumValue = 0L)),
                endUs = msToUs(timeMs = rangesForTrack.maxOf { maxOf(it.startMs, it.endMs) }.coerceAtLeast(minimumValue = 0L)),
            )
        }
    } else {
        val clips = selections.filterIsInstance<Selectable.TimelineEntryItem>().mapNotNull { selected ->
            val track = tracks.getOrNull(index = selected.trackIndex) as? AudioTimelineTrack
            track?.entries?.get(key = selected.entryStartMs)?.takeIf { selected.clipId == null }
                ?.let { selected.trackIndex to it }
        }
        if (clips.isEmpty()) {
            return emptyList()
        }
        val start = clips.minOf { it.second.startTimeUs }
        val end = clips.maxOf { it.second.endTimeUs }
        clips.map { it.first }.distinct().map { index ->
            AudioConsolidationTarget(trackIndex = index, startUs = start, endUs = end)
        }
    }
    return targets.filter { target ->
        val track = tracks.getOrNull(index = target.trackIndex) as? AudioTimelineTrack
        target.endUs > target.startUs && track?.entries?.values?.any { it.intersects(target = target) } == true
    }.sortedBy { it.trackIndex }
}

private fun AudioEntry.intersects(target: AudioConsolidationTarget): Boolean {
    return startTimeUs < target.endUs && endTimeUs > target.startUs
}

private fun AudioEntry.segment(startUs: Long, endUs: Long): AudioEntry? {
    if (endUs <= startUs) {
        return null
    }
    val startSample = (clipStartSample + usToSamples(timeUs = startUs - startTimeUs, sampleRate = sampleRate))
        .coerceIn(minimumValue = clipStartSample, maximumValue = clipEndSample)
    val endSample = (clipStartSample + usToSamples(timeUs = endUs - startTimeUs, sampleRate = sampleRate))
        .coerceIn(minimumValue = startSample, maximumValue = clipEndSample)
    if (endSample <= startSample) {
        return null
    }
    return copyWithPreciseTiming(
        startTimeUs = startUs,
        durationUs = endUs - startUs,
        clipStartSample = startSample,
        clipEndSample = endSample,
    )
}

private fun AudioTimelineTrack.snapshotBytes(): ByteArray {
    return AmethystProtoBuf.encodeToByteArray(serializer = AudioTimelineTrack.serializer(), value = this)
}
