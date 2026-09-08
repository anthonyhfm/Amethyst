package dev.anthonyhfm.amethyst.workspace.audio

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.StemKind
import dev.anthonyhfm.amethyst.timeline.data.StemMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val CUDA_RUNTIME_DOWNLOAD_BYTES = 2_700_000_000L

enum class StemSeparationModel(
    val standardModelId: String,
    val standardDownloadBytes: Long,
    val highQualityModelId: String? = null,
    val highQualityDownloadBytes: Long? = null,
) {
    HYBRID_TRANSFORMER(
        standardModelId = "htdemucs",
        standardDownloadBytes = 84_141_911L,
        highQualityModelId = "htdemucs_ft",
        highQualityDownloadBytes = 336_565_084L,
    ),
    HYBRID_DEMUCS(
        standardModelId = "hdemucs_mmi",
        standardDownloadBytes = 167_407_275L,
    ),
    MDX(
        standardModelId = "mdx",
        standardDownloadBytes = 691_349_536L,
        highQualityModelId = "mdx_extra",
        highQualityDownloadBytes = 669_581_740L,
    ),
    MDX_QUANTIZED(
        standardModelId = "mdx_q",
        standardDownloadBytes = 209_090_706L,
        highQualityModelId = "mdx_extra_q",
        highQualityDownloadBytes = 167_578_560L,
    ),
    BS_ROFORMER(
        standardModelId = "bs_roformer_4stem",
        standardDownloadBytes = 527_385_512L,
    ),
}

data class StemSeparationConfig(
    val model: StemSeparationModel = StemSeparationModel.HYBRID_TRANSFORMER,
    val highQuality: Boolean = false,
) {
    val supportsHighQuality: Boolean
        get() = model.highQualityModelId != null

    val resolvedModelId: String
        get() = if (highQuality) model.highQualityModelId ?: model.standardModelId else model.standardModelId

    val downloadBytes: Long
        get() = if (highQuality) {
            model.highQualityDownloadBytes ?: model.standardDownloadBytes
        } else {
            model.standardDownloadBytes
        }
}

enum class StemExtractionStage {
    QUEUED,
    INSTALLING_ACCELERATION,
    DOWNLOADING_MODEL,
    PREPARING_AUDIO,
    SEPARATING,
    IMPORTING_STEMS,
    COMPLETE,
    FAILED,
    CANCELLED,
}

data class StemExtractionJob(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val stage: StemExtractionStage = StemExtractionStage.QUEUED,
    val progress: Float = 0f,
    val backend: String? = null,
    val error: String? = null,
    val forceCpu: Boolean = false,
    val config: StemSeparationConfig = StemSeparationConfig(),
) {
    val isTerminal: Boolean
        get() = stage == StemExtractionStage.COMPLETE ||
            stage == StemExtractionStage.FAILED ||
            stage == StemExtractionStage.CANCELLED
}

data class StemExtractionProgress(
    val stage: StemExtractionStage,
    val progress: Float,
    val backend: String? = null,
)

data class ExtractedStem(
    val kind: StemKind,
    val rawData: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
)

interface StemExtractionPlatformBackend {
    val isAvailable: Boolean
    val canInstallCuda: Boolean
    val isCudaReady: Boolean

    fun isModelReady(modelId: String): Boolean

    suspend fun extract(
        source: AudioSource,
        modelId: String,
        forceCpu: Boolean,
        installCuda: Boolean,
        onProgress: (StemExtractionProgress) -> Unit,
    ): List<ExtractedStem>
}

expect fun createStemExtractionPlatformBackend(): StemExtractionPlatformBackend

