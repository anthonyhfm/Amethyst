package dev.anthonyhfm.amethyst.workspace.audio

import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.engine.audio.source.PreparedAudioSourceCache
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.StemKind
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Workspace-scoped owner of decoded project audio.
 *
 * Timeline clips and sample devices only retain an asset ID and their own region.
 * Loading a different workspace replaces this repository atomically; closing a
 * workspace clears it and stops any library preview.
 */
object AudioLibraryRepository {
    data class RemovedSource(
        val source: AudioSource,
        val originalIndex: Int,
    )

    data class Removal(
        val sources: List<RemovedSource>,
    ) {
        val sourceIds: Set<String> = sources.mapTo(linkedSetOf()) { it.source.id }
    }

    data class PreviewState(
        val sourceId: String? = null,
        val positionFrame: Long = 0L,
        val isPlaying: Boolean = false,
    ) {
        fun progress(source: AudioSource): Float = if (source.totalSamples <= 0L) {
            0f
        } else {
            positionFrame.toDouble()
                .div(source.totalSamples.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
        }
    }

    private data class SourceFingerprint(
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
        val byteCount: Int,
        val contentHash: Int,
    )

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _sources = MutableStateFlow<Map<String, AudioSource>>(emptyMap())
    val sources: StateFlow<Map<String, AudioSource>> = _sources.asStateFlow()
    private val _sourceOrder = MutableStateFlow<List<String>>(emptyList())
    val sourceOrder: StateFlow<List<String>> = _sourceOrder.asStateFlow()

    private val _previewState = MutableStateFlow(PreviewState())
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    private var previewPlaybackId: String? = null
    private var previewJob: Job? = null

    /** Adds [source], returning an existing byte-identical asset when available. */
    fun add(source: AudioSource): AudioSource {
        val current = _sources.value
        current[source.id]?.let { existing ->
            require(existing.hasSameAudioAs(source)) {
                "Audio source ID '${source.id}' is already used by different audio data"
            }
            return existing
        }

        val fingerprint = source.fingerprint()
        val duplicate = current.values.firstOrNull { candidate ->
            candidate.fingerprint() == fingerprint && candidate.rawData.contentEquals(source.rawData)
        }
        if (duplicate != null) return duplicate

        _sources.update { it + (source.id to source) }
        _sourceOrder.update { order ->
            if (source.id in order) order else order + source.id
        }
        return source
    }

    fun get(id: String): AudioSource? = _sources.value[id]

    fun all(): List<AudioSource> {
        val current = _sources.value
        return _sourceOrder.value.mapNotNull(current::get)
    }

    fun stemsFor(parentSourceId: String): List<AudioSource> = all()
        .filter { it.stemMetadata?.parentSourceId == parentSourceId }
        .sortedBy { STEM_ORDER.indexOf(it.stemMetadata?.kind) }

    fun hasStems(parentSourceId: String): Boolean =
        _sources.value.values.any { it.stemMetadata?.parentSourceId == parentSourceId }

    fun missingStemKinds(parentSourceId: String): Set<StemKind> {
        val existingKinds = stemsFor(parentSourceId).mapNotNullTo(mutableSetOf()) { it.stemMetadata?.kind }
        return STEM_ORDER.filterTo(linkedSetOf()) { it !in existingKinds }
    }

    /** Adds new stems without replacing already extracted kinds. */
    fun addMissingStems(parentSourceId: String, stems: List<AudioSource>) {
        if (stems.isEmpty()) return
        val parent = get(parentSourceId)
        require(parent != null && parent.stemMetadata == null) {
            "Unknown parent audio source '$parentSourceId'"
        }
        val kinds = stems.mapNotNull { it.stemMetadata?.kind }
        require(kinds.size == stems.size && kinds.toSet().size == stems.size)
        require(stems.all { it.stemMetadata?.parentSourceId == parentSourceId })
        require(kinds.all { it in missingStemKinds(parentSourceId) }) {
            "Audio source '$parentSourceId' already contains one of the supplied stems"
        }

        val additions = stems.associateBy(AudioSource::id)
        require(additions.size == stems.size)
        require(additions.keys.none(_sources.value::containsKey))
        _sources.update { it + additions }
        _sourceOrder.update { current ->
            val updatedSources = _sources.value
            val withoutChildren = current.filterNot { id ->
                updatedSources[id]?.stemMetadata?.parentSourceId == parentSourceId
            }.toMutableList()
            val parentIndex = withoutChildren.indexOf(parentSourceId)
            val childIds = STEM_ORDER.mapNotNull { kind ->
                updatedSources.values.firstOrNull {
                    it.stemMetadata?.parentSourceId == parentSourceId && it.stemMetadata.kind == kind
                }?.id
            }
            if (parentIndex == -1) withoutChildren + childIds
            else withoutChildren.apply { addAll(parentIndex + 1, childIds) }
        }
    }

    /** Adds a complete four-stem result in one observable update. */
    fun addStemGroup(parentSourceId: String, stems: List<AudioSource>) {
        require(!hasStems(parentSourceId)) { "Audio source '$parentSourceId' already has stems" }
        require(stems.size == STEM_ORDER.size)
        require(stems.mapNotNull { it.stemMetadata?.kind }.toSet() == STEM_ORDER.toSet())
        addMissingStems(parentSourceId, stems)
    }

    /** IDs removed by a user action: a root includes all of its extracted stems. */
    fun removalSourceIds(sourceId: String): Set<String> {
        val source = get(sourceId) ?: return emptySet()
        return if (source.stemMetadata == null) {
            buildSet {
                add(sourceId)
                stemsFor(sourceId).mapTo(this) { it.id }
            }
        } else {
            setOf(sourceId)
        }
    }

    fun remove(sourceId: String): Removal? = removeIds(removalSourceIds(sourceId))

    fun remove(removal: Removal): Removal? = removeIds(removal.sourceIds)

    fun restore(removal: Removal) {
        if (removal.sources.isEmpty()) return
        val additions = removal.sources.associate { it.source.id to it.source }
        require(additions.keys.none(_sources.value::containsKey))
        _sources.update { it + additions }
        _sourceOrder.update { current ->
            current.toMutableList().apply {
                removal.sources.sortedBy(RemovedSource::originalIndex).forEach { removed ->
                    add(removed.originalIndex.coerceIn(0, size), removed.source.id)
                }
            }
        }
    }

    private fun removeIds(sourceIds: Set<String>): Removal? {
        if (sourceIds.isEmpty()) return null
        val currentSources = _sources.value
        val currentOrder = _sourceOrder.value
        val removed = currentOrder.mapIndexedNotNull { index, id ->
            currentSources[id]?.takeIf { id in sourceIds }?.let { RemovedSource(it, index) }
        }
        if (removed.isEmpty()) return null
        if (_previewState.value.sourceId in sourceIds) stopPreview(resetPosition = true)
        PreparedAudioSourceCache.clear()
        _sources.value = currentSources - removed.map { it.source.id }.toSet()
        _sourceOrder.value = currentOrder.filterNot(sourceIds::contains)
        return Removal(removed)
    }

    /** Moves a source inside the user-defined library order. */
    fun move(sourceId: String, toIndex: Int) {
        _sourceOrder.update { current ->
            val sourceMap = _sources.value
            val rootIds = current.filter { sourceMap[it]?.stemMetadata == null }.toMutableList()
            val fromIndex = rootIds.indexOf(sourceId)
            if (fromIndex == -1 || rootIds.size < 2) return@update current
            val destination = toIndex.coerceIn(0, rootIds.lastIndex)
            if (fromIndex == destination) return@update current
            rootIds.add(destination, rootIds.removeAt(fromIndex))
            buildList {
                rootIds.forEach { rootId ->
                    add(rootId)
                    addAll(
                        current.filter { id -> sourceMap[id]?.stemMetadata?.parentSourceId == rootId }
                    )
                }
                addAll(current.filter { id -> id !in this })
            }
        }
    }

    /** Replaces the complete workspace library and stops previews from the old workspace. */
    fun load(sources: List<AudioSource>) {
        stopPreview(resetPosition = true)
        PreparedAudioSourceCache.clear()
        val canonical = LinkedHashMap<String, AudioSource>()
        sources.forEach { source ->
            canonical[source.id]?.let { existing ->
                require(existing.hasSameAudioAs(source)) {
                    "Audio source ID '${source.id}' occurs more than once with different audio data"
                }
                return@forEach
            }
            // Persisted IDs are part of the project graph. Even byte-identical sources
            // must retain their IDs here, otherwise existing clip/device references can
            // become dangling. Runtime imports still deduplicate through add().
            canonical[source.id] = source
        }
        _sources.value = canonical
        _sourceOrder.value = canonical.keys.toList()
    }

    suspend fun importFile(file: PlatformFile): AudioSource? {
        val signal = Echo.decodeAudioData(
            audioData = file.readBytes(),
            fileName = file.name,
        ) ?: return null
        val rawData = signal.rawData ?: return null
        if (rawData.isEmpty()) return null

        return add(
            AudioSource(
                id = UUID.randomUUID(),
                fileName = file.name,
                rawData = rawData,
                sampleRate = signal.sampleRate,
                channels = signal.channels,
                bitDepth = signal.bitDepth,
            )
        )
    }

    fun togglePreview(sourceId: String) {
        val source = get(sourceId) ?: return
        val current = _previewState.value
        when {
            current.sourceId == sourceId && current.isPlaying -> pausePreview()
            else -> startPreview(
                source = source,
                startFrame = current.positionFrame
                    .takeIf { current.sourceId == sourceId }
                    ?.coerceIn(0L, source.totalSamples)
                    ?: 0L,
            )
        }
    }

    fun seekPreview(sourceId: String, progress: Float) {
        val source = get(sourceId) ?: return
        val targetFrame = (source.totalSamples * progress.coerceIn(0f, 1f))
            .toLong()
            .coerceIn(0L, source.totalSamples)
        val wasPlaying = _previewState.value.sourceId == sourceId && _previewState.value.isPlaying
        stopPreviewPlayback()
        _previewState.value = PreviewState(sourceId = sourceId, positionFrame = targetFrame)
        if (wasPlaying) startPreview(source, targetFrame)
    }

    fun stopPreview(resetPosition: Boolean = true) {
        val current = _previewState.value
        stopPreviewPlayback()
        _previewState.value = if (resetPosition) {
            PreviewState()
        } else {
            current.copy(isPlaying = false)
        }
    }

    fun clear() {
        stopPreview(resetPosition = true)
        PreparedAudioSourceCache.clear()
        _sources.value = emptyMap()
        _sourceOrder.value = emptyList()
    }

    private fun startPreview(source: AudioSource, startFrame: Long) {
        stopPreviewPlayback()
        val resolvedStart = startFrame
            .takeIf { it < source.totalSamples }
            ?.coerceAtLeast(0L)
            ?: 0L
        val playbackId = Echo.playSource(
            sourceId = source.id,
            startFrame = resolvedStart,
            endFrameExclusive = source.totalSamples,
            origin = PREVIEW_ORIGIN,
        ) ?: run {
            _previewState.value = PreviewState(sourceId = source.id, positionFrame = resolvedStart)
            return
        }

        previewPlaybackId = playbackId
        _previewState.value = PreviewState(
            sourceId = source.id,
            positionFrame = resolvedStart,
            isPlaying = true,
        )
        val startedAt = TimeSource.Monotonic.markNow()
        previewJob = repositoryScope.launch {
            while (isActive) {
                val elapsedFrames = (
                    startedAt.elapsedNow().inWholeNanoseconds.toDouble() * source.sampleRate.toDouble() /
                        NANOS_PER_SECOND.toDouble()
                    ).toLong()
                val position = (resolvedStart + elapsedFrames).coerceAtMost(source.totalSamples)
                _previewState.value = PreviewState(
                    sourceId = source.id,
                    positionFrame = position,
                    isPlaying = position < source.totalSamples,
                )
                if (position >= source.totalSamples) {
                    previewPlaybackId = null
                    break
                }
                delay(PREVIEW_REFRESH_MILLIS)
            }
        }
    }

    private fun pausePreview() {
        val current = _previewState.value
        stopPreviewPlayback()
        _previewState.value = current.copy(isPlaying = false)
    }

    private fun stopPreviewPlayback() {
        previewJob?.cancel()
        previewJob = null
        previewPlaybackId?.let(Echo::stop)
        previewPlaybackId = null
    }

    private fun AudioSource.fingerprint() = SourceFingerprint(
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth,
        byteCount = rawData.size,
        contentHash = rawData.contentHashCode(),
    )

    private fun AudioSource.hasSameAudioAs(other: AudioSource): Boolean =
        fingerprint() == other.fingerprint() && rawData.contentEquals(other.rawData)

    private const val PREVIEW_REFRESH_MILLIS = 16L
    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val PREVIEW_ORIGIN = "AudioLibraryPreview"
    private val STEM_ORDER = listOf(StemKind.VOCALS, StemKind.DRUMS, StemKind.BASS, StemKind.OTHER)
}
