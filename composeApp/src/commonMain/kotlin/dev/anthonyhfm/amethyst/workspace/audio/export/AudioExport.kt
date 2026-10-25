package dev.anthonyhfm.amethyst.workspace.audio.export

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.automation.TimelineAutomationEvaluator
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.deepCopy
import dev.anthonyhfm.amethyst.timeline.data.endTimeUs
import dev.anthonyhfm.amethyst.timeline.data.msToUs
import dev.anthonyhfm.amethyst.timeline.data.samplesToUs
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class AudioExportFormat(val extension: String) {
    WAV(extension = "wav"),
    MP3(extension = "mp3"),
}

internal data class AudioExportOptions(
    val format: AudioExportFormat = AudioExportFormat.WAV,
    val sampleRate: Int = 48_000,
    val bitDepth: Int = 24,
    val bitrate: Int = 320,
)

internal data class AudioExportClip(
    val entry: AudioEntry,
    val source: AudioSource?,
    val track: AudioTimelineTrack?,
    val startUs: Long,
    val endUs: Long,
    val audible: Boolean,
)

internal data class AudioExportSnapshot(
    val clips: List<AudioExportClip>,
    val startUs: Long,
    val endUs: Long,
    val name: String,
    val channels: Int = 2,
    val originalSampleRate: Int? = null,
) {
    val isTimeline: Boolean get() = originalSampleRate == null
    val durationUs: Long get() = endUs - startUs
    val hasAudibleClips: Boolean get() = clips.any { it.audible }
    val hasMissingSources: Boolean get() = clips.any { it.audible && it.source == null }
}

internal class AudioExportRequest(
    val selection: AudioExportSnapshot?,
    val entire: AudioExportSnapshot?,
    val source: AudioExportSnapshot? = null,
)

object AudioExport {
    val isAvailable: Boolean get() = platform is Platform.Desktop
    private val _request = MutableStateFlow<AudioExportRequest?>(value = null)
    internal val request = _request.asStateFlow()
    private var releaseSources: (() -> Unit)? = null

    fun openTimeline() {
        if (!isAvailable || _request.value != null) {
            return
        }
        val tracks = TimelineRepository.tracks.value
        open(
            request = AudioExportRequest(
                selection = captureTimelineExport(tracks = tracks, selections = SelectionManager.selections.value),
                entire = captureTimelineExport(tracks = tracks, selections = null),
            ),
        )
    }

    fun openSource(source: AudioSource) {
        if (!isAvailable || _request.value != null) {
            return
        }
        val entry = AudioEntry(
            startTimeMs = 0L,
            durationMs = source.totalDurationMs,
            fileName = source.fileName,
            sourceId = source.id,
            clipStartSample = 0L,
            clipEndSample = source.totalSamples,
            sampleRate = source.sampleRate,
            channels = source.channels,
            bitDepth = source.bitDepth,
        )
        val endUs = samplesToUs(sampleCount = source.totalSamples, sampleRate = source.sampleRate)
        open(
            request = AudioExportRequest(
                selection = null,
                entire = null,
                source = AudioExportSnapshot(
                    clips = listOf(
                        AudioExportClip(
                            entry = entry,
                            source = source,
                            track = null,
                            startUs = 0L,
                            endUs = endUs,
                            audible = true,
                        ),
                    ),
                    startUs = 0L,
                    endUs = endUs,
                    name = source.fileName.substringBeforeLast(delimiter = '.'),
                    channels = source.channels,
                    originalSampleRate = source.sampleRate,
                ),
            ),
        )
    }

    private fun open(request: AudioExportRequest) {
        val paths = listOfNotNull(request.selection, request.entire, request.source)
            .flatMap { it.clips }
            .mapNotNull { it.source?.pcmBytes?.filePath }
            .toSet()
        releaseSources = ProjectPcmFiles.pin(paths = paths)
        _request.value = request
    }

    internal fun close(expectedRequest: AudioExportRequest? = _request.value) {
        if (_request.value !== expectedRequest) {
            return
        }
        val release = releaseSources
        releaseSources = null
        _request.value = null
        release?.invoke()
    }
}

internal fun captureTimelineExport(
    tracks: List<TimelineTrack<*>>,
    selections: List<Selectable>?,
    sourceProvider: (String) -> AudioSource? = AudioLibraryRepository::get,
): AudioExportSnapshot? {
    val ranges = selections?.filterIsInstance<Selectable.TimelineRange>().orEmpty()
    val selectedClips = selections?.filterIsInstance<Selectable.TimelineEntryItem>().orEmpty()
    val selectedTracks = selections?.filterIsInstance<Selectable.TimelineTrack>().orEmpty().map { it.trackIndex }
    if (selections != null && ranges.isEmpty() && selectedClips.isEmpty() && selectedTracks.isEmpty()) {
        return null
    }
    val anySoloed = tracks.any { it.isSoloed }
    val clips = tracks.flatMapIndexed { index, original ->
        if (original !is AudioTimelineTrack) {
            return@flatMapIndexed emptyList()
        }
        if (selections != null) {
            val selected = when {
                ranges.isNotEmpty() -> ranges.any { it.trackIndex == index }
                selectedClips.isNotEmpty() -> selectedClips.any { it.trackIndex == index }
                else -> index in selectedTracks
            }
            if (!selected) {
                return@flatMapIndexed emptyList()
            }
        }
        val track = original.deepCopy() as AudioTimelineTrack
        val trackRanges = ranges.filter { it.trackIndex == index && it.endMs > it.startMs }
        val entries = track.entries.values.filter { entry ->
            when {
                selections == null -> true
                ranges.isNotEmpty() -> trackRanges.any { range ->
                    entry.startTimeUs < msToUs(timeMs = range.endMs) &&
                        entry.endTimeUs > msToUs(timeMs = range.startMs)
                }
                selectedClips.isNotEmpty() -> selectedClips.any { selection ->
                    selection.trackIndex == index &&
                        selection.entryStartMs == entry.startTimeMs &&
                        selection.clipId == null
                }
                else -> index in selectedTracks
            }
        }
        entries.flatMap { entry ->
            val bounds = if (ranges.isEmpty()) {
                listOf(entry.startTimeUs to entry.endTimeUs)
            } else {
                trackRanges.map { range ->
                    maxOf(entry.startTimeUs, msToUs(timeMs = range.startMs)) to
                        minOf(entry.endTimeUs, msToUs(timeMs = range.endMs))
                }
            }
            bounds.filter { it.second > it.first }.map { (start, end) ->
                AudioExportClip(
                    entry = entry,
                    source = sourceProvider(entry.sourceId),
                    track = track,
                    startUs = start,
                    endUs = end,
                    audible = !entry.isMuted && TimelineAutomationEvaluator.isPlaybackEnabled(
                        track = track,
                        anySoloedTrack = anySoloed,
                    ),
                )
            }
        }
    }
    if (clips.isEmpty()) {
        return null
    }
    return AudioExportSnapshot(
        clips = clips,
        startUs = when {
            selections == null -> 0L
            ranges.isNotEmpty() -> ranges.minOf { msToUs(timeMs = it.startMs) }.coerceAtLeast(minimumValue = 0L)
            else -> clips.minOf { it.startUs }
        },
        endUs = if (ranges.isNotEmpty()) {
            ranges.maxOf { msToUs(timeMs = it.endMs) }
        } else {
            clips.maxOf { it.endUs }
        },
        name = if (selections == null) "Audio" else "Audio Selection",
    )
}