/** Serial workspace-scoped queue for expensive local source-separation jobs. */
object StemExtractionRepository {
    private data class Request(
        val jobId: String,
        val source: AudioSource,
        val config: StemSeparationConfig,
        val forceCpu: Boolean,
        val installCuda: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val backend by lazy(::createStemExtractionPlatformBackend)
    private val waiting = ArrayDeque<Request>()
    private var activeRequest: Request? = null
    private var activeJob: Job? = null

    private val _jobs = MutableStateFlow<List<StemExtractionJob>>(emptyList())
    val jobs: StateFlow<List<StemExtractionJob>> = _jobs.asStateFlow()
    val isAvailable: Boolean get() = backend.isAvailable
    fun requiresModelConsent(config: StemSeparationConfig): Boolean =
        !backend.isModelReady(config.resolvedModelId) && config.resolvedModelId !in _consentedModelIds.value
    val shouldOfferCuda: Boolean
        get() = backend.canInstallCuda && !backend.isCudaReady && !_cudaDeclinedForSession.value

    private val _consentedModelIds = MutableStateFlow<Set<String>>(emptySet())
    private val _cudaDeclinedForSession = MutableStateFlow(false)
    val cudaDeclinedForSession: StateFlow<Boolean> = _cudaDeclinedForSession.asStateFlow()

    fun grantModelConsent(config: StemSeparationConfig) {
        _consentedModelIds.update { it + config.resolvedModelId }
    }

    fun declineCudaForSession() {
        _cudaDeclinedForSession.value = true
    }

    fun enqueue(
        sourceId: String,
        config: StemSeparationConfig = StemSeparationConfig(),
        forceCpu: Boolean = false,
        installCuda: Boolean = false,
    ): String? {
        if (!isAvailable || requiresModelConsent(config)) return null
        val source = AudioLibraryRepository.get(sourceId) ?: return null
        if (source.stemMetadata != null || AudioLibraryRepository.missingStemKinds(sourceId).isEmpty()) return null
        if (_jobs.value.any { it.sourceId == sourceId && !it.isTerminal }) return null

        val id = UUID.randomUUID()
        waiting.addLast(Request(id, source, config, forceCpu, installCuda))
        _jobs.update { jobs ->
            jobs.filterNot { it.sourceId == sourceId && it.stage == StemExtractionStage.COMPLETE } +
                StemExtractionJob(id, source.id, source.fileName, forceCpu = forceCpu, config = config)
        }
        pump()
        return id
    }

    fun cancelForSource(sourceId: String) {
        _jobs.value
            .filter { it.sourceId == sourceId && !it.isTerminal }
            .forEach { cancel(it.id) }
    }

    fun cancel(jobId: String) {
        val active = activeRequest
        if (active?.jobId == jobId) {
            activeJob?.cancel()
            return
        }
        val retained = waiting.filterNot { it.jobId == jobId }
        if (retained.size != waiting.size) {
            waiting.clear()
            waiting.addAll(retained)
            updateJob(jobId) { it.copy(stage = StemExtractionStage.CANCELLED, progress = 0f) }
        }
    }

    fun retryOnCpu(jobId: String): String? {
        val failed = _jobs.value.firstOrNull { it.id == jobId && it.stage == StemExtractionStage.FAILED }
            ?: return null
        _jobs.update { jobs -> jobs.filterNot { it.id == jobId } }
        return enqueue(failed.sourceId, config = failed.config, forceCpu = true)
    }

    fun dismiss(jobId: String) {
        _jobs.update { jobs -> jobs.filterNot { it.id == jobId && it.isTerminal } }
    }

    fun reset() {
        activeJob?.cancel()
        waiting.clear()
        activeRequest = null
        activeJob = null
        _jobs.value = emptyList()
    }

    private fun pump() {
        if (activeJob != null) return
        val request = waiting.removeFirstOrNull() ?: return
        activeRequest = request
        val launchedJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val results = backend.extract(
                    request.source,
                    request.config.resolvedModelId,
                    request.forceCpu,
                    request.installCuda,
                ) { progress ->
                    updateJob(request.jobId) {
                        it.copy(
                            stage = progress.stage,
                            progress = progress.progress.coerceIn(0f, 1f),
                            backend = progress.backend ?: it.backend,
                            error = null,
                        )
                    }
                }
                updateJob(request.jobId) { it.copy(stage = StemExtractionStage.IMPORTING_STEMS, progress = 0.96f) }
                val missingKinds = AudioLibraryRepository.missingStemKinds(request.source.id)
                require(results.mapTo(mutableSetOf()) { it.kind }.containsAll(missingKinds)) {
                    "Stem extraction did not return every missing stem"
                }
                val sources = results.filter { it.kind in missingKinds }.map { stem ->
                    AudioSource(
                        id = UUID.randomUUID(),
                        fileName = "${request.source.fileName.substringBeforeLast('.', request.source.fileName)} [${stem.kind.displayName()}].wav",
                        rawData = stem.rawData,
                        sampleRate = stem.sampleRate,
                        channels = stem.channels,
                        bitDepth = stem.bitDepth,
                        stemMetadata = StemMetadata(request.source.id, stem.kind, request.config.resolvedModelId),
                    )
                }
                AudioLibraryRepository.addMissingStems(request.source.id, sources)
                updateJob(request.jobId) { it.copy(stage = StemExtractionStage.COMPLETE, progress = 1f) }
            } catch (_: CancellationException) {
                updateJob(request.jobId) { it.copy(stage = StemExtractionStage.CANCELLED, progress = 0f) }
            } catch (error: Throwable) {
                updateJob(request.jobId) {
                    it.copy(
                        stage = StemExtractionStage.FAILED,
                        error = error.message ?: "Stem extraction failed",
                    )
                }
            } finally {
                if (activeRequest?.jobId == request.jobId) {
                    activeRequest = null
                    activeJob = null
                    pump()
                }
            }
        }
        activeJob = launchedJob
        launchedJob.start()
    }

    private fun updateJob(jobId: String, transform: (StemExtractionJob) -> StemExtractionJob) {
        _jobs.update { jobs -> jobs.map { if (it.id == jobId) transform(it) else it } }
    }
}

fun StemKind.displayName(): String = when (this) {
    StemKind.VOCALS -> "Vocals"
    StemKind.DRUMS -> "Drums"
    StemKind.BASS -> "Bass"
    StemKind.OTHER -> "Other"
}
